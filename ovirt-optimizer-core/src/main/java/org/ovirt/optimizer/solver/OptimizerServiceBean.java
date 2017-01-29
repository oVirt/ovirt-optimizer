package org.ovirt.optimizer.solver;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.cdi.Autoload;
import org.ovirt.optimizer.config.ConfigProvider;
import org.ovirt.optimizer.ovirt.ClusterDiscovery;
import org.ovirt.optimizer.ovirt.OvirtClient;
import org.ovirt.optimizer.rest.dto.DebugSnapshot;
import org.ovirt.optimizer.rest.dto.Result;
import org.ovirt.optimizer.rest.dto.ScoreResult;
import org.ovirt.optimizer.scheduling.ExecutorServiceProducer;
import org.ovirt.optimizer.solver.facts.Instance;
import org.ovirt.optimizer.solver.facts.RunningVm;
import org.ovirt.optimizer.solver.problemspace.Migration;
import org.ovirt.optimizer.solver.problemspace.OptimalDistributionStepsSolution;
import org.ovirt.optimizer.solver.thread.ClusterInfoUpdater;
import org.ovirt.optimizer.solver.thread.ClusterOptimizer;
import org.ovirt.optimizer.solver.util.SolverUtils;
import org.slf4j.Logger;

import javax.annotation.ManagedBean;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * This is the main service class for computing the optimized
 * Vm to host assignments.
 */
@Autoload
@ApplicationScoped
@ManagedBean
public class OptimizerServiceBean implements OptimizerServiceRemote {
    @Inject
    private Logger log;

    @Inject
    ExecutorServiceProducer executors;

    @Inject
    ClusterDiscovery discovery;

    @Inject
    OvirtClient client;

    @Inject
    ConfigProvider configProvider;

    // This attribute is used by the exported API and has to
    // be used in thread safe way
    final Map<String, ClusterOptimizer> clusterOptimizers = new ConcurrentHashMap<>();

    @PostConstruct
    public void create() {
        log.info("oVirt optimizer service starting");
        int refresh =
                Integer.parseInt(configProvider.load().getConfig().getProperty(ConfigProvider.SOLVER_CLUSTER_REFRESH));
        executors.getScheduler().scheduleWithFixedDelay(() -> executors.getThreadPool().submit(this::refreshClusters),
                0, refresh, TimeUnit.SECONDS);
    }

