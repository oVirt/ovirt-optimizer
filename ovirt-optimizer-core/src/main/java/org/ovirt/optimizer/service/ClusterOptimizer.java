package org.ovirt.optimizer.service;

import org.apache.log4j.Logger;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.XmlSolverFactory;
import org.optaplanner.core.impl.event.BestSolutionChangedEvent;
import org.optaplanner.core.impl.event.SolverEventListener;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.impl.solver.ProblemFactChange;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.service.problemspace.OptimalDistributionSolution;
import org.ovirt.optimizer.service.problemspace.VmAssignment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This class represents the task for optimization
 * of a single cluster.
 */
public class ClusterOptimizer implements Runnable {
    private static Logger log = Logger.getLogger(ClusterOptimizer.class);
    final Solver solver;
    final String clusterId;
    OptimalDistributionSolution bestSolution;
    final ClusterInfoUpdater updater;
    final Finished finishedCallback;

    public interface Finished {
        void solvingFinished(ClusterOptimizer planner, Thread thread);
    }

    public ClusterOptimizer(OvirtClient client, final String clusterId, Finished finishedCallback) {
        this.clusterId = clusterId;
        this.finishedCallback = finishedCallback;

        SolverFactory solverFactory = new XmlSolverFactory("/org/ovirt/optimizer/service/rules/solver.xml");

        solver = solverFactory.buildSolver();
        solver.addEventListener(new SolverEventListener() {
            @Override
            public void bestSolutionChanged(BestSolutionChangedEvent bestSolutionChangedEvent) {
                // Ignore incomplete solutions
                if (!solver.isEveryProblemFactChangeProcessed()) {
                    log.debug("Ignoring incomplete solution");
                    return;
                }

                synchronized (ClusterOptimizer.this) {
                    bestSolution = (OptimalDistributionSolution)bestSolutionChangedEvent.getNewBestSolution();
                }
                log.info(String.format("New solution for %s available (score %s)",
                        clusterId, bestSolution.getScore().toString()));
            }
        });

        // Create new solution space
        bestSolution = new OptimalDistributionSolution();
        bestSolution.setHosts(new HashSet<Host>());
        bestSolution.setVms(new HashSet<VmAssignment>());
        bestSolution.setOtherFacts(new HashSet<Object>());

        solver.setPlanningProblem(bestSolution);

        // Configure updater so we can pass information to the solution space
        updater = new ClusterInfoUpdater(client, clusterId, new ClusterInfoUpdater.ClusterUpdateAvailable() {
            @Override
            public void checkUpdate(final Set<VM> vms, final Set<Host> hosts, final Set<Object> facts) {
                solver.addProblemFactChange(new ClusterFactChange(vms, hosts, facts));
            }
        });
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
            OptimalDistributionSolution space = (OptimalDistributionSolution) scoreDirector.getWorkingSolution();

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

            // Remove missing VMs from the solution
            Set<String> remainingVmIds = new HashSet<>();
            for (Iterator<VmAssignment> it = space.getVms().iterator(); it.hasNext(); ) {
                VmAssignment assignment = it.next();
                if (!vmMap.containsKey(assignment.getId())) {
                    scoreDirector.beforeEntityRemoved(assignment);
                    it.remove();
                    scoreDirector.afterEntityRemoved(assignment);

                    // Remove the Vm from facts
                    scoreDirector.beforeProblemFactRemoved(assignment.getVm());
                    space.getRawVms().remove(assignment.getVm());
                    scoreDirector.afterProblemFactRemoved(assignment.getVm());
                } else {
                    remainingVmIds.add(assignment.getId());
                }
            }

            // Create new host fact set
            space.setHosts(new HashSet<Host>(space.getHosts()));

            // Record which host fact instances need to be removed later
            Collection<Host> oldHosts = new ArrayList<>(space.getHosts());

            // Add all hosts (new instances) to the facts
            for (Map.Entry<String, Host> h : hostMap.entrySet()) {
                scoreDirector.beforeProblemFactAdded(h.getValue());
                space.getHosts().add(h.getValue());
                scoreDirector.afterProblemFactAdded(h.getValue());
            }

            // Update the solution with new host and vm data
            for (VmAssignment assignment : space.getVms()) {
                // Remove missing hosts from the VMs
                if (!hostMap.containsKey(assignment.getHost().getId())) {
                    scoreDirector.beforeVariableChanged(assignment, "hosts");
                    assignment.setHost(null);
                    scoreDirector.afterVariableChanged(assignment, "hosts");

                    // and update the solution with new instances of Hosts
                } else if (assignment.getHost() != null) {
                    scoreDirector.beforeVariableChanged(assignment, "hosts");
                    assignment.setHost(hostMap.get(assignment.getHost().getId()));
                    scoreDirector.afterVariableChanged(assignment, "hosts");
                }

                // Replace the old VM instance with the updated VM instance
                VM oldVm = assignment.getVm();
                VM newVm = vmMap.get(assignment.getId());

                // Add new VM to facts
                scoreDirector.beforeProblemFactAdded(newVm);
                space.getRawVms().add(newVm);
                scoreDirector.afterProblemFactAdded(newVm);

                // Update the backend instances of VM
                scoreDirector.beforeProblemFactChanged(assignment);
                assignment.setVm(newVm);
                scoreDirector.afterProblemFactChanged(assignment);

                // Remove old VM from facts
                scoreDirector.beforeProblemFactRemoved(oldVm);
                space.getRawVms().remove(oldVm);
                scoreDirector.afterProblemFactRemoved(oldVm);
            }

            // Delete old hosts from the facts
            for (Host h : oldHosts) {
                scoreDirector.beforeProblemFactRemoved(h);
                space.getHosts().remove(h);
                scoreDirector.afterProblemFactRemoved(h);
            }

            // Find new VMs and insert them
            Set<String> newVmIds = new HashSet<>(vmMap.keySet());
            newVmIds.removeAll(remainingVmIds);
            for (String vmId : newVmIds) {
                // Add new raw Vm to facts
                VM newVm = vmMap.get(vmId);
                scoreDirector.beforeProblemFactAdded(newVm);
                space.getRawVms().add(newVm);
                scoreDirector.afterProblemFactAdded(newVm);

                // Create new VM to Host assignment with empty host
                VmAssignment newAssignment = new VmAssignment(newVm);
                newAssignment.setHost(newAssignment.getVm().getHost());
                scoreDirector.beforeEntityAdded(newAssignment);
                space.getVms().add(newAssignment);
                scoreDirector.afterEntityAdded(newAssignment);
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
        }
    }

    public ClusterInfoUpdater getUpdaterInstance() {
        return updater;
    }

    void solve() {
        log.info(String.format("Solver for %s starting", clusterId));
        solver.solve();
        log.info(String.format("Solver for %s finished", clusterId));
        synchronized (this) {
            bestSolution = (OptimalDistributionSolution) solver.getBestSolution();
        }
    }

    public OptimalDistributionSolution getBestSolution() {
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
