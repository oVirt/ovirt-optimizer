package org.ovirt.optimizer.service;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.constraint.ConstraintMatch;
import org.optaplanner.core.api.score.constraint.ConstraintMatchTotal;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.event.BestSolutionChangedEvent;
import org.optaplanner.core.api.solver.event.SolverEventListener;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.service.facts.RunningVm;
import org.ovirt.optimizer.service.problemspace.CancelVmRunningFactChange;
import org.ovirt.optimizer.service.problemspace.ClusterSituation;
import org.ovirt.optimizer.service.problemspace.EnsureVmRunningFactChange;
import org.ovirt.optimizer.service.problemspace.Migration;
import org.ovirt.optimizer.service.problemspace.OptimalDistributionStepsSolution;
import org.ovirt.optimizer.util.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * This class represents the task for optimization
 * of a single cluster.
 */
public class ClusterOptimizer implements Runnable {
    private static Logger log = LoggerFactory.getLogger(ClusterOptimizer.class);
    final Solver solver;
    final String clusterId;
    private volatile OptimalDistributionStepsSolution bestSolution;
    ClusterInfoUpdater updater;
    ClusterInfoUpdater.ClusterUpdateAvailable updateHandler;
    final Finished finishedCallback;
    final List<File> customDrlFiles;

    public interface Finished {
        void solvingFinished(ClusterOptimizer planner, Thread thread);
    }

    private void addCustomDrlFiles(ScoreDirectorFactoryConfig config, List<File> customDrlFiles) {
        if (config.getScoreDrlFileList() != null) {
            config.getScoreDrlFileList().addAll(customDrlFiles);
        } else {
            config.setScoreDrlFileList(customDrlFiles);
        }
    }

    /**
     * Use the state in the current best solution to recompute score of
     * provided migrations.
     *
     * This method creates a new solver with disabled construction heuristics
     * and local search.
     *
     * @param migrationIds
     * @return HardSoftScore of the migrations
     */
    HardSoftScore computeScore(List<Map<String, String>> migrationIds) {
        OptimalDistributionStepsSolution sourceSolution = null;

        synchronized (ClusterOptimizer.this) {
            sourceSolution = bestSolution;
        }

        log.debug("Reevaluating solution {}", migrationIds);

        SolverFactory solverFactory = SolverFactory.createFromXmlResource("org/ovirt/optimizer/service/rules/scoreonly.xml");
        addCustomDrlFiles(solverFactory.getSolverConfig().getScoreDirectorFactoryConfig(), customDrlFiles);
        Solver solver = solverFactory.buildSolver();

        /* Reconstruct the Solution object with current facts */
        OptimalDistributionStepsSolution solution = new OptimalDistributionStepsSolution();
        solution.setHosts(sourceSolution.getHosts());
        solution.setVms(sourceSolution.getVms());
        solution.setOtherFacts(sourceSolution.getOtherFacts());
        solution.setFixedFacts(sourceSolution.getFixedFacts());

        /* Get id to object mappings for hosts and VMs */
        Map<String, Host> hosts = new HashMap<>();
        for (Host h: solution.getHosts()) {
            hosts.put(h.getId(), h);
            log.debug("Found host {}", h.getId());
        }

        Map<String, VM> vms = new HashMap<>();
        for (VM vm: solution.getVms()) {
            vms.put(vm.getId(), vm);
            log.debug("Found VM {}", vm.getId());
        }

        /* Recreate the migration objects */
        List<Migration> migrations = new ArrayList<>();
        for (Map<String, String> migrationStep: migrationIds) {
            for (Map.Entry<String, String> singleMigration: migrationStep.entrySet()) {
                // Create new migration step
                Migration migration = new Migration();
                String hostId = singleMigration.getValue();

                // Inject current host data
                if (hosts.containsKey(hostId)) {
                    migration.setDestination(hosts.get(hostId));
                    log.debug("Setting destination for {} to {}", migration, hostId);
                }
                else {
                    log.warn("Host {} is no longer valid", hostId);
                }

                // Inject current VM data
                String vmId = singleMigration.getKey();
                if (vms.containsKey(vmId)) {
                    migration.setVm(vms.get(vmId));
                    log.debug("Setting VM for {} to {}", migration, vmId);
                }
                else {
                    log.warn("VM {} is no longer valid", vmId);
                }

                // Add the step to the list of steps
                migrations.add(migration);
            }
        }

        /* Compute the migration ordering cache */
        solution.setSteps(migrations);
        solution.establishStepOrdering();

        /* Prepare shadow variables */
        ClusterSituation previous = solution;
        for (Migration m: migrations) {
            m.recomputeSituationAfter(previous);
            previous = m;
        }

        /* Recompute score */
        solver.solve(solution);

        if (log.isDebugEnabled()) {
            recomputeScoreUsingScoreDirector(solver, solution);
        }

        return solution.getScore();
    }

    public void registerUpdater(ClusterInfoUpdater updater, ClusterInfoUpdater.ClusterUpdateAvailable handler) {
        this.updater = updater;
        this.updateHandler = handler;

        if (updater != null && updateHandler != null) {
            updater.addHandler(updateHandler);
        }
    }

