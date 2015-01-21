package org.ovirt.optimizer.service.test;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.constraint.ConstraintMatch;
import org.optaplanner.core.api.score.constraint.ConstraintMatchTotal;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.ovirt.engine.sdk.entities.CPU;
import org.ovirt.engine.sdk.entities.Cluster;
import org.ovirt.engine.sdk.entities.Cores;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.MemoryOverCommit;
import org.ovirt.engine.sdk.entities.MemoryPolicy;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.engine.sdk.entities.VmPlacementPolicy;
import org.ovirt.optimizer.service.facts.PolicyUnit;
import org.ovirt.optimizer.service.facts.PolicyUnitEnabled;
import org.ovirt.optimizer.service.facts.RunningVm;
import org.ovirt.optimizer.service.problemspace.ClusterSituation;
import org.ovirt.optimizer.service.problemspace.Migration;
import org.ovirt.optimizer.service.problemspace.OptimalDistributionStepsSolution;
import org.ovirt.optimizer.service.problemspace.SituationTest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TestOptimizer {
    final Cluster cluster;
    final OptimalDistributionStepsSolution bestSolution;
    final Solver solver;
    int numberOfSteps = 1;

    public TestOptimizer(Set<ClusterFeatures> features) {
        SolverFactory solverFactory = SolverFactory.createFromXmlResource("org/ovirt/optimizer/service/rules/scoreonly.xml");
        solver = solverFactory.buildSolver();

        // Create new solution space
        bestSolution = new OptimalDistributionStepsSolution();
        bestSolution.setHosts(new HashSet<Host>());
        bestSolution.setVms(new HashSet<VM>());
        bestSolution.setOtherFacts(new HashSet<Object>());
        bestSolution.setFixedFacts(new HashSet<Object>());

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

        for (VM vm : bestSolution.getVms()) {
            if (vm.getHost() != null) {
                bestSolution.getOtherFacts().add(new RunningVm(vm.getId()));
            }
        }

        bestSolution.establishStepOrdering();
    }

    public OptimalDistributionStepsSolution run() {
        prepare();
        solver.solve(bestSolution);
        return (OptimalDistributionStepsSolution)solver.getBestSolution();
    }

    public HardSoftScore score() {
        prepare();

        ScoreDirector director = solver.getScoreDirectorFactory().buildScoreDirector();
        director.setWorkingSolution(bestSolution);
        return (HardSoftScore)director.calculateScore();
    }

    public TestOptimizer addMigration(VM vm, Host destination) {
        if (destination != null) {
            bestSolution.getHosts().add(destination);
        }

        if (vm != null) {
            bestSolution.getVms().add(vm);
        }

        bestSolution.getSteps().add(new Migration(vm, destination));
        return this;
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
        bestSolution.getVms().add(vm);
        return this;
    }

    public TestOptimizer enablePolicyUnit(PolicyUnit unit, int factor) {
        bestSolution.getFixedFacts().add(new PolicyUnitEnabled(unit.getUuid().toString(), factor));
        return this;
    }

    public TestOptimizer enablePolicyUnit(PolicyUnit unit) {
        bestSolution.getFixedFacts().add(new PolicyUnitEnabled(unit.getUuid().toString(), 1));
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

        bestSolution.getVms().add(vm);

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

        bestSolution.getHosts().add(host);

        return host;
    }
}
