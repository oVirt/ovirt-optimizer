package org.ovirt.optimizer.solver.problemspace;

import java.util.Map;
import java.util.Set;

public interface ClusterSituation {
    Map<String, String> getVmToHostAssignments();
    Map<String, Set<String>> getHostToVmAssignments();
}