    public static ClusterOptimizer optimizeCluster(OvirtClient client, ConfigProvider configProvider, final String clusterId, int maxSteps, Finished finishedCallback) {
        long timeout = Integer.valueOf(configProvider.load().getConfig().getProperty(ConfigProvider.SOLVER_TIMEOUT)) * 1000;
        ClusterOptimizer optimizer = new ClusterOptimizer(clusterId, maxSteps, timeout, finishedCallback, configProvider.customRuleFiles());
        ClusterInfoUpdater updater = new ClusterInfoUpdater(client, configProvider, clusterId);
        optimizer.registerUpdater(updater, new ClusterUpdateAvailableForOptimizer(clusterId, optimizer.getSolver()));
        return optimizer;
    }

    private ClusterOptimizer(final String clusterId, int maxSteps, long unimprovedMilisLimit, Finished finishedCallback,
                             List<File> customDrlFiles) {
        this.clusterId = clusterId;
        this.finishedCallback = finishedCallback;
        this.customDrlFiles = customDrlFiles;

        SolverFactory solverFactory = SolverFactory.createFromXmlResource("org/ovirt/optimizer/service/rules/solver.xml");
        addCustomDrlFiles(solverFactory.getSolverConfig().getScoreDirectorFactoryConfig(), this.customDrlFiles);
        SolverConfig solverConfig = solverFactory.getSolverConfig();
        TerminationConfig terminationConfig = solverConfig.getTerminationConfig();
        terminationConfig.setUnimprovedMillisecondsSpentLimit(unimprovedMilisLimit);

        solver = solverFactory.buildSolver();

        solver.addEventListener(new SolverEventListener() {
            @Override
            public void bestSolutionChanged(BestSolutionChangedEvent bestSolutionChangedEvent) {
                // Ignore incomplete solutions
                if (!solver.isEveryProblemFactChangeProcessed()) {
                    log.debug("Ignoring incomplete solution");
                    return;
                }

                if(!bestSolutionChangedEvent.isNewBestSolutionInitialized()) {
                    log.debug("Ignoring uninitialized solution");
                    return;
                }

                synchronized (ClusterOptimizer.this) {
                    bestSolution = (OptimalDistributionStepsSolution)bestSolutionChangedEvent.getNewBestSolution();
                    log.info(String.format("New solution for %s available (score %s)",
                            clusterId, bestSolution.getScore().toString()));

                    if (log.isDebugEnabled()) {
                        recomputeScoreUsingScoreDirector(solver, bestSolution);
                    }
                }
            }
        });

        // Create new solution space
        bestSolution = new OptimalDistributionStepsSolution();
        bestSolution.setHosts(new HashSet<Host>());
        bestSolution.setVms(new HashSet<VM>());
        bestSolution.setOtherFacts(new HashSet<Object>());
        bestSolution.setFixedFacts(new HashSet<Object>());

        // Prepare the step placeholders
        List<Migration> migrationSteps = new ArrayList<>();
        for (int i = 0; i < maxSteps; i++) {
            migrationSteps.add(new Migration());
        }
        bestSolution.setSteps(migrationSteps);
        bestSolution.establishStepOrdering();

        ClusterSituation previous = bestSolution;
        for (Migration m: migrationSteps) {
            m.recomputeSituationAfter(previous);
            previous = m;
        }
    }

    /**
     * Recompute solution's score and log all rules that affected it (in debug mode)
     * This method uses new score director from the passed solver
     * @param solver
     * @param solution
     */
    private void recomputeScoreUsingScoreDirector(Solver solver, OptimalDistributionStepsSolution solution) {
        ScoreDirector director = solver.getScoreDirectorFactory().buildScoreDirector();
        director.setWorkingSolution(solution);
        director.calculateScore();
        for (ConstraintMatchTotal constraintMatchTotal : director.getConstraintMatchTotals()) {
            String constraintName = constraintMatchTotal.getConstraintName();
            Number weightTotal = constraintMatchTotal.getWeightTotalAsNumber();
            for (ConstraintMatch constraintMatch : constraintMatchTotal.getConstraintMatchSet()) {
                List<Object> justificationList = constraintMatch.getJustificationList();
                Number weight = constraintMatch.getWeightAsNumber();
                log.debug("Constraint match {} with weight {}", constraintMatch, weight);
                for (Object item: justificationList) {
                    log.debug("Justified by {}", item);
                }
            }
        }
        log.debug("Final score {}", bestSolution.getScore().toString());
    }

    public ClusterInfoUpdater getUpdaterInstance() {
        return updater;
    }

    void solve() {
        log.info(String.format("Solver for %s starting", clusterId));
        solver.solve(bestSolution);
        log.info(String.format("Solver for %s finished", clusterId));
        synchronized (this) {
            bestSolution = (OptimalDistributionStepsSolution) solver.getBestSolution();
        }
    }

    public OptimalDistributionStepsSolution getBestSolution() {
        synchronized (this) {
            return bestSolution;
        }
    }

    public void terminate() {
        log.info(String.format("Solver thread for %s was asked to terminate", clusterId));
        solver.terminateEarly();
        updater.removeHandler(updateHandler);
    }

    public void run() {
        log.info(String.format("Solver thread for %s starting", clusterId));
        solve();
        finishedCallback.solvingFinished(this, Thread.currentThread());
        log.info(String.format("Solver thread for %s finished", clusterId));
    }

    private Solver getSolver() {
        return solver;
    }

    public void ensureVmIsRunning(String uuid) {
        solver.addProblemFactChange(new EnsureVmRunningFactChange(uuid));
    }

    public void cancelVmIsRunning(String uuid) {
        solver.addProblemFactChange(new CancelVmRunningFactChange(uuid));
    }
}
