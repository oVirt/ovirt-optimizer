package org.ovirt.optimizer.service;

import org.ovirt.optimizer.common.Result;

public interface OptimizerServiceRemote {
    Result getCurrentResult(String cluster);
}
