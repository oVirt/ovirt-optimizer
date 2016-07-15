package org.ovirt.optimizer.solver.problemspace;

import org.junit.Before;
import org.junit.Test;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.solver.facts.AffinityLabel;
import org.ovirt.optimizer.solver.facts.PolicyUnit;
import org.ovirt.optimizer.solver.facts.RunningVm;
import org.ovirt.optimizer.solver.test.TestOptimizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.UUID;

public class AffinityLabelTest {
    Host host;
    VM vm;
    TestOptimizer optimizer;

    @Before
    public void commonAffinityTest() {
        optimizer = new TestOptimizer();
        host = optimizer.createHost("h1", 1000000000L);
        vm = optimizer.createVm("vm1", 10000000L);

        optimizer.enablePolicyUnit(PolicyUnit.AFFINITY_LABEL_FILTER)
                .addFact(new RunningVm(vm.getId()))
                .addMigration(vm, host);
    }

    private void assertValidSolution() {
        HardSoftScore score = optimizer.score();
        assertEquals(0, score.getHardScore());
    }

    private void assertInvalidSolution() {
        HardSoftScore score = optimizer.score();
        assertNotEquals(0, score.getHardScore());
    }

    @Test
    public void testNoLabels() throws Exception {
        assertValidSolution();
    }

    @Test
    public void testMissingLabels() throws Exception {
        AffinityLabel label = new AffinityLabel(UUID.randomUUID().toString(), vm.getId());
        optimizer.addFact(label);

        assertInvalidSolution();
    }

    @Test
    public void testValidLabels() throws Exception {
        AffinityLabel label = new AffinityLabel(UUID.randomUUID().toString(), vm.getId());
        optimizer.addFact(label);

        label = new AffinityLabel(label.getId(), host.getId());
        optimizer.addFact(label);

        assertValidSolution();
    }

    @Test
    public void testExtraLabels() throws Exception {
        AffinityLabel label = new AffinityLabel(UUID.randomUUID().toString(), host.getId());
        optimizer.addFact(label);

        assertValidSolution();
    }
}
