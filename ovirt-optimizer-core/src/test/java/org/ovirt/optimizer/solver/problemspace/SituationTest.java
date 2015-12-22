package org.ovirt.optimizer.solver.problemspace;

import org.junit.Test;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.HostedEngine;
import org.ovirt.engine.sdk.entities.Status;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.solver.facts.PolicyUnit;
import org.ovirt.optimizer.solver.test.ClusterFeatures;
import org.ovirt.optimizer.solver.test.TestOptimizer;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

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
     * Test whether a pinned to host VM can be started on different host
     */
    @Test
    public void testStartPinnedVmOnWrongHost() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host = optimizer.createHost("H1", 1000000000L);
        Host host2 = optimizer.createHost("H2", 1000000000L);

        VM vm = optimizer.createVm("VM-A", 10000000L);
        vm.getPlacementPolicy().setHost(host);
        vm.getPlacementPolicy().setAffinity("pinned");

        HardSoftScore result = optimizer
                .startVm(vm)
                .addMigration(vm, host2)
                .enablePolicyUnit(PolicyUnit.PIN_TO_HOST_FILTER)
                .score();

        assertNotEquals(0, result.getHardScore());
    }

    /**
     * Test whether a pinned to host VM can be started on proper host
     * with migration of other VM performed first
     */
    @Test
    public void testStartPinnedVmWithMigration() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host = optimizer.createHost("H1", 1000000000L);
        Host host2 = optimizer.createHost("H2", 1000000000L);

        // Create new instance of host to mimic the SDK's behaviour better
        Host dummyhost = new Host();
        dummyhost.setId("H1");

        VM vm = optimizer.createVm("VM-A", 10000000L);
        vm.getPlacementPolicy().setHost(dummyhost);
        vm.getPlacementPolicy().setAffinity("pinned");

        VM vm2 = optimizer.createVm("VM-B", 10000000L);
        vm2.setHost(host);

        HardSoftScore result = optimizer
                .startVm(vm)
                .addMigration(vm2, host2)
                .addMigration(vm, host)
                .enablePolicyUnit(PolicyUnit.EVEN_GUEST_DIST_WEIGHT)
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

    /**
     * Test migration of hosted engine to non-HE enabled host
     */
    @Test
    public void testInvalidHEMigrationHostNotConfigured() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host1 = optimizer.createHost("H1", 1000000000L);
        host1.setHostedEngine(new HostedEngine());
        host1.getHostedEngine().setActive(true);
        host1.getHostedEngine().setScore(2400);
        host1.getHostedEngine().setConfigured(true);
        host1.getHostedEngine().setGlobalMaintenance(false);
        host1.getHostedEngine().setLocalMaintenance(false);

        Host host2 = optimizer.createHost("H2", 1000000000L);

        VM vm1 = optimizer.createVm("VM-A", 10000000L);
        vm1.setHost(host1);
        vm1.setStatus(new Status());
        vm1.getStatus().setState("UP");
        vm1.setOrigin("hostedEngine");

        HardSoftScore result = optimizer
                .enablePolicyUnit(PolicyUnit.HOSTED_ENGINE_FILTER)
                .addMigration(vm1, host2)
                .score();

        assertNotEquals(0, result.getHardScore());
    }

    /**
     * Test migration of hosted engine to HE enabled host
     * with score 0
     */
    @Test
    public void testInvalidHEMigrationHostNotReady() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host1 = optimizer.createHost("H1", 1000000000L);
        host1.setHostedEngine(new HostedEngine());
        host1.getHostedEngine().setActive(true);
        host1.getHostedEngine().setScore(2400);
        host1.getHostedEngine().setConfigured(true);
        host1.getHostedEngine().setGlobalMaintenance(false);
        host1.getHostedEngine().setLocalMaintenance(false);

        Host host2 = optimizer.createHost("H2", 1000000000L);
        host2.setHostedEngine(new HostedEngine());
        host2.getHostedEngine().setActive(true);
        host2.getHostedEngine().setScore(0);
        host2.getHostedEngine().setConfigured(true);
        host2.getHostedEngine().setGlobalMaintenance(false);
        host2.getHostedEngine().setLocalMaintenance(false);

        VM vm1 = optimizer.createVm("VM-A", 10000000L);
        vm1.setHost(host1);
        vm1.setStatus(new Status());
        vm1.getStatus().setState("UP");
        vm1.setOrigin("hostedEngine");

        HardSoftScore result = optimizer
                .enablePolicyUnit(PolicyUnit.HOSTED_ENGINE_FILTER)
                .addMigration(vm1, host2)
                .score();

        assertNotEquals(0, result.getHardScore());
    }

    /**
     * Test migration of hosted engine to HE enabled host
     * with score lower than the host that is currently
     * used
     */
    @Test
    public void testValidHEMigrationHostInferior() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host1 = optimizer.createHost("H1", 1000000000L);
        host1.setHostedEngine(new HostedEngine());
        host1.getHostedEngine().setActive(true);
        host1.getHostedEngine().setScore(2400);
        host1.getHostedEngine().setConfigured(true);
        host1.getHostedEngine().setGlobalMaintenance(false);
        host1.getHostedEngine().setLocalMaintenance(false);

        Host host2 = optimizer.createHost("H2", 1000000000L);
        host2.setHostedEngine(new HostedEngine());
        host2.getHostedEngine().setActive(true);
        host2.getHostedEngine().setScore(2000);
        host2.getHostedEngine().setConfigured(true);
        host2.getHostedEngine().setGlobalMaintenance(false);
        host2.getHostedEngine().setLocalMaintenance(false);

        VM vm1 = optimizer.createVm("VM-A", 10000000L);
        vm1.setHost(host1);
        vm1.setStatus(new Status());
        vm1.getStatus().setState("UP");
        vm1.setOrigin("hostedEngine");

        HardSoftScore result = optimizer
                .enablePolicyUnit(PolicyUnit.HOSTED_ENGINE_FILTER)
                .enablePolicyUnit(PolicyUnit.HOSTED_ENGINE_WEIGHT)
                .addMigration(vm1, host2)
                .score();

        assertEquals(0, result.getHardScore());
        // -1 for single performed migration
        assertNotEquals(-1, result.getSoftScore());
    }

    /**
     * Test migration of hosted engine to HE enabled host
     * with score higher than the host that is currently
     * used
     */
    @Test
    public void testValidHEMigrationHostBetter() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host1 = optimizer.createHost("H1", 1000000000L);
        host1.setHostedEngine(new HostedEngine());
        host1.getHostedEngine().setActive(true);
        host1.getHostedEngine().setScore(2000);
        host1.getHostedEngine().setConfigured(true);
        host1.getHostedEngine().setGlobalMaintenance(false);
        host1.getHostedEngine().setLocalMaintenance(false);

        Host host2 = optimizer.createHost("H2", 1000000000L);
        host2.setHostedEngine(new HostedEngine());
        host2.getHostedEngine().setActive(true);
        host2.getHostedEngine().setScore(2400);
        host2.getHostedEngine().setConfigured(true);
        host2.getHostedEngine().setGlobalMaintenance(false);
        host2.getHostedEngine().setLocalMaintenance(false);

        VM vm1 = optimizer.createVm("VM-A", 10000000L);
        vm1.setHost(host1);
        vm1.setStatus(new Status());
        vm1.getStatus().setState("UP");
        vm1.setOrigin("hostedEngine");

        HardSoftScore result = optimizer
                .enablePolicyUnit(PolicyUnit.HOSTED_ENGINE_FILTER)
                .enablePolicyUnit(PolicyUnit.HOSTED_ENGINE_WEIGHT)
                .addMigration(vm1, host2)
                .score();

        assertEquals(0, result.getHardScore());
        // -1 for a single performed migration
        assertEquals(-1, result.getSoftScore());
    }

    /**
     * Test HE rules for NPEs.
     */
    @Test
    public void testHERulesWithoutHE() {
        TestOptimizer optimizer = new TestOptimizer(EnumSet.noneOf(ClusterFeatures.class));

        Host host1 = optimizer.createHost("H1", 1000000000L);
        Host host2 = optimizer.createHost("H2", 1000000000L);

        VM vm1 = optimizer.createVm("VM-A", 10000000L);
        vm1.setHost(host1);
        vm1.setStatus(new Status());
        vm1.getStatus().setState("UP");
        vm1.setOrigin("hostedEngine");

        HardSoftScore result = optimizer
                .enablePolicyUnit(PolicyUnit.HOSTED_ENGINE_FILTER)
                .enablePolicyUnit(PolicyUnit.HOSTED_ENGINE_WEIGHT)
                .addMigration(vm1, host2)
                .score();

        // destination host is not HE enabled
        assertNotEquals(0, result.getHardScore());
        // -1 for a single performed migration
        assertEquals(-1, result.getSoftScore());
    }

    /**
     * Test whether VM can be run on host with enough cores and
     * cannot be run on host with not enough cores
     */
    @Test
    public void testNotEnoughCores(){
        TestOptimizer optimizer = new TestOptimizer();

        Host host = optimizer.createHost("H1", 1000000000L);
        host.getCpu().getTopology().setSockets(1);
        host.getCpu().getTopology().setCores(2);

        VM vm = optimizer.createVm("VM-A", 10000000L);
        vm.getCpu().getTopology().setSockets(1);
        vm.getCpu().getTopology().setCores(2);

        optimizer.enablePolicyUnit(PolicyUnit.CPU_FILTER)
                .startVm(vm)
                .addMigration(vm,host);

        HardSoftScore r1 = optimizer.score();

        vm.getCpu().getTopology().setCores(4);
        HardSoftScore r2 = optimizer.score();

        assertEquals(0, r1.getHardScore());
        assertNotEquals(0, r2.getHardScore());
    }
}

