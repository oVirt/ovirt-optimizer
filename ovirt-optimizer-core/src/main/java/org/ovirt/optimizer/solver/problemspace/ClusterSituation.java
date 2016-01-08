package org.ovirt.optimizer.solver.problemspace;

import java.util.Map;
import java.util.Set;

public interface ClusterSituation {
    Map<Long, String> getInstanceToHostAssignments();
    Map<String, Set<Long>> getHostToInstanceAssignments();
}
