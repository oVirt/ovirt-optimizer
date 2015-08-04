package org.ovirt.optimizer.service.problemspace;

import org.junit.Test;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.Property;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.service.facts.*;
import org.ovirt.optimizer.service.test.TestOptimizer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DistributionTest {

    private static final long MB = 1000000L;

    /**
     * Copies CPU load values from an input array into a list of VmStats objects
     */
    private void fillVmCpuUtil( List<VmStats> statList, float[] cpuLoad ){
        for (int i = 0; i < statList.size(); ++i){
            VmStats stat = statList.get(i);
            stat.setCpuCurrentGuest(cpuLoad[i]);
            stat.setCpuCurrentTotal(cpuLoad[i]);
        }
    }

    /**
     * Copies used memory values from an input array into a list of VM objects
     */
    private void fillVmMemoryUtil( List<VM> vms, long[] usedMemory ){
        for (int i = 0; i < vms.size(); ++i){
            vms.get(i).setMemory(usedMemory[i] );
            vms.get(i).getMemoryPolicy().setGuaranteed( usedMemory[i] );
        }
    }

    /**
     * Test if more even cpu distribution gets lower score than an uneven one
     */
    @Test
    public void testCpuEvenDistribution() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.enablePolicyUnit(PolicyUnit.EVEN_DIST_WEIGHT, 1);

        // Create 2 hosts for distribution testing
        Host h1 = optimizer.createHost("h1", 1000L * MB);
        Host h2 = optimizer.createHost("h2", 1000L * MB);

        List<VmStats> statList = new ArrayList<>();
        final int vmCount = 4;

        // Create VMs and add them to facts
        for (int i = 0; i < vmCount; ++i) {
            VM vm = optimizer.createVm("vm" + i, 1L * MB);

            // set VMs to hosts uniformly
            vm.setHost(((i % 2) == 0) ? h1 : h2);

            VmStats stats = new VmStats("vm" + i);
            stats.setMemUsed(1L * MB);
            stats.setMemInstalled(1L * MB);
            stats.setCpuCurrentHypervisor(0.0f);
            statList.add(stats);

            optimizer.addFact(stats)
                    .addMigration(vm, vm.getHost())
                    .startVm(vm);
        }

        // Change cpu load inside vm statistics and measure score
        fillVmCpuUtil(statList, new float[]{20.0f, 40.0f, 30.0f, 40.0f});
        HardSoftScore r1 = optimizer.score();

        fillVmCpuUtil(statList, new float[]{20.0f, 30.0f, 40.0f, 40.0f});
        HardSoftScore r2 = optimizer.score();

        assertTrue( r1.getSoftScore() < r2.getSoftScore() );
    }


    /**
     * Test if more even memory distribution gets lower score than an uneven one
     */
    @Test
    public void testMemoryEvenDistribution() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.enablePolicyUnit(PolicyUnit.EVEN_DIST_WEIGHT, 1);

        Host h1 = optimizer.createHost("h1", 1000L * MB);
        Host h2 = optimizer.createHost("h2", 1000L * MB);

        List<VM> vmList = new ArrayList<>();
        final int vmCount = 4;

        for (int i = 0; i < vmCount; ++i) {
            VM vm = optimizer.createVm("vm" + i, 1L * MB);

            // set VMs to hosts uniformly
            vm.setHost(((i % 2) == 0) ? h1 : h2);

            optimizer.addMigration(vm, vm.getHost())
                    .startVm(vm);

            vmList.add(vm);
        }

        // Change memory requirements of VMs and measure score
        fillVmMemoryUtil(vmList, new long[] { 200L * MB, 400L * MB, 300L * MB, 400L * MB });
        HardSoftScore r1 = optimizer.score();

        fillVmMemoryUtil(vmList, new long[]{ 200L * MB, 300L * MB, 400L * MB, 400L * MB});
        HardSoftScore r2 = optimizer.score();

        assertTrue( r1.getSoftScore() < r2.getSoftScore() );
    }

    /**
     * Test if high cpu utilization is penalised
     */
    @Test
    public void testCpuHighUtilization() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.enablePolicyUnit(PolicyUnit.EVEN_DIST_WEIGHT, 1);

        // Set 50% cpu as overutilized
        Property threshold = new Property();
        threshold.setName(PolicyProperty.HIGH_UTIL.getName());
        threshold.setValue("50");

        Host host = optimizer.createHost("h1", 1000000000L);
        host.getCpu().getTopology().setCores(4);

        VM vm = optimizer.createVm("vm", 1000000L);
        vm.setHost(host);
        vm.getCpu().getTopology().setCores(4);
        VmStats stats = new VmStats("vm");
        stats.setMemUsed(1000000L);
        stats.setMemInstalled(1000000L);
        stats.setCpuCurrentHypervisor(0.0f);

        optimizer.addMigration(vm, host)
                .startVm(vm)
                .addFact(threshold)
                .addFact(stats);

        // Test score with vm running at different cpu loads
        stats.setCpuCurrentGuest(30.0f);
        stats.setCpuCurrentTotal(30.0f);
        HardSoftScore r1 = optimizer.score();

        stats.setCpuCurrentGuest(40.0f);
        stats.setCpuCurrentTotal(40.0f);
        HardSoftScore r2 = optimizer.score();

        stats.setCpuCurrentGuest(60.0f);
        stats.setCpuCurrentTotal(60.0f);
        HardSoftScore r3 = optimizer.score();

        // assert big difference between r2 and r3
        assertTrue( r1.getSoftScore() >= r2.getSoftScore() && (r2.getSoftScore() - r3.getSoftScore()) > 10000 );
    }


    /**
     * Test if high memory utilization is penalised
     */
    @Test
    public void testMemHighUtilization(){
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.enablePolicyUnit(PolicyUnit.EVEN_DIST_WEIGHT, 1);

        // Set less than 100 MB free memory as overutilized
        Property threshold = new Property();
        threshold.setName(PolicyProperty.MAX_FREE_MEM_OVER_UTIL.getName());
        threshold.setValue(Long.toString(100L * MB));

        Host host = optimizer.createHost("h1", 1000L * MB);

        // Create vm with 10 MB memory
        VM vm = optimizer.createVm("vm", 10L * MB);
        vm.setHost(host);

        optimizer.addMigration(vm, host)
                .startVm(vm)
                .addFact(threshold);


        // Test score of vm with different memory requirements
        HardSoftScore r1 = optimizer.score();

        vm.setMemory(100L * MB);
        vm.getMemoryPolicy().setGuaranteed(100L * MB);
        HardSoftScore r2 = optimizer.score();

        vm.setMemory(950L * MB);
        vm.getMemoryPolicy().setGuaranteed(950L * MB);
        HardSoftScore r3 = optimizer.score();

        // assert big difference between r2 and r3
        assertTrue(r1.getSoftScore() >= r2.getSoftScore() && (r2.getSoftScore() - r3.getSoftScore()) > 10000);
    }

    /**
     * Test if running hosts are penalised
     */
    @Test
    public void testRunningHostPenalization(){
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.enablePolicyUnit(PolicyUnit.POWER_SAVING_WEIGHT, 1);

        Host host1 = optimizer.createHost("h1", 1000000000L);
        Host host2 = optimizer.createHost("h2", 1000000000L);

        VM vm1 = optimizer.createVm("vm1", 1000000L);
        vm1.setHost(host1);

        VM vm2 = optimizer.createVm("vm2", 1000000L);
        vm2.setHost(host1);

        optimizer.addMigration(vm1, host1)
                .startVm(vm1)
                .startVm(vm2);

        HardSoftScore r1 = optimizer.score();

        vm2.setHost(host2);
        HardSoftScore r2 = optimizer.score();

        // Both VMs on the same host should have better score than one VM on each
        assertTrue(r1.getSoftScore() > r2.getSoftScore());
    }
}
