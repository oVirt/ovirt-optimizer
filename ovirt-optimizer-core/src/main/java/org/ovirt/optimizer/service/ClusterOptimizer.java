package org.ovirt.optimizer.service;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.constraint.ConstraintMatch;
import org.optaplanner.core.api.score.constraint.ConstraintMatchTotal;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.event.BestSolutionChangedEvent;
import org.optaplanner.core.api.solver.event.SolverEventListener;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.impl.solver.ProblemFactChange;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.service.problemspace.ClusterSituation;
import org.ovirt.optimizer.service.problemspace.Migration;
import org.ovirt.optimizer.service.problemspace.OptimalDistributionStepsSolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class represents the task for optimization
 * of a single cluster.
 */
public class ClusterOptimizer implements Runnable {
    private static Logger log = LoggerFactory.getLogger(ClusterOptimizer.class);
    final Solver solver;
    final String clusterId;
    private volatile OptimalDistributionStepsSolution bestSolution;
    final ClusterInfoUpdater updater;
    final Finished finishedCallback;

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
        Solver solver = solverFactory.buildSolver();

        /* Reconstruct the Solution object with current facts */
        OptimalDistributionStepsSolution solution = new OptimalDistributionStepsSolution();
        solution.setHosts(sourceSolution.getHosts());
        solution.setVms(sourceSolution.getVms());
        solution.setOtherFacts(sourceSolution.getOtherFacts());

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

    public ClusterOptimizer(OvirtClient client, final String clusterId, int maxSteps, Finished finishedCallback) {
        this.clusterId = clusterId;
        this.finishedCallback = finishedCallback;

        SolverFactory solverFactory = SolverFactory.createFromXmlResource("org/ovirt/optimizer/service/rules/solver.xml");

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

        // Configure updater so we can pass information to the solution space
        updater = new ClusterInfoUpdater(client, clusterId);
        updater.addHandler(new ClusterInfoUpdater.ClusterUpdateAvailable() {
            @Override
            public void checkUpdate(final Set<VM> vms, final Set<Host> hosts, final Set<Object> facts) {
                solver.addProblemFactChange(new ClusterFactChange(vms, hosts, facts));
            }
        });
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

    /**
     * This class implements Optaplanner's ProblemFactChange functionality and is used to
     * asynchronously update data in the Optaplanner's solver.
     */
    private class ClusterFactChange implements ProblemFactChange {
        final Set<VM> vms;
        final Set<Host> hosts;
        final Set<Object> facts;

        private ClusterFactChange(Set<VM> vms, Set<Host> hosts, Set<Object> facts) {
            this.vms = vms;
            this.hosts = hosts;
            this.facts = facts;
        }

        @Override
        public void doChange(ScoreDirector scoreDirector) {
            log.info(String.format("Updating facts for cluster %s", clusterId));
            OptimalDistributionStepsSolution space = (OptimalDistributionStepsSolution) scoreDirector.getWorkingSolution();

            // Create a mapping between hostId and new Host information
            Map<String, Host> hostMap = new HashMap<>();
            for (Host h : hosts) {
                hostMap.put(h.getId(), h);
            }

            // Create a mapping between vmId and the new VM information
            Map<String, VM> vmMap = new HashMap<>();
            for (VM vm : vms) {
                vmMap.put(vm.getId(), vm);
            }

            // Create new host and vm fact set to not disturb other solutions
            space.setHosts(new HashSet<Host>(space.getHosts()));
            space.setVms(new HashSet<VM>(space.getVms()));
            space.setOtherFacts(new HashSet<Object>(space.getOtherFacts()));

            // Record which host fact instances need to be removed later
            Collection<Host> oldHosts = new ArrayList<>(space.getHosts());
            Collection<VM> oldVMs = new ArrayList<>(space.getVms());

            // Add all hosts (new instances) to the facts
            for (Map.Entry<String, Host> h : hostMap.entrySet()) {
                scoreDirector.beforeProblemFactAdded(h.getValue());
                space.getHosts().add(h.getValue());
                scoreDirector.afterProblemFactAdded(h.getValue());
            }

            // Add all VMs (new instances) to the facts
            for (Map.Entry<String, VM> h : vmMap.entrySet()) {
                scoreDirector.beforeProblemFactAdded(h.getValue());
                space.getVms().add(h.getValue());
                scoreDirector.afterProblemFactAdded(h.getValue());
            }

            // Update the solution with new host and vm data
            for (Migration step: space.getSteps()) {
                // Remove missing hosts from the migrations
                if (step.getDestination() == null) {
                    // nothing needed
                } else if (!hostMap.containsKey(step.getDestination().getId())) {
                    scoreDirector.beforeVariableChanged(step, "destination");
                    step.setDestination(null);
                    scoreDirector.afterVariableChanged(step, "destination");

                    // and update the solution with new instances of Hosts
                } else {
                    scoreDirector.beforeVariableChanged(step, "destination");
                    step.setDestination(hostMap.get(step.getDestination().getId()));
                    scoreDirector.afterVariableChanged(step, "destination");
                }

                // Remove missing VMs from the migrations
                if (step.getVm() == null) {
                    // nothing needed
                } else if (!hostMap.containsKey(step.getVm().getId())) {
                    scoreDirector.beforeVariableChanged(step, "vm");
                    step.setVm(null);
                    scoreDirector.afterVariableChanged(step, "vm");

                    // and update the solution with new instances of VMs
                } else {
                    scoreDirector.beforeVariableChanged(step, "vm");
                    step.setVm(vmMap.get(step.getVm().getId()));
                    scoreDirector.afterVariableChanged(step, "vm");
                }
            }

            // Delete old hosts and vms from the facts
            for (Host h : oldHosts) {
                scoreDirector.beforeProblemFactRemoved(h);
                space.getHosts().remove(h);
                scoreDirector.afterProblemFactRemoved(h);
            }
            for (VM vm : oldVMs) {
                scoreDirector.beforeProblemFactRemoved(vm);
                space.getVms().remove(vm);
                scoreDirector.afterProblemFactRemoved(vm);
            }
            
            // Remove old helper facts (Networks, PolicyUnits and other data)
            for (Iterator<Object> it = space.getOtherFacts().iterator(); it.hasNext(); ) {
                Object fact = it.next();
                scoreDirector.beforeProblemFactRemoved(fact);
                it.remove();
                scoreDirector.afterProblemFactRemoved(fact);
            }

            // Add new helper facts
            for (Object fact : facts) {
                scoreDirector.beforeProblemFactAdded(fact);
                space.getOtherFacts().add(fact);
                scoreDirector.afterProblemFactAdded(fact);
            }

            /* Recompute the caches in Migration steps
            ClusterSituation situation = space;
            for (Migration step: space.getSteps()) {
                step.recomputeSituationAfter(situation);
                situation = step;
            }*/
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
    }

    public void run() {
        log.info(String.format("Solver thread for %s starting", clusterId));
        solve();
        finishedCallback.solvingFinished(this, Thread.currentThread());
        log.info(String.format("Solver thread for %s finished", clusterId));
    }
}
