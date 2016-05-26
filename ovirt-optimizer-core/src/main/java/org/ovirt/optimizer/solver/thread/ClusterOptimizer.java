package org.ovirt.optimizer.solver.thread;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.event.BestSolutionChangedEvent;
import org.optaplanner.core.api.solver.event.SolverEventListener;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.config.ConfigProvider;
import org.ovirt.optimizer.ovirt.OvirtClient;
import org.ovirt.optimizer.rest.dto.Result;
import org.ovirt.optimizer.solver.factchanges.CancelVmRunningFactChange;
import org.ovirt.optimizer.solver.factchanges.ClusterUpdateAvailable;
import org.ovirt.optimizer.solver.factchanges.ClusterUpdateAvailableForOptimizer;
import org.ovirt.optimizer.solver.factchanges.EnsureVmRunningFactChange;
import org.ovirt.optimizer.solver.facts.Instance;
import org.ovirt.optimizer.solver.problemspace.ClusterSituation;
import org.ovirt.optimizer.solver.problemspace.Migration;
import org.ovirt.optimizer.solver.problemspace.OptimalDistributionStepsSolution;
import org.ovirt.optimizer.solver.util.SolverUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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
    final Solver<OptimalDistributionStepsSolution> solver;
    final String clusterId;
    private volatile OptimalDistributionStepsSolution bestSolution;
    ClusterInfoUpdater updater;
    ClusterUpdateAvailable updateHandler;
    final Finished finishedCallback;
    final List<File> customDrlFiles;

    public interface Finished {
        void solvingFinished(ClusterOptimizer planner, Thread thread);
    }

    /**
     * Use the state in the current best solution to recompute score of
     * provided migrations.
     *
     * This method creates a new solver with disabled construction heuristics
     * and local search.
     *
     * @param oldResult
     * @return HardSoftScore of the migrations
     */
    public HardSoftScore computeScore(Result oldResult) {
        OptimalDistributionStepsSolution sourceSolution = null;

        synchronized (ClusterOptimizer.this) {
            sourceSolution = bestSolution;
        }

        return SolverUtils.computeScore(sourceSolution,
                oldResult,
                Collections.<String>emptySet(),
                customDrlFiles);
    }

    public void registerUpdater(ClusterInfoUpdater updater, ClusterUpdateAvailable handler) {
        this.updater = updater;
        this.updateHandler = handler;

        if (updater != null && updateHandler != null) {
            updater.addHandler(updateHandler);
        }
    }

    public static ClusterOptimizer optimizeCluster(OvirtClient client, ConfigProvider configProvider, final String clusterId, int maxSteps, Finished finishedCallback) {
        long timeout = Integer.parseInt(configProvider.load().getConfig().getProperty(ConfigProvider.SOLVER_TIMEOUT)) * 1000;
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

        SolverFactory<OptimalDistributionStepsSolution> solverFactory;

        if (maxSteps > 0) {
            // Construct full optimizer
            solverFactory =
                    SolverFactory.createFromXmlResource("org/ovirt/optimizer/solver/rules/solver.xml");

            SolverUtils.addCustomDrlFiles(solverFactory.getSolverConfig().getScoreDirectorFactoryConfig(),
                    this.customDrlFiles);

            // Suspend solver when no better solution has been found for some time
            SolverConfig solverConfig = solverFactory.getSolverConfig();
            TerminationConfig terminationConfig = solverConfig.getTerminationConfig();
            terminationConfig.setUnimprovedMillisecondsSpentLimit(unimprovedMilisLimit);
        } else {
            // No optimization requested, degrade to daemonized score only solver and
            // provide only simple one-by-one scheduling as a service
            solverFactory =
                    SolverFactory.createFromXmlResource("org/ovirt/optimizer/solver/rules/scoreonly.xml");

            // There has to be at least one empty step to trigger the final situation rules
            maxSteps = 1;

            SolverUtils.addCustomDrlFiles(solverFactory.getSolverConfig().getScoreDirectorFactoryConfig(),
                    this.customDrlFiles);

            // Make sure the solver runs in daemon mode
            SolverConfig solverConfig = solverFactory.getSolverConfig();
            solverConfig.setDaemon(true);

            log.warn("Optimization disabled, enabling scoring subsystem only.");
        }

        solver = solverFactory.buildSolver();

        solver.addEventListener(new SolverEventListener<OptimalDistributionStepsSolution>() {
            @Override
            public void bestSolutionChanged(BestSolutionChangedEvent<OptimalDistributionStepsSolution> bestSolutionChangedEvent) {
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
                    // Get new solution and set the timestamp to current time
                    bestSolution = bestSolutionChangedEvent.getNewBestSolution();
                    bestSolution.setTimestamp(System.currentTimeMillis());

                    log.info(String.format("New solution for %s available (score %s)",
                            clusterId, bestSolution.getScore().toString()));

                    if (log.isDebugEnabled()) {
                        SolverUtils.recomputeScoreUsingScoreDirector(solver, bestSolution);
                    }
                }
            }
        });

        // Create new solution space
        bestSolution = new OptimalDistributionStepsSolution();

        bestSolution.setHosts(new HashSet<Host>());
        bestSolution.setInstances(new HashSet<Instance>());
        bestSolution.setOtherFacts(new HashSet<Object>());
        bestSolution.setFixedFacts(new HashSet<Object>());
        bestSolution.setVms(new HashMap<String, VM>());

        // Prepare the step placeholders
        List<Migration> migrationSteps = new ArrayList<>();
        for (int i = 0; i < maxSteps; i++) {
            migrationSteps.add(new Migration());
        }
        bestSolution.setSteps(migrationSteps);
        bestSolution.establishStepOrdering();

        bestSolution.setTimestamp(System.currentTimeMillis());

        ClusterSituation previous = bestSolution;
        for (Migration m: migrationSteps) {
            m.recomputeSituationAfter(previous);
            previous = m;
        }
    }

    public ClusterInfoUpdater getUpdaterInstance() {
        return updater;
    }

    void solve() {
        log.info(String.format("Solver for %s starting", clusterId));
        solver.solve(bestSolution);
        log.info(String.format("Solver for %s finished", clusterId));
        synchronized (this) {
            bestSolution = solver.getBestSolution();
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

    public String getClusterId() {
        return clusterId;
    }

    public void ensureVmIsRunning(String uuid) {
        solver.addProblemFactChange(new EnsureVmRunningFactChange(uuid));
    }

    public void cancelVmIsRunning(String uuid) {
        solver.addProblemFactChange(new CancelVmRunningFactChange(uuid));
    }
}