    // Synchronized should not be needed, but is here as a
    // safeguard for prevention from threading mistakes
    private void refreshClusters() {
        log.debug("Discovering clusters...");
        Set<String> availableClusters = discovery.getClusters();
        if (availableClusters == null) {
            log.error("Cluster discovery failed");
            return;
        }

        /* Compute a set of new clusters */
        availableClusters.removeAll(clusterOptimizers.keySet());

        Properties config = new ConfigProvider().load().getConfig();
        final int maxSteps = Integer.parseInt(config.getProperty(ConfigProvider.SOLVER_STEPS));

        for (String clusterId : availableClusters) {
            log.info(String.format("New cluster %s detected", clusterId));

            long timeout = Integer.parseInt(configProvider.load().getConfig().getProperty(ConfigProvider.SOLVER_TIMEOUT));
            int refresh = Integer.parseInt(configProvider.load().getConfig().getProperty(ConfigProvider.SOLVER_DATA_REFRESH));

            ClusterOptimizer optimizer = new ClusterOptimizer(clusterId, maxSteps, timeout * 1000,
                    configProvider.customRuleFiles());
            ClusterInfoUpdater updater = new ClusterInfoUpdater(client, optimizer);

            clusterOptimizers.put(clusterId, optimizer);
            CompletableFuture.supplyAsync(optimizer, executors.getThreadPool())
                    .thenApply(OptimalDistributionStepsSolution::getClusterId)
                    .thenApply(clusterOptimizers::remove);
            executors.getScheduler().scheduleWithFixedDelay(() -> executors.getThreadPool().submit(updater),
                    0, refresh, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    public void stop() {
        log.info("oVirt service service stopping");
    }

    public Set<String> getAllClusters() {
        return Collections.unmodifiableSet(clusterOptimizers.keySet());
    }

    public Set<String> getActiveClusters() {
        return Collections.unmodifiableSet(clusterOptimizers.keySet());
    }

    public void computeVmStart(String cluster, String uuid) {
        ClusterOptimizer clusterOptimizer;

        clusterOptimizer = clusterOptimizers.get(cluster);
        if (clusterOptimizer == null) {
            log.error(String.format("Cluster %s does not exist", cluster));
            return;
        }

        clusterOptimizer.ensureVmIsRunning(uuid);
    }

    public void cancelVmStart(String cluster, String uuid) {
        ClusterOptimizer clusterOptimizer;

        clusterOptimizer = clusterOptimizers.get(cluster);
        if (clusterOptimizer == null) {
            log.error(String.format("Cluster %s does not exist", cluster));
            return;
        }

        clusterOptimizer.cancelVmIsRunning(uuid);
    }

    @Override
    public ScoreResult recomputeScore(String cluster, Result oldResult) {
        ClusterOptimizer clusterOptimizer;

        clusterOptimizer = clusterOptimizers.get(cluster);
        if (clusterOptimizer == null) {
            log.error(String.format("Cluster %s does not exist", cluster));
            return null;
        }

        ScoreResult scoreResult = new ScoreResult();
        HardSoftScore score = clusterOptimizer.computeScore(oldResult);
        scoreResult.setHardScore(score.getHardScore());
        scoreResult.setSoftScore(score.getSoftScore());
        return scoreResult;
    }

    @Override
    public Result getCurrentResult(String cluster) {
        ClusterOptimizer clusterOptimizer;
        Result r;

        clusterOptimizer = clusterOptimizers.get(cluster);
        if (clusterOptimizer == null) {
            log.error(String.format("Cluster %s does not exist", cluster));
            r = Result.createEmpty(cluster);
        } else {
            r = getCurrentResult(clusterOptimizer);
        }

        return r;
    }

    private Result getCurrentResult(ClusterOptimizer clusterOptimizer) {
        OptimalDistributionStepsSolution best = clusterOptimizer.getBestSolution();
        String clusterId = clusterOptimizer.getClusterId();

        Result r = solutionToResult(clusterId, best);
        return r;
    }

    private Result solutionToResult(String clusterId, OptimalDistributionStepsSolution solution) {
        Result r = new Result(clusterId);
        r.setStatus(Result.ResultStatus.OK);
        r.setHardScore(solution.getScore().getHardScore());
        r.setSoftScore(solution.getScore().getSoftScore());

        r.setHosts(new HashSet<String>());
        for (Host h : solution.getHosts()) {
            r.getHosts().add(h.getId());
        }

        r.setVms(new HashSet<String>());
        for (VM vm : solution.getVms().values()) {
            r.getVms().add(vm.getId());
        }

        r.setRequestedVms(new HashSet<String>());
        for (Object fact : solution.getFixedFacts()) {
            if (fact instanceof RunningVm) {
                r.getRequestedVms().add(((RunningVm) fact).getId());
            }
        }

        Map<Long, Instance> instances = new HashMap<>();
        for (Instance i : solution.getInstances()) {
            instances.put(i.getId(), i);
        }

        // Preprocess the situations and remove all secondary allocations
        r.setHostToVms(hostToInstancesToVms(solution.getFinalSituation().getHostToInstanceAssignments(), instances));
        r.setVmToHost(instanceToHostToVm(solution.getFinalSituation().getInstanceToHostAssignments(), instances));
        r.setCurrentVmToHost(instanceToHostToVm(solution.getInstanceToHostAssignments(), instances));

        r.setBackupReorg(new ArrayList<List<Map<Long, String>>>());
        r.setBackups(new HashMap<Long, String>());

        // The current backup step to accumulated backup migrations
        List<Map<Long, String>> backupStep = new ArrayList<>();

        List<Map<String, String>> migrations = new ArrayList<>();
        for (Migration step : solution.getSteps()) {
            if (!step.isValid()) {
                continue;
            }

            if (step.getInstance().getPrimary()) {
                Map<String, String> migration = new HashMap<>();
                migration.put(step.getInstance().getVmId(), step.getDestination().getId());
                migrations.add(migration);

                r.getBackupReorg().add(backupStep);
                backupStep = new ArrayList<>();
            } else {
                Map<Long, String> migration = new HashMap<>();
                migration.put(step.getInstance().getId(), step.getDestination().getId());
                backupStep.add(migration);
                r.getBackups().put(step.getInstance().getId(), step.getInstance().getVmId());
            }
        }

        // Tailing backup space reorg
        r.getBackupReorg().add(backupStep);

        r.setMigrations(migrations);

        long time = System.currentTimeMillis();
        r.setAge(time - solution.getTimestamp());
        return r;
    }

    private Map<String, Set<String>> hostToInstancesToVms(Map<String, Set<Long>> h2i, Map<Long, Instance> instances) {
        Map<String, Set<String>> h2vm = new HashMap<>();
        for (Map.Entry<String, Set<Long>> hostMap: h2i.entrySet()) {
            h2vm.put(hostMap.getKey(), new HashSet<String>());
            for (Long instId: hostMap.getValue()) {
                Instance inst = instances.get(instId);
                if (inst.getPrimary()) {
                    h2vm.get(hostMap.getKey()).add(inst.getVmId());
                }
            }
        }
        return h2vm;
    }

    private Map<String, String> instanceToHostToVm(Map<Long, String> i2hs, Map<Long, Instance> instances) {
        Map<String, String> vm2h = new HashMap<>();
        for (Map.Entry<Long, String> i2h: i2hs.entrySet()) {
            Instance inst = instances.get(i2h.getKey());
            if (inst.getPrimary()) {
                vm2h.put(inst.getVmId(), i2h.getValue());
            }
        }
        return vm2h;
    }

    @Override
    public ScoreResult recomputeScore(OptimalDistributionStepsSolution situation, Result result) {
        HardSoftScore score = SolverUtils.computeScore(situation, result,
                Collections.<String>emptySet(),
                configProvider.customRuleFiles());

        ScoreResult scoreResult = new ScoreResult();
        scoreResult.setHardScore(score.getHardScore());
        scoreResult.setSoftScore(score.getSoftScore());
        return scoreResult;
    }

    @Override
    public Map<String, ScoreResult> simpleSchedule(String clusterId, OptimalDistributionStepsSolution situation,
            Result baseResult, String vmId) {
        Map<String, ScoreResult> results = new HashMap<>();
        Map<String, String> migration = new HashMap<>();
        List<Map<String, String>> migrations = new ArrayList<>();

        if (situation == null) {
            // Use the latest information when no base state was provided
            final ClusterOptimizer clusterOptimizer = clusterOptimizers.get(clusterId);
            if (clusterOptimizer == null) {
                return null;
            }

            situation = clusterOptimizer.getBestSolution();
        }

        // Mark the scheduled VM as running
        Set<String> vmStarts = new HashSet<>();
        vmStarts.add(vmId);

        Result dummyResult = new Result();

        if (baseResult != null) {
            // Use provided migration steps first when available
            migrations.addAll(baseResult.getMigrations());

            // Copy the backup space migrations
            dummyResult.setBackups(baseResult.getBackups());
            dummyResult.setBackupReorg(baseResult.getBackupReorg());

            // Mark all migrated base result VMs as running
            for (Map<String, String> step : migrations) {
                vmStarts.addAll(step.keySet());
            }
        }

        // Add the scheduling step migration
        migrations.add(migration);

        dummyResult.setMigrations(migrations);

        // Compute the score for each possible destination
        for (Host host : situation.getHosts()) {
            migration.clear();
            migration.put(vmId, host.getId());

            HardSoftScore score = SolverUtils.computeScore(situation, dummyResult,
                    vmStarts,
                    configProvider.customRuleFiles());
            ScoreResult scoreResult = new ScoreResult();
            scoreResult.setHardScore(score.getHardScore());
            scoreResult.setSoftScore(score.getSoftScore());
            results.put(host.getId(), scoreResult);
        }

        return results;
    }

    @Override
    public Map<String, DebugSnapshot> getDebugSnapshot() {
        Map<String, DebugSnapshot> snaps = new HashMap<>();

        for (ClusterOptimizer optimizer : clusterOptimizers.values()) {
            DebugSnapshot snap = new DebugSnapshot();
            snap.setCluster(optimizer.getClusterId());
            snap.setState(optimizer.getBestSolution());
            snap.setResult(solutionToResult(optimizer.getClusterId(), snap.getState()));
            snaps.put(optimizer.getClusterId(), snap);
        }

        return snaps;
    }

    @Override
    public Set<String> knownClusters() {
        return Collections.unmodifiableSet(clusterOptimizers.keySet());
    }
}
