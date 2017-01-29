package org.ovirt.optimizer.solver.thread;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.rest.dto.Result;
import org.ovirt.optimizer.solver.factchanges.CancelVmRunningFactChange;
import org.ovirt.optimizer.solver.factchanges.ClusterFactChange;
import org.ovirt.optimizer.solver.factchanges.EnsureVmRunningFactChange;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * This class represents the task for optimization
 * of a single cluster.
 */
public class ClusterOptimizer implements Supplier<OptimalDistributionStepsSolution> {
    private static Logger log = LoggerFactory.getLogger(ClusterOptimizer.class);
    private final Solver<OptimalDistributionStepsSolution> solver;
    private final String clusterId;
    private final AtomicReference<OptimalDistributionStepsSolution> bestSolution = new AtomicReference<>();
    private final List<File> customDrlFiles;

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

        sourceSolution = bestSolution.get();

        return SolverUtils.computeScore(sourceSolution,
                oldResult,
                Collections.<String>emptySet(),
                customDrlFiles);
    }

    public ClusterOptimizer(final String clusterId, int maxSteps, long unimprovedMilisLimit,
                             List<File> customDrlFiles) {
        this.clusterId = clusterId;
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

        solver.addEventListener(bestSolutionChangedEvent -> {
            // Ignore incomplete solutions
            if (!solver.isEveryProblemFactChangeProcessed()) {
                log.debug("Ignoring incomplete solution");
                return;
            }

            if(!bestSolutionChangedEvent.isNewBestSolutionInitialized()) {
                log.debug("Ignoring uninitialized solution");
                return;
            }

            // Get new solution and set the timestamp to current time
            OptimalDistributionStepsSolution solution = bestSolutionChangedEvent.getNewBestSolution();
            solution.setTimestamp(System.currentTimeMillis());

            log.info(String.format("New solution for %s available (score %s)",
                    clusterId, solution.getScore().toString()));
            bestSolution.set(solution);

            if (log.isDebugEnabled()) {
                SolverUtils.recomputeScoreUsingScoreDirector(solver, solution);
            }
        });

        // Create new solution space
        OptimalDistributionStepsSolution solution = new OptimalDistributionStepsSolution();

        solution.setClusterId(clusterId);
        solution.setHosts(new HashSet<>());
        solution.setInstances(new HashSet<>());
        solution.setOtherFacts(new HashSet<>());
        solution.setFixedFacts(new HashSet<>());
        solution.setVms(new HashMap<>());

        // Prepare the step placeholders
        List<Migration> migrationSteps = new ArrayList<>();
        for (int i = 0; i < maxSteps; i++) {
            migrationSteps.add(new Migration());
        }
        solution.setSteps(migrationSteps);
        solution.establishStepOrdering();

        solution.setTimestamp(System.currentTimeMillis());

        ClusterSituation previous = solution;
        for (Migration m: migrationSteps) {
            m.recomputeSituationAfter(previous);
            previous = m;
        }

        bestSolution.set(solution);
    }

    private void solve() {
        log.info(String.format("Solver for %s starting", clusterId));
        solver.solve(bestSolution.get());
        log.info(String.format("Solver for %s finished", clusterId));
        bestSolution.set(solver.getBestSolution());
    }

    public OptimalDistributionStepsSolution getBestSolution() {
        return bestSolution.get();
    }

    public void terminate() {
        log.info(String.format("Solver thread for %s was asked to terminate", clusterId));
        solver.terminateEarly();
    }

    @Override
    public OptimalDistributionStepsSolution get() {
        log.info(String.format("Solver thread for %s starting", clusterId));
        solve();
        log.info(String.format("Solver thread for %s finished", clusterId));
        return bestSolution.get();
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

    public void processUpdate(final Set<VM> vms, final Set<Host> hosts, final Set<Object> facts) {
        solver.addProblemFactChange(new ClusterFactChange(clusterId, vms, hosts, facts));
    }
}
