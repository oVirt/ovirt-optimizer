package org.ovirt.optimizer.service;

import org.ovirt.optimizer.common.Result;
import org.ovirt.optimizer.common.ScoreResult;

public interface OptimizerServiceRemote {
    Result getCurrentResult(String cluster);
    ScoreResult recomputeScore(String cluster, Result oldResult);
    void computeVmStart(String cluster, String uuid);
    void cancelVmStart(String cluster, String uuid);
}
