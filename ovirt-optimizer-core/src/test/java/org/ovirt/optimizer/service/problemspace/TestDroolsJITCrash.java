package org.ovirt.optimizer.service.problemspace;

import org.junit.Test;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.ovirt.engine.sdk.entities.Cluster;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.MemoryPolicy;
import org.ovirt.engine.sdk.entities.VM;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class TestDroolsJITCrash  {
    /**
     * Test evaluation exception in Drools when using Optaplanner 6.1.0-SNAPSHOT
     */
    @Test
    public void testCrash() {
        SolverFactory solverFactory = SolverFactory.createFromXmlResource("org/ovirt/optimizer/test/rules/solver.xml");
        Solver solver = solverFactory.buildSolver();

        // Create new solution space
        OptimalDistributionStepsSolution bestSolution = new OptimalDistributionStepsSolution();
        bestSolution.setHosts(new HashSet<Host>());
        bestSolution.setVms(new HashSet<VM>());
        bestSolution.setOtherFacts(new HashSet<Object>());

        // Create cluster
        Cluster c = new Cluster();
        c.setId("test-cluster");
        MemoryPolicy m = new MemoryPolicy();
        m.setBallooning(true);
        c.setMemoryPolicy(m);

        // Create host
        Host h = new Host();
        h.setMemory(1024L);
        h.setId("test-host");
        h.setCluster(c);

        // Create VM
        VM vm = new VM();
        vm.setId("test-vm");
        vm.setCluster(c);
        vm.setMemory(512L);
        vm.setHost(h);
        MemoryPolicy memoryPolicy = new MemoryPolicy();
        memoryPolicy.setGuaranteed(256L);
        vm.setMemoryPolicy(memoryPolicy);

        // Add facts
        bestSolution.getHosts().add(h);
        bestSolution.getVms().add(vm);

        // Prepare the step placeholders
        List<Migration> migrationSteps = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            migrationSteps.add(new Migration());
        }
        bestSolution.setSteps(migrationSteps);
        bestSolution.establishStepOrdering();

        solver.solve(bestSolution);
    }

    @Test
    public void testClassPath() {
        URL url = getClass().getResource("org/ovirt/optimizer/test/rules/solver.xml");
        System.out.println(url);
    }
}
