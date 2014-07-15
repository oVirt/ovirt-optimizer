package org.ovirt.optimizer.service.problemspace;

import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.impl.solver.ProblemFactChange;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.Status;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.service.facts.RunningVm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This class implements Optaplanner's ProblemFactChange functionality and is used to
 * asynchronously update data in the Optaplanner's solver.
 */
public class ClusterFactChange implements ProblemFactChange {
    static final Logger log = LoggerFactory.getLogger(ClusterFactChange.class);

    final Set<VM> vms;
    final Set<Host> hosts;
    final Set<Object> facts;
    final String clusterId;

    public ClusterFactChange(String clusterId, Set<VM> vms, Set<Host> hosts, Set<Object> facts) {
        this.vms = vms;
        this.hosts = hosts;
        this.facts = facts;
        this.clusterId = clusterId;
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

        /* Recompute the caches in Migration steps
        ClusterSituation situation = space;
        for (Migration step: space.getSteps()) {
            step.recomputeSituationAfter(situation);
            situation = step;
        }*/
    }
}
