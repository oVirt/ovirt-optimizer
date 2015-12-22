package org.ovirt.optimizer.solver.problemspace;

import org.junit.Test;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.ovirt.engine.sdk.entities.AffinityGroup;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.solver.facts.PolicyUnit;
import org.ovirt.optimizer.solver.facts.VmAffinityGroup;
import org.ovirt.optimizer.solver.test.TestOptimizer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AffinityTest {

    private static class TestResult{
        public int scoreSameHost;
        public int scoreDifferentHosts;
    }

    /**
     * Common method for affinity tests
     *
     * @return Scores when 2 VMs are on the same host and on different hosts
     */
    private TestResult commonAffinityTest( boolean enforcing, boolean positive){
        TestOptimizer optimizer = new TestOptimizer();

        Host h1 = optimizer.createHost("h1", 1000000000L);
        Host h2 = optimizer.createHost("h2", 1000000000L);

        VM vm1 = optimizer.createVm("vm1", 10000000L);
        VM vm2 = optimizer.createVm("vm2", 10000000L);

        vm1.setHost(h1);
        vm2.setHost(h1);

        AffinityGroup group = new AffinityGroup();
        group.setId("group");
        group.setPositive(positive);
        group.setEnforcing(enforcing);

        List<VM> list = new ArrayList<>();
        list.add(vm1);
        list.add(vm2);

        VmAffinityGroup groupFact = VmAffinityGroup.create(group, list);

        optimizer.enablePolicyUnit(PolicyUnit.VM_AFFINITY_FILTER)
                .enablePolicyUnit(PolicyUnit.VM_AFFINITY_WEIGHT, 1)
                .addFact(groupFact)
                .addMigration(vm1, h1);

        HardSoftScore r1 = optimizer.score();

        optimizer.addMigration(vm2, h2);
        HardSoftScore r2 = optimizer.score();

        TestResult res = new TestResult();

        res.scoreSameHost       = enforcing ? r1.getHardScore() : r1.getSoftScore();
        res.scoreDifferentHosts = enforcing ? r2.getHardScore() : r2.getSoftScore();

        return res;
    }

    @Test
    public void testEnforcingPositiveAffinity(){
        TestResult res = commonAffinityTest(true, true);
        assertEquals(0, res.scoreSameHost);
        assertNotEquals(0, res.scoreDifferentHosts);
    }

    @Test
    public void testEnforcingNegativeAffinity(){
        TestResult res = commonAffinityTest(true, false);
        assertEquals(0, res.scoreDifferentHosts);
        assertNotEquals(0, res.scoreSameHost);
    }

    @Test
    public void testNotEnforcingPositiveAffinity(){
        TestResult res = commonAffinityTest(false, true);
        assertTrue( res.scoreSameHost > res.scoreDifferentHosts );
    }

    @Test
    public void testNotEnforcingNegativeAffinity(){
        TestResult res = commonAffinityTest(false, false);
        assertTrue(res.scoreDifferentHosts > res.scoreSameHost);
    }
}
