package org.ovirt.optimizer.service.problemspace;

import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.value.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.impl.solution.Solution;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

@PlanningSolution
public class OptimalDistributionSolution implements Solution<HardSoftScore> {
    private HardSoftScore score;

    Set<VmAssignment> vms;
    Set<Host> hosts;
    Set<Object> otherFacts;
    Set<VM> rawVms;

    @Override
    public HardSoftScore getScore() {
        return score;
    }

    @Override
    public void setScore(HardSoftScore hardSoftScore) {
        score = hardSoftScore;
    }

    @Override
    public Collection<?> getProblemFacts() {
        Collection<Host> facts = new ArrayList<>();
        facts.addAll(hosts);
        return facts;
    }

    @ValueRangeProvider(id = "hosts")
    public Set<Host> getHosts() {
        return hosts;
    }

    public void setVms(Set<VmAssignment> vms) {
        this.vms = vms;
    }

    public void setHosts(Set<Host> hosts) {
        this.hosts = hosts;
    }

    @PlanningEntityCollectionProperty
    public Set<VmAssignment> getVms() {
        return vms;
    }

    public Set<Object> getOtherFacts() {
        return otherFacts;
    }

    public void setOtherFacts(Set<Object> otherFacts) {
        this.otherFacts = otherFacts;
    }

    public Set<VM> getRawVms() {
        return rawVms;
    }

    public void setRawVms(Set<VM> rawVms) {
        this.rawVms = rawVms;
    }
}
