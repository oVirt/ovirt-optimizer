package org.ovirt.optimizer.solver.problemspace;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.Solution;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@PlanningSolution
public class OptimalDistributionStepsSolution implements Solution<HardSoftScore>,
                                                         ClusterSituation {
    @JsonIgnore
    private HardSoftScore score;

    List<Migration> steps;
    Set<Host> hosts;
    Set<Object> otherFacts;
    Set<Object> fixedFacts;
    Set<VM> vms;

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
        facts.addAll(vms);
        facts.addAll(hosts);
        facts.addAll(otherFacts);
        facts.addAll(fixedFacts);
        return facts;
    }

    @ValueRangeProvider(id = "hosts")
    public Set<Host> getHosts() {
        return hosts;
    }

    public void setVms(Set<VM> vms) {
        this.vms = vms;
    }

    public void setHosts(Set<Host> hosts) {
        this.hosts = hosts;
    }

    @ValueRangeProvider(id = "vms")
    public Set<VM> getVms() {
        return vms;
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
    public Map<String, String> getVmToHostAssignments() {
        Map<String, String> situation = new HashMap<>();
        for (VM vm: vms) {
            situation.put(vm.getId(), vm.getHost()==null ? null : vm.getHost().getId());
        }
        return situation;
    }

    @Override
    public Map<String, Set<String>> getHostToVmAssignments() {
        Map<String, Set<String>> situation = new HashMap<>();
        for (Host h: hosts) {
            situation.put(h.getId(), new HashSet<String>());
        }

        for (VM vm: vms) {
            if (vm.getHost() == null) {
                continue;
            }
            
            situation.get(vm.getHost().getId()).add(vm.getId());
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
}
