package org.ovirt.optimizer.service.problemspace;

import org.junit.Test;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.Status;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.service.facts.PolicyUnit;
import org.ovirt.optimizer.service.test.ClusterFeatures;
import org.ovirt.optimizer.service.test.TestOptimizer;

import java.util.EnumSet;

import static org.junit.Assert.*;

public class SituationTest {

    /**
     * Test whether a pinned to host VM can be started
     */
    @Test
    public void testStartPinnedVm() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host = optimizer.createHost("H1", 1000000000L);

        VM vm = optimizer.createVm("VM-A", 10000000L);
        vm.getPlacementPolicy().setHost(host);
        vm.getPlacementPolicy().setAffinity("pinned");

        HardSoftScore result = optimizer
                .startVm(vm)
                .addMigration(vm, host)
                .enablePolicyUnit(PolicyUnit.PIN_TO_HOST_FILTER)
                .score();

        assertEquals(0, result.getHardScore());
    }

    /**
     * Test whether a pinned to host VM can't be migrated
     */
    @Test
    public void testMigratePinnedVm() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host1 = optimizer.createHost("H1", 1000000000L);
        Host host2 = optimizer.createHost("H2", 1000000000L);

        VM vm = optimizer.createVm("VM-A", 10000000L);
        vm.getPlacementPolicy().setHost(host1);
        vm.getPlacementPolicy().setAffinity("pinned");
        vm.setHost(host1);

        HardSoftScore result = optimizer
                .addMigration(vm, host2)
                .enablePolicyUnit(PolicyUnit.PIN_TO_HOST_FILTER)
                .score();

        assertNotEquals(0, result.getHardScore());
    }

    /**
     * Test whether a VM can't be started when it does not fit
     */
    @Test
    public void testStartHugeVm() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host = optimizer.createHost("H1", 1000L);
        VM vm = optimizer.createVm("VM-A", 10000000L);

        HardSoftScore result = optimizer
                .startVm(vm)
                .addMigration(vm, host)
                .enablePolicyUnit(PolicyUnit.MEMORY_FILTER)
                .score();

        assertNotEquals(0, result.getHardScore());
    }

    /**
     * Test whether a VM in Down state can't be started without user's request
     */
    @Test
    public void testUnexpectedStart() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host1 = optimizer.createHost("H1", 1000000000L);
        Host host2 = optimizer.createHost("H2", 1000000000L);

        VM vm1 = optimizer.createVm("VM-A", 10000000L);
        vm1.setHost(host1);
        vm1.setStatus(new Status());
        vm1.getStatus().setState("UP");

        VM vm2 = optimizer.createVm("VM-B", 10000000L);
        vm2.setStatus(new Status());
        vm2.getStatus().setState("DOWN");

        HardSoftScore result = optimizer
                .addMigration(vm2, host2)
                .score();

        assertNotEquals(0, result.getHardScore());
    }
}
