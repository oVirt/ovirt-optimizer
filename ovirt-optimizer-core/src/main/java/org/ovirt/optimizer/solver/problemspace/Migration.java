package org.ovirt.optimizer.solver.problemspace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.variable.CustomShadowVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.optimizer.solver.facts.Instance;

@PlanningEntity
public class Migration implements ClusterSituation {
    /* planning variables */
    private Instance instance;
    private Host destination;

    /* steps to finish (0 means final state) */
    private int stepsToFinish;

    /* shadow variables */
    private Map<Long, String> instanceToHostAssignments;
    private Map<String, Set<Long>> hostToInstanceAssignments;
    private boolean start;
    private boolean valid;

    public Migration() {
        instanceToHostAssignments = new HashMap<>();
        hostToInstanceAssignments = new HashMap<>();
    }

    public Migration(Instance instance, Host destination) {
        this();
        this.instance = instance;
        this.destination = destination;
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
    }

    @PlanningVariable(valueRangeProviderRefs = { "hosts" }, nullable = true)
    public Host getDestination() {
        return destination;
    }

    public void setDestination(Host host) {
        this.destination = host;
    }

    public boolean isFinalStep() {
        return stepsToFinish == 0;
    }

    public void setInstanceToHostAssignments(Map<Long, String> instanceToHostAssignments) {
        this.instanceToHostAssignments = instanceToHostAssignments;
    }

    public String getAssignment(Long instanceId) {
        return instanceToHostAssignments.get(instanceId);
    }

    /**
     * This method recomputes the cluster situation as a cumulative result
     * of this and all previous steps.
     */
    public void recomputeSituationAfter(ClusterSituation previous) {
        instanceToHostAssignments = new HashMap<>(previous.getInstanceToHostAssignments());
        hostToInstanceAssignments = new HashMap<>();

        for (Map.Entry<String, Set<Long>> item : previous.getHostToInstanceAssignments().entrySet()) {
            hostToInstanceAssignments.put(item.getKey(), new HashSet<>(item.getValue()));
        }

        if (instance == null
                || (destination == null && instance.getPrimary())) {
            // Incomplete data set, no migration is performed
            valid = false;
            return;
        }

        String source = instanceToHostAssignments.get(instance.getId());
        if (source != null) {
            hostToInstanceAssignments.get(source).remove(instance.getId());
        }

        if (destination != null) {
            Set<Long> hostSet = hostToInstanceAssignments.computeIfAbsent(destination.getId(), k -> new HashSet<>());
            hostSet.add(instance.getId());
        }

		/* Check whether the VM is newly started by this step -
           or in other words: if it was not assigned in the previous step
		 */
        start = (instanceToHostAssignments.get(instance.getId()) == null);

		/* Check whether a migration is performed here. We can ignore this step
           if the VM was already running on the destination host.

           The migration is also valid when a secondary instance is removed from
           hosts.
		 */
        String oldHost = instanceToHostAssignments.get(instance.getId());
        valid = (oldHost == null && destination != null)
                || (oldHost != null && destination == null && !instance.getPrimary())
                || (oldHost != null && destination != null && !oldHost.equals(destination.getId()));

        instanceToHostAssignments.put(instance.getId(), destination != null ? destination.getId() : null);
    }

    @CustomShadowVariable(variableListenerClass = MigrationStepChangeListener.class,
            sources = { @CustomShadowVariable.Source(variableName = "destination"),
                    @CustomShadowVariable.Source(variableName = "instance") })
    @Override
    public Map<Long, String> getInstanceToHostAssignments() {
        return instanceToHostAssignments;
    }

    @CustomShadowVariable(variableListenerClass = MigrationStepChangeListener.class,
            sources = { @CustomShadowVariable.Source(variableName = "destination"),
                    @CustomShadowVariable.Source(variableName = "instance") })
    @Override
    public Map<String, Set<Long>> getHostToInstanceAssignments() {
        return hostToInstanceAssignments;
    }

    public void setHostToInstanceAssignments(Map<String, Set<Long>> hostToInstanceAssignments) {
        this.hostToInstanceAssignments = hostToInstanceAssignments;
    }

    /**
     * Check whether this migration represents a VM start action. The value is
     * precomputed using the recomputeSituationAfter hook.
     */
    @CustomShadowVariable(variableListenerClass = MigrationStepChangeListener.class,
            sources = { @CustomShadowVariable.Source(variableName = "destination"),
                    @CustomShadowVariable.Source(variableName = "instance") })
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
            sources = { @CustomShadowVariable.Source(variableName = "destination"),
                    @CustomShadowVariable.Source(variableName = "instance") })
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean isValid) {
        this.valid = isValid;
    }

    @PlanningVariable(valueRangeProviderRefs = { "instances" }, nullable = true)
    public Instance getInstance() {
        return instance;
    }

    public int getStepsToFinish() {
        return stepsToFinish;
    }

    public void setStepsToFinish(int stepsToFinish) {
        this.stepsToFinish = stepsToFinish;
    }
}
