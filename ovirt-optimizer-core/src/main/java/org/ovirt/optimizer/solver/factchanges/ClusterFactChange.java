package org.ovirt.optimizer.solver.factchanges;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.impl.solver.ProblemFactChange;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.solver.facts.Instance;
import org.ovirt.optimizer.solver.facts.RunningVm;
import org.ovirt.optimizer.solver.problemspace.ClusterSituation;
import org.ovirt.optimizer.solver.problemspace.Migration;
import org.ovirt.optimizer.solver.problemspace.OptimalDistributionStepsSolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements Optaplanner's ProblemFactChange functionality and is used to
 * asynchronously update data in the Optaplanner's solver.
 */
public class ClusterFactChange implements ProblemFactChange {
    private static final Logger log = LoggerFactory.getLogger(ClusterFactChange.class);

    private final Set<VM> vms;
    private final Set<Host> hosts;
    private final Set<Object> facts;
    private final String clusterId;

    public ClusterFactChange(String clusterId, Set<VM> vms, Set<Host> hosts, Set<Object> facts) {
        this.vms = vms;
        this.hosts = hosts;
        this.facts = facts;
        this.clusterId = clusterId;
    }

    @Override
    public void doChange(ScoreDirector scoreDirector) {
        log.debug(String.format("Updating facts for cluster %s", clusterId));
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
        space.setHosts(new HashSet<>(space.getHosts()));
        space.setInstances(new HashSet<>(space.getInstances()));
        space.setOtherFacts(new HashSet<>(space.getOtherFacts()));

        // Record which host fact instances need to be removed later
        Collection<Host> oldHosts = new ArrayList<>(space.getHosts());

        // Record which VM instances were part of the solution (and thus have
        // the necessary instances already created
        Collection<String> oldVms = new HashSet<>(space.getVms().keySet());

        // Add all hosts (new instances) to the facts
        for (Map.Entry<String, Host> h : hostMap.entrySet()) {
            scoreDirector.beforeProblemFactAdded(h.getValue());
            space.getHosts().add(h.getValue());
            scoreDirector.afterProblemFactAdded(h.getValue());
        }

        // Remove old VM data
        for (Iterator<String> it = space.getVms().keySet().iterator(); it.hasNext(); ) {
            String vmId = it.next();
            VM vm = space.getVms().get(vmId);
            scoreDirector.beforeProblemFactRemoved(vm);
            it.remove();
            scoreDirector.afterProblemFactRemoved(vm);
        }

        // Add new VM data
        for (Map.Entry<String, VM> h : vmMap.entrySet()) {
            scoreDirector.beforeProblemFactAdded(h.getValue());
            space.getVms().put(h.getKey(), h.getValue());
            scoreDirector.afterProblemFactAdded(h.getValue());

            // New VM
            if (!oldVms.contains(h.getKey())) {
                Instance newInstance = new Instance(h.getKey());

                // TODO add more instances for HA reservation case

                scoreDirector.beforeProblemFactAdded(newInstance);
                space.getInstances().add(newInstance);
                scoreDirector.afterProblemFactAdded(newInstance);
            }
        }

        // Update the migration steps with new host and vm data
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
            if (step.getInstance() == null) {
                // nothing needed
            } else if (!vmMap.containsKey(step.getInstance().getVmId())) {
                // vm removed
                scoreDirector.beforeVariableChanged(step, "instance");
                step.setInstance(null);
                scoreDirector.afterVariableChanged(step, "instance");

            } else {
                // vm still present, nothing needed
            }
        }

        // Delete old hosts from the facts
        for (Host h : oldHosts) {
            scoreDirector.beforeProblemFactRemoved(h);
            space.getHosts().remove(h);
            scoreDirector.afterProblemFactRemoved(h);
        }

        // Delete old Instances from the facts
        for (Iterator<Instance> it = space.getInstances().iterator(); it.hasNext(); ) {
            Instance instance = it.next();

            if (space.getVms().containsKey(instance.getVmId())
                    && instance.getPrimary()) {
                // Always keep primary instance
                continue;
            } else if (space.getVms().containsKey(instance.getVmId())) {
                // TODO handle secondary instance removal when the HA flag is removed
                // and the instance has to be removed from Migrations too!
                continue;
            }

            scoreDirector.beforeProblemFactRemoved(instance);
            it.remove();
            scoreDirector.afterProblemFactRemoved(instance);
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

        // Remove VmStart requests for Vms that are already up
        for (Iterator<Object> i = space.getFixedFacts().iterator(); i.hasNext(); ) {
            Object fact = i.next();
            if (fact instanceof RunningVm
                    && vmMap.containsKey(((RunningVm)fact).getId())
                    && vmMap.get(((RunningVm)fact).getId()).getStatus().getState().toLowerCase().equals("up")) {
                scoreDirector.beforeProblemFactRemoved(fact);
                i.remove();
                scoreDirector.afterProblemFactRemoved(fact);
            }
        }

        /* Force refresh of shadow vars even though no migration changed as
         * there might have been a change to the base situation */
        ClusterSituation situation = space;
        for (Migration m: space.getSteps()) {
            log.trace("Recomputing shadow variables in {} ({})", m.toString(), m.getStepsToFinish());
            scoreDirector.beforeVariableChanged(m, "instanceToHostAssignments");
            scoreDirector.beforeVariableChanged(m, "hostToInstanceAssignments");
            scoreDirector.beforeVariableChanged(m, "start");
            scoreDirector.beforeVariableChanged(m, "valid");

            m.recomputeSituationAfter(situation);
            situation = m;

            scoreDirector.afterVariableChanged(m, "instanceToHostAssignments");
            scoreDirector.afterVariableChanged(m, "hostToInstanceAssignments");
            scoreDirector.afterVariableChanged(m, "start");
            scoreDirector.afterVariableChanged(m, "valid");
        }

        /* Required since Optaplanner 6.3.0 */
        scoreDirector.triggerVariableListeners();
    }
}
