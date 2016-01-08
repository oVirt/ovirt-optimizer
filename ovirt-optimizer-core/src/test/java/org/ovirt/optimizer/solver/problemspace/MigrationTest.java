package org.ovirt.optimizer.solver.problemspace;

import org.junit.Test;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.cdi.LoggerFactory;
import org.ovirt.optimizer.solver.facts.Instance;

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
        assertEquals("host1", m1.getAssignment(1L));
        assertEquals("host2", m1.getAssignment(2L));
        assertEquals("host1", m1.getAssignment(3L));
        assertEquals("host2", m1.getAssignment(4L));
        assertEquals(2, m1.getHostToInstanceAssignments().get("host1").size());
        assertEquals(2, m1.getHostToInstanceAssignments().get("host2").size());
    }

    @Test
    public void testRecomputeHostMissing() {
        ClusterSituation start = prepareDummyCluster();
        Migration m1 = new Migration();

        Host h1 = new Host();
        h1.setId("host1");

        Instance vm1 = new Instance("vm-1");
        vm1.setId(1L);

        m1.setInstance(vm1);
        m1.recomputeSituationAfter(start);
        assertEquals("host1", m1.getAssignment(1L));
        assertEquals("host2", m1.getAssignment(2L));
        assertEquals("host1", m1.getAssignment(3L));
        assertEquals("host2", m1.getAssignment(4L));
        assertEquals(false, m1.getHostToInstanceAssignments().containsKey(null));
        assertEquals(2, m1.getHostToInstanceAssignments().get("host1").size());
        assertEquals(2, m1.getHostToInstanceAssignments().get("host2").size());
    }

    @Test
    public void testRecomputeVmMissing() {
        ClusterSituation start = prepareDummyCluster();
        Migration m1 = new Migration();

        Host h2 = new Host();
        h2.setId("host2");

        m1.setDestination(h2);
        m1.recomputeSituationAfter(start);
        assertEquals("host1", m1.getAssignment(1L));
        assertEquals("host2", m1.getAssignment(2L));
        assertEquals("host1", m1.getAssignment(3L));
        assertEquals("host2", m1.getAssignment(4L));
        assertEquals(false, m1.getInstanceToHostAssignments().containsKey(null));
        assertEquals(2, m1.getHostToInstanceAssignments().get("host1").size());
        assertEquals(2, m1.getHostToInstanceAssignments().get("host2").size());
    }

    @Test
    public void testRecomputeChange() {
        ClusterSituation start = prepareDummyCluster();
        Migration m1 = new Migration();

        Host h1 = new Host();
        h1.setId("host1");

        Host h2 = new Host();
        h2.setId("host2");

        Instance vm1 = new Instance("vm-1");
        vm1.setId(1L);

        m1.setInstance(vm1);
        m1.setDestination(h2);
        m1.recomputeSituationAfter(start);

        assertEquals("host2", m1.getAssignment(1L));
        assertEquals("host2", m1.getAssignment(2L));
        assertEquals("host1", m1.getAssignment(3L));
        assertEquals("host2", m1.getAssignment(4L));
        assertEquals(1, m1.getHostToInstanceAssignments().get("host1").size());
        assertEquals(3, m1.getHostToInstanceAssignments().get("host2").size());
    }

    private ClusterSituation prepareDummyCluster() {
        return new ClusterSituation() {
            @Override
            public Map<Long, String> getInstanceToHostAssignments() {
                Map<Long, String> s = new HashMap<Long, String>();
                s.put(1L, "host1");
                s.put(2L, "host2");
                s.put(3L, "host1");
                s.put(4L, "host2");
                return s;
            }

            @Override
            public Map<String, Set<Long>> getHostToInstanceAssignments() {
                Map<String, Set<Long>> s = new HashMap<>();
                s.put("host1", new HashSet<Long>());
                s.put("host2", new HashSet<Long>());

                for (Map.Entry<Long, String> item: getInstanceToHostAssignments().entrySet()) {
                    s.get(item.getValue()).add(item.getKey());
                }

                return s;
            }
        };
    }
}
