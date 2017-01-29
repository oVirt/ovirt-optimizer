package org.ovirt.optimizer.solver.test;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.constraint.ConstraintMatch;
import org.optaplanner.core.api.score.constraint.ConstraintMatchTotal;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.ovirt.engine.sdk.entities.CPU;
import org.ovirt.engine.sdk.entities.Cluster;
import org.ovirt.engine.sdk.entities.CpuTopology;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.MemoryOverCommit;
import org.ovirt.engine.sdk.entities.MemoryPolicy;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.engine.sdk.entities.VmPlacementPolicy;
import org.ovirt.optimizer.solver.facts.HostInfo;
import org.ovirt.optimizer.solver.facts.Instance;
import org.ovirt.optimizer.solver.facts.PolicyUnit;
import org.ovirt.optimizer.solver.facts.PolicyUnitEnabled;
import org.ovirt.optimizer.solver.facts.RunningVm;
import org.ovirt.optimizer.solver.facts.VmInfo;
import org.ovirt.optimizer.solver.problemspace.ClusterSituation;
import org.ovirt.optimizer.solver.problemspace.Migration;
import org.ovirt.optimizer.solver.problemspace.OptimalDistributionStepsSolution;
import org.ovirt.optimizer.solver.util.SolverUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestOptimizer {
    final Cluster cluster;
    final OptimalDistributionStepsSolution bestSolution;
    final Solver solver;
    int numberOfSteps = 1;

    HashSet<VmInfo> vmInfos = new HashSet<>();
    HashSet<HostInfo> hostInfos = new HashSet<>();
    Map<VM, Instance> primaryInstances = new HashMap<>();

    public TestOptimizer(){
        this(EnumSet.noneOf(ClusterFeatures.class));
    }

    public TestOptimizer(Set<ClusterFeatures> features) {
        solver = SolverUtils.getScoreOnlySolver(Collections.emptyList());

        // Create new solution space
        bestSolution = new OptimalDistributionStepsSolution();
        bestSolution.setHosts(new HashSet<>());
        bestSolution.setInstances(new HashSet<>());
        bestSolution.setOtherFacts(new HashSet<>());
        bestSolution.setFixedFacts(new HashSet<>());
        bestSolution.setVms(new HashMap<>());

        // Create cluster
        cluster = new Cluster();
        cluster.setId("test-cluster");
        MemoryPolicy m = new MemoryPolicy();
        m.setBallooning(features.contains(ClusterFeatures.BALLOONING));
        m.setOverCommit(new MemoryOverCommit());
        m.getOverCommit().setPercent(100);
        cluster.setMemoryPolicy(m);

        // Prepare the step placeholders
        List<Migration> migrationSteps = new ArrayList<>();
        bestSolution.setSteps(migrationSteps);
    }

    private void prepare() {
        while (bestSolution.getSteps().size() < numberOfSteps) {
            bestSolution.getSteps().add(new Migration());
        }

        ClusterSituation situation = bestSolution;
        for (Migration m: bestSolution.getSteps()) {
            m.recomputeSituationAfter(situation);
            situation = m;
        }

        for (VM vm : bestSolution.getVms().values()) {
            if (vm.getHost() != null) {
                bestSolution.getOtherFacts().add(new RunningVm(vm.getId()));
            }
        }

        bestSolution.establishStepOrdering();

        for(Host h : bestSolution.getHosts()){
            HostInfo info = HostInfo.createFromHost(h, true);
            hostInfos.add(info);
            bestSolution.getFixedFacts().add(info);
        }

        for(VM vm : bestSolution.getVms().values()){
            VmInfo info = VmInfo.createFromVm(vm, true);
            vmInfos.add(info);
            bestSolution.getFixedFacts().add(info);
        }
    }

    private void cleanup(){
        bestSolution.getFixedFacts().removeAll( hostInfos );
        hostInfos.clear();

        bestSolution.getFixedFacts().removeAll(vmInfos);
        vmInfos.clear();
    }


    public OptimalDistributionStepsSolution run() {
        prepare();
        solver.solve(bestSolution);
        OptimalDistributionStepsSolution solution = (OptimalDistributionStepsSolution)solver.getBestSolution();
        cleanup();

        return solution;
    }

    public HardSoftScore score() {
        prepare();
        ScoreDirector director = solver.getScoreDirectorFactory().buildScoreDirector();
        director.setWorkingSolution(bestSolution);
        HardSoftScore score = (HardSoftScore)director.calculateScore();

        for (ConstraintMatchTotal constraintMatchTotal : director.getConstraintMatchTotals()) {
            String constraintName = constraintMatchTotal.getConstraintName();
            Number weightTotal = constraintMatchTotal.getWeightTotalAsNumber();
            for (ConstraintMatch constraintMatch : constraintMatchTotal.getConstraintMatchSet()) {
                List<Object> justificationList = constraintMatch.getJustificationList();
                Number weight = constraintMatch.getWeightAsNumber();
                System.out.println(String.format("Constraint match %s with weight %d", constraintMatch, weight.intValue()));
                for (Object item : justificationList) {
                    System.out.println(String.format("Justified by %s", item));
                }
            }
        }

        cleanup();

        return score;
    }

    public TestOptimizer addMigration(Instance vm, Host destination) {
        if (destination != null) {
            bestSolution.getHosts().add(destination);
        }

        if (vm != null) {
            bestSolution.getInstances().add(vm);
        }

        bestSolution.getSteps().add(new Migration(vm, destination));
        return this;
    }

    public TestOptimizer addMigration(VM vm, Host destination) {
        return addMigration(primaryInstances.get(vm), destination);
    }

    public TestOptimizer setNumberOfSteps(int count) {
        numberOfSteps = count;
        return this;
    }

    public TestOptimizer addHost(Host host) {
        bestSolution.getHosts().add(host);
        return this;
    }

    public TestOptimizer addVm(VM vm) {
        bestSolution.getVms().put(vm.getId(), vm);
        final Instance instance = new Instance(vm);
        primaryInstances.put(vm, instance);
        bestSolution.getInstances().add(instance);
        return this;
    }

    public TestOptimizer addFact(Object fact){
        bestSolution.getFixedFacts().add(fact);
        return this;
    }


    public TestOptimizer enablePolicyUnit(PolicyUnit unit, int factor) {
        bestSolution.getFixedFacts().add(new PolicyUnitEnabled(unit.getUuid(), factor));
        return this;
    }

    public TestOptimizer enablePolicyUnit(PolicyUnit unit) {
        bestSolution.getFixedFacts().add(new PolicyUnitEnabled(unit.getUuid(), 1));
        return this;
    }

    public TestOptimizer startVm(VM vm) {
        bestSolution.getFixedFacts().add(new RunningVm(vm.getId()));
        return this;
    }

    public VM createVm(String name, Long memory) {
        VM vm = new VM();
        vm.setCluster(cluster);
        vm.setName(name);
        vm.setId(name);
        vm.setPlacementPolicy(new VmPlacementPolicy());
        vm.getPlacementPolicy().setAffinity("migratable");
        vm.setMemory(memory);
        vm.setMemoryPolicy(new MemoryPolicy());
        vm.getMemoryPolicy().setBallooning(false);
        vm.getMemoryPolicy().setGuaranteed(memory);
        vm.setCpu(new CPU());
        vm.getCpu().setArchitecture("Westmere");
        vm.getCpu().setTopology(new CpuTopology());

        addVm(vm);

        return vm;
    }

    public Host createHost(String name, Long memory) {
        Host host = new Host();
        host.setName(name);
        host.setId(name);
        host.setMemory(memory);
        host.setCluster(cluster);
        host.setCpu(new CPU());
        host.getCpu().setArchitecture("Westmere");
        host.getCpu().setTopology(new CpuTopology());

        addHost(host);

        return host;
    }

    public Instance getPrimaryInstance(VM vm) {
        return primaryInstances.get(vm);
    }
}
