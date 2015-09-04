package org.ovirt.optimizer.service.problemspace;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.variable.CustomShadowVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@PlanningEntity
public class Migration implements ClusterSituation {
    /* planning variables */
    VM vm;
    Host destination;

    /* steps to finish (0 means final state) */
    int stepsToFinish;

    /* shadow variables */
    Map<String, String> vmToHostAssignments;
    Map<String, Set<String>> hostToVmAssignments;
    boolean start;
    boolean valid;

    public Migration() {
        vmToHostAssignments = new HashMap<>();
        hostToVmAssignments = new HashMap<>();
    }

    public Migration(VM vm, Host destination) {
        this();
        this.vm = vm;
        this.destination = destination;
    }

    public void setVm(VM vm) {
        this.vm = vm;
    }

    @PlanningVariable(valueRangeProviderRefs = {"hosts"}, nullable = true)
    public Host getDestination() {
        return destination;
    }

    public void setDestination(Host host) {
        this.destination = host;
    }

    public boolean isFinalStep() {
        return stepsToFinish == 0;
    }

    public void setVmToHostAssignments(Map<String, String> vmToHostAssignments) {
        this.vmToHostAssignments = vmToHostAssignments;
    }

    public String getAssignment(String vm) {
        return vmToHostAssignments.get(vm);
    }

    /**
     * This method recomputes the cluster situation as a cumulative result
     * of this and all previous steps.
     */
    public void recomputeSituationAfter(ClusterSituation previous) {
        vmToHostAssignments = new HashMap<>(previous.getVmToHostAssignments());
        hostToVmAssignments = new HashMap<>();

        for (Map.Entry<String, Set<String>> item: previous.getHostToVmAssignments().entrySet()) {
            hostToVmAssignments.put(item.getKey(), new HashSet<String>(item.getValue()));
        }

        if (vm == null || destination == null) {
            // Incomplete data set, no migration is performed
            valid = false;
            return;
        }

        String source = vmToHostAssignments.get(vm.getId());
        if (source != null) {
            hostToVmAssignments.get(source).remove(vm.getId());
        }

        Set<String> hostSet = hostToVmAssignments.get(destination.getId());
        if (hostSet == null) {
            hostSet = new HashSet<>();
            hostToVmAssignments.put(destination.getId(), hostSet);
        }
        hostSet.add(vm.getId());

		/* Check whether the VM is newly started by this step -
           or in other words: if it was not assigned in the previous step
		 */
        start = (vmToHostAssignments.get(vm.getId()) == null);

		/* Check whether a migration is performed here. We can ignore this step
		   if the VM was already running on the destination host
		 */
        String oldHost = vmToHostAssignments.get(vm.getId());
        valid = oldHost == null || !oldHost.equals(destination.getId());

        vmToHostAssignments.put(vm.getId(), destination.getId());
    }

    @CustomShadowVariable(variableListenerClass = MigrationStepChangeListener.class,
            sources = {@CustomShadowVariable.Source(variableName = "destination"),
                    @CustomShadowVariable.Source(variableName = "vm")})
    @Override
    public Map<String, String> getVmToHostAssignments() {
        return vmToHostAssignments;
    }

    @CustomShadowVariable(variableListenerClass = MigrationStepChangeListener.class,
            sources = {@CustomShadowVariable.Source(variableName = "destination"),
                    @CustomShadowVariable.Source(variableName = "vm")})
    @Override
    public Map<String, Set<String>> getHostToVmAssignments() {
        return hostToVmAssignments;
    }

    public void setHostToVmAssignments(Map<String, Set<String>> hostToVmAssignments) {
        this.hostToVmAssignments = hostToVmAssignments;
    }

    /**
     * Check whether this migration represents a VM start action. The value is
     * precomputed using the recomputeSituationAfter hook.
     */
    @CustomShadowVariable(variableListenerClass = MigrationStepChangeListener.class,
            sources = {@CustomShadowVariable.Source(variableName = "destination"),
                    @CustomShadowVariable.Source(variableName = "vm")})
    public boolean isStart() {
        return start;
    }

    public void setStart(boolean isStart) {
        this.start = isStart;
    }

    /**
     * Check whether this migration is doing anything or represents an incomplete step.
     */
    @CustomShadowVariable(variableListenerClass = MigrationStepChangeListener.class,
            sources = {@CustomShadowVariable.Source(variableName = "destination"),
                    @CustomShadowVariable.Source(variableName = "vm")})
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean isValid) {
        this.valid = isValid;
    }

    @PlanningVariable(valueRangeProviderRefs = {"vms"}, nullable = true)
    public VM getVm() {
        return vm;
    }

    public int getStepsToFinish() {
        return stepsToFinish;
    }

    public void setStepsToFinish(int stepsToFinish) {
        this.stepsToFinish = stepsToFinish;
    }
}
