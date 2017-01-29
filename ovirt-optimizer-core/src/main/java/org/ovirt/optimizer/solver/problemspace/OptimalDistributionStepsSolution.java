package org.ovirt.optimizer.solver.problemspace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.Solution;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.solver.facts.Instance;

@PlanningSolution
public class OptimalDistributionStepsSolution implements Solution<HardSoftScore>,
                                                         ClusterSituation {
    @JsonIgnore
    private HardSoftScore score;

    private List<Migration> steps;
    private Set<Host> hosts;
    private Set<Object> otherFacts;
    private Set<Object> fixedFacts;
    private Map<String, VM> vms;
    private Set<Instance> instances;

    private String clusterId;

    // Timestamp when the solution was returned from optaplanner
    private long timestamp;

    @Override
    public HardSoftScore getScore() {
        return score;
    }

    @Override
    public void setScore(HardSoftScore score) {
        this.score = score;
    }

    public Set<Object> getFixedFacts() {
        return fixedFacts;
    }

    public void setFixedFacts(Set<Object> fixedFacts) {
        this.fixedFacts = fixedFacts;
    }

    @Override
    public Collection<?> getProblemFacts() {
        Collection<Object> facts = new ArrayList<>();
        facts.addAll(instances);
        facts.addAll(hosts);
        facts.addAll(otherFacts);
        facts.addAll(fixedFacts);
        facts.addAll(vms.values());
        return facts;
    }

    @ValueRangeProvider(id = "hosts")
    public Set<Host> getHosts() {
        return hosts;
    }

    public void setInstances(Set<Instance> instances) {
        this.instances = instances;
    }

    public void setHosts(Set<Host> hosts) {
        this.hosts = hosts;
    }

    @ValueRangeProvider(id = "instances")
    public Set<Instance> getInstances() {
        return instances;
    }

    public Set<Object> getOtherFacts() {
        return otherFacts;
    }

    public void setOtherFacts(Set<Object> otherFacts) {
        this.otherFacts = otherFacts;
    }

    @PlanningEntityCollectionProperty
    public List<Migration> getSteps() {
        return steps;
    }

    public void setSteps(List<Migration> steps) {
        this.steps = steps;
    }

    @Override
    public Map<Long, String> getInstanceToHostAssignments() {
        Map<Long, String> situation = new HashMap<>();
        for (Instance instance: instances) {
            VM vm = vms.get(instance.getVmId());
            situation.put(instance.getId(), vm.getHost()==null ? null : vm.getHost().getId());
        }
        return situation;
    }

    @Override
    public Map<String, Set<Long>> getHostToInstanceAssignments() {
        Map<String, Set<Long>> situation = new HashMap<>();
        for (Host h: hosts) {
            situation.put(h.getId(), new HashSet<>());
        }

        for (Instance instance: instances) {
            VM vm = vms.get(instance.getVmId());

            if (vm.getHost() == null) {
                continue;
            }
            
            situation.get(vm.getHost().getId()).add(instance.getId());
        }

        return situation;
    }

    public ClusterSituation getFinalSituation() {
        if (steps.isEmpty()) {
            return this;
        }
        else {
            return steps.get(steps.size() - 1);
        }
    }

    public void establishStepOrdering() {
        int stepsToFinish = steps.size();

        for(Migration m: steps) {
            m.setStepsToFinish(--stepsToFinish);
        }
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, VM> getVms() {
        return vms;
    }

    public void setVms(Map<String, VM> vms) {
        this.vms = vms;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }
}
