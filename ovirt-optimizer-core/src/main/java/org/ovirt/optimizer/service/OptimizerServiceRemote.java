package org.ovirt.optimizer.service;

import org.ovirt.optimizer.common.DebugSnapshot;
import org.ovirt.optimizer.common.Result;
import org.ovirt.optimizer.common.ScoreResult;
import org.ovirt.optimizer.service.problemspace.OptimalDistributionStepsSolution;

import java.util.List;
import java.util.Map;

public interface OptimizerServiceRemote {
    Map<String, DebugSnapshot> getDebugSnapshot();
    Result getCurrentResult(String cluster);
    ScoreResult recomputeScore(String cluster, Result oldResult);
    ScoreResult recomputeScore(OptimalDistributionStepsSolution situation, Result result);
    Map<String, ScoreResult> simpleSchedule(String clusterId, OptimalDistributionStepsSolution situation, List<Map<String, String>> preSteps, String vm);
    void computeVmStart(String cluster, String uuid);
    void cancelVmStart(String cluster, String uuid);
}
