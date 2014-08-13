package org.ovirt.optimizer.service;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.common.Result;
import org.ovirt.optimizer.common.ScoreResult;
import org.ovirt.optimizer.service.facts.RunningVm;
import org.ovirt.optimizer.service.problemspace.Migration;
import org.ovirt.optimizer.service.problemspace.OptimalDistributionStepsSolution;
import org.ovirt.optimizer.util.Autoload;
import org.ovirt.optimizer.util.ConfigProvider;
import org.ovirt.optimizer.util.SchedulerService;
import org.quartz.JobDetail;
import org.slf4j.Logger;

import javax.annotation.ManagedBean;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;


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
    SchedulerService scheduler;

    @Inject
    ClusterDiscovery discovery;

    @Inject
    OvirtClient client;

    @Inject
    ConfigProvider configProvider;

    SchedulerService.Timer discoveryTimer;
    Set<Thread> threads;

    // This attribute is used by the exported API and has to
    // be used in thread safe way
    final Map<String, ClusterOptimizer> clusterOptimizers = new HashMap<>();

    @PostConstruct
    public void create() {
        log.info("oVirt optimizer service starting");
        threads = new HashSet<>();
        int refresh = Integer.valueOf(configProvider.load().getConfig().getProperty(ConfigProvider.SOLVER_CLUSTER_REFRESH));
        discoveryTimer = scheduler.createTimer(refresh, DiscoveryTimeout.class);
    }

    // Synchronized should not be needed, but is here as a
    // safeguard for prevention from threading mistakes
    public synchronized void discoveryTimeout(final JobDetail timer){
        // Check for possible spurious timeouts from old instances
        if (timer.getKey() != discoveryTimer.getJobDetail().getKey()) {
            log.warn(String.format("Unknown timeout from %s", timer.toString()));
            return;
        }

        log.info("Discovering clusters...");
        Set<String> availableClusters = discovery.getClusters();
        if (availableClusters == null) {
            log.error("Cluster discovery failed");
            return;
        }

        Set<String> missingClusters;

        synchronized (clusterOptimizers) {
            /* Compute a set of clusters that disappeared */
            missingClusters = new HashSet<>(clusterOptimizers.keySet());
            missingClusters.removeAll(availableClusters);

            /* Compute a set of new clusters */
            availableClusters.removeAll(clusterOptimizers.keySet());
        }

        Properties config = new ConfigProvider().load().getConfig();
        final int maxSteps = Integer.valueOf(config.getProperty(ConfigProvider.SOLVER_STEPS));

        for (String clusterId: availableClusters) {
            log.info(String.format("New cluster %s detected", clusterId));

            ClusterOptimizer planner = ClusterOptimizer.optimizeCluster(client, configProvider, clusterId, maxSteps, new ClusterOptimizer.Finished() {
                @Override
                public void solvingFinished(ClusterOptimizer planner, Thread thread) {
                    threads.remove(thread);
                }
            });

            Thread updater = new Thread(planner.getUpdaterInstance());
            Thread solver = new Thread(planner);

            updater.start();
            threads.add(updater);

            solver.start();
            threads.add(solver);

            synchronized (clusterOptimizers) {
                clusterOptimizers.put(clusterId, planner);
            }
        }

        synchronized (clusterOptimizers) {
            for (String clusterId: missingClusters) {
                clusterOptimizers.get(clusterId).terminate();
                clusterOptimizers.get(clusterId).getUpdaterInstance().terminate();
                log.info(String.format("Cluster %s was removed", clusterId));
            }
        }
    }

    @PreDestroy
    public void stop() {
        log.info("oVirt service service stopping");
        discoveryTimer.cancel();

        synchronized (clusterOptimizers) {
            for (ClusterOptimizer clusterOptimizer: clusterOptimizers.values()) {
                clusterOptimizer.getUpdaterInstance().terminate();
                clusterOptimizer.terminate();
            }
        }

        log.info("Waiting for threads to finish");
        // Iterate over copy of the set as the ending threads will
        // be removed by callback
        for (Thread thread: new ArrayList<>(threads)) {
            try {
                thread.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("oVirt service service stopped");
    }

    public Set<String> getAllClusters() {
        synchronized (clusterOptimizers) {
            return clusterOptimizers.keySet();
        }
    }

    public Set<String> getActiveClusters() {
        synchronized (clusterOptimizers) {
            return clusterOptimizers.keySet();
        }
    }

    public void computeVmStart(String cluster, String uuid) {
        ClusterOptimizer clusterOptimizer;

        synchronized (clusterOptimizers) {
            clusterOptimizer = clusterOptimizers.get(cluster);
        }

        if (clusterOptimizer == null) {
            log.error(String.format("Cluster %s does not exist", cluster));
            return;
        }

        clusterOptimizer.ensureVmIsRunning(uuid);
    }

    public void cancelVmStart(String cluster, String uuid) {
        ClusterOptimizer clusterOptimizer;

        synchronized (clusterOptimizers) {
            clusterOptimizer = clusterOptimizers.get(cluster);
        }

        if (clusterOptimizer == null) {
            log.error(String.format("Cluster %s does not exist", cluster));
            return;
        }

        clusterOptimizer.cancelVmIsRunning(uuid);
    }

    @Override
    public ScoreResult recomputeScore(String cluster, Result oldResult) {
        ClusterOptimizer clusterOptimizer;

        synchronized (clusterOptimizers) {
            clusterOptimizer = clusterOptimizers.get(cluster);
        }

        if (clusterOptimizer == null) {
            log.error(String.format("Cluster %s does not exist", cluster));
            return null;
        }

        ScoreResult scoreResult = new ScoreResult();
        HardSoftScore score = clusterOptimizer.computeScore(oldResult.getMigrations());
        scoreResult.setHardScore(score.getHardScore());
        scoreResult.setSoftScore(score.getSoftScore());
        return scoreResult;
    }

    @Override
    public Result getCurrentResult(String cluster) {
        ClusterOptimizer clusterOptimizer;
        Result r;

        synchronized (clusterOptimizers) {
            clusterOptimizer = clusterOptimizers.get(cluster);
        }

        if (clusterOptimizer == null) {
            log.error(String.format("Cluster %s does not exist", cluster));
            r = Result.createEmpty(cluster);
        }
        else {
            OptimalDistributionStepsSolution best = clusterOptimizer.getBestSolution();

            r = new Result(cluster);
            r.setHardScore(best.getScore().getHardScore());
            r.setSoftScore(best.getScore().getSoftScore());

            r.setHosts(new HashSet<String>());
            for (Host h: best.getHosts()) {
                r.getHosts().add(h.getId());
            }

            r.setVms(new HashSet<String>());
            for (VM vm: best.getVms()) {
                r.getVms().add(vm.getId());
            }

            r.setRequestedVms(new HashSet<String>());
            for (Object fact: best.getFixedFacts()) {
                if (fact instanceof RunningVm) {
                    r.getRequestedVms().add(((RunningVm)fact).getId());
                }
            }

            r.setHostToVms(best.getFinalSituation().getHostToVmAssignments());
            r.setVmToHost(best.getFinalSituation().getVmToHostAssignments());
            r.setCurrentVmToHost(best.getVmToHostAssignments());

            List<Map<String, String>> migrations = new ArrayList<>();
            for (Migration step: best.getSteps()) {
                if (step.getVm() == null || step.getDestination() == null) {
                    continue;
                }
                Map<String, String> migration = new HashMap<>();
                migration.put(step.getVm().getId(), step.getDestination().getId());
                migrations.add(migration);
            }
            r.setMigrations(migrations);
        }

        return r;
    }
}
