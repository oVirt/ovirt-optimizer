package org.ovirt.optimizer.service.problemspace;

import java.util.Map;
import java.util.Set;

public interface ClusterSituation {
    Map<String, String> getVmToHostAssignments();
    Map<String, Set<String>> getHostToVmAssignments();
}
