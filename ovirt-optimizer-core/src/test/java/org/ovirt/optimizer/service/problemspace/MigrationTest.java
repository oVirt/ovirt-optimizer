package org.ovirt.optimizer.service.problemspace;

import org.junit.Test;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class MigrationTest {
    @Test
    public void testRecomputeNoChange() {
        ClusterSituation start = prepareDummyCluster();
        Migration m1 = new Migration();
        m1.recomputeSituationAfter(start);
        assertEquals("host1", m1.getAssignment("vm1"));
        assertEquals("host2", m1.getAssignment("vm2"));
        assertEquals("host1", m1.getAssignment("vm3"));
        assertEquals("host2", m1.getAssignment("vm4"));
        assertEquals(2, m1.getHostToVmAssignments().get("host1").size());
        assertEquals(2, m1.getHostToVmAssignments().get("host2").size());
    }

    @Test
    public void testRecomputeHostMissing() {
        ClusterSituation start = prepareDummyCluster();
        Migration m1 = new Migration();

        Host h1 = new Host();
        h1.setId("host1");

        VM vm1 = new VM();
        vm1.setId("vm1");
        vm1.setHost(h1);

        m1.setVm(vm1);
        m1.recomputeSituationAfter(start);
        assertEquals("host1", m1.getAssignment("vm1"));
        assertEquals("host2", m1.getAssignment("vm2"));
        assertEquals("host1", m1.getAssignment("vm3"));
        assertEquals("host2", m1.getAssignment("vm4"));
        assertEquals(false, m1.getHostToVmAssignments().containsKey(null));
        assertEquals(2, m1.getHostToVmAssignments().get("host1").size());
        assertEquals(2, m1.getHostToVmAssignments().get("host2").size());
    }

    @Test
    public void testRecomputeVmMissing() {
        ClusterSituation start = prepareDummyCluster();
        Migration m1 = new Migration();

        Host h2 = new Host();
        h2.setId("host2");

        m1.setDestination(h2);
        m1.recomputeSituationAfter(start);
        assertEquals("host1", m1.getAssignment("vm1"));
        assertEquals("host2", m1.getAssignment("vm2"));
        assertEquals("host1", m1.getAssignment("vm3"));
        assertEquals("host2", m1.getAssignment("vm4"));
        assertEquals(false, m1.getVmToHostAssignments().containsKey(null));
        assertEquals(2, m1.getHostToVmAssignments().get("host1").size());
        assertEquals(2, m1.getHostToVmAssignments().get("host2").size());
    }

    @Test
    public void testRecomputeChange() {
        ClusterSituation start = prepareDummyCluster();
        Migration m1 = new Migration();

        Host h1 = new Host();
        h1.setId("host1");

        Host h2 = new Host();
        h2.setId("host2");

        VM vm1 = new VM();
        vm1.setId("vm1");
        vm1.setHost(h1);

        m1.setVm(vm1);
        m1.setDestination(h2);
        m1.recomputeSituationAfter(start);

        assertEquals("host2", m1.getAssignment("vm1"));
        assertEquals("host2", m1.getAssignment("vm2"));
        assertEquals("host1", m1.getAssignment("vm3"));
        assertEquals("host2", m1.getAssignment("vm4"));
        assertEquals(1, m1.getHostToVmAssignments().get("host1").size());
        assertEquals(3, m1.getHostToVmAssignments().get("host2").size());
    }

    private ClusterSituation prepareDummyCluster() {
        return new ClusterSituation() {
            @Override
            public Map<String, String> getVmToHostAssignments() {
                Map<String, String> s = new HashMap<String, String>();
                s.put("vm1", "host1");
                s.put("vm2", "host2");
                s.put("vm3", "host1");
                s.put("vm4", "host2");
                return s;
            }

            @Override
            public Map<String, Set<String>> getHostToVmAssignments() {
                Map<String, Set<String>> s = new HashMap<>();
                s.put("host1", new HashSet<String>());
                s.put("host2", new HashSet<String>());

                for (Map.Entry<String, String> item: getVmToHostAssignments().entrySet()) {
                    s.get(item.getValue()).add(item.getKey());
                }

                return s;
            }
        };
    }
}
