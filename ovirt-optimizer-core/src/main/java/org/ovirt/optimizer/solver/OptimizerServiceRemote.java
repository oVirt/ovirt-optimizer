package org.ovirt.optimizer.solver;

import org.ovirt.optimizer.rest.dto.DebugSnapshot;
import org.ovirt.optimizer.rest.dto.Result;
import org.ovirt.optimizer.rest.dto.ScoreResult;
import org.ovirt.optimizer.solver.problemspace.OptimalDistributionStepsSolution;

import java.util.List;
import java.util.Map;

public interface OptimizerServiceRemote {
    Map<String, DebugSnapshot> getDebugSnapshot();
    Result getCurrentResult(String cluster);
    ScoreResult recomputeScore(String cluster, Result oldResult);
    ScoreResult recomputeScore(OptimalDistributionStepsSolution situation, Result result);
    Map<String, ScoreResult> simpleSchedule(String clusterId, OptimalDistributionStepsSolution situation, Result baseResult, String vm);
    void computeVmStart(String cluster, String uuid);
    void cancelVmStart(String cluster, String uuid);
}
