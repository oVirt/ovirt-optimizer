package org.ovirt.optimizer.service;

import org.optaplanner.core.api.solver.Solver;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.service.problemspace.ClusterFactChange;

import java.util.Set;

class ClusterUpdateAvailableForOptimizer implements ClusterInfoUpdater.ClusterUpdateAvailable {
    final String clusterId;
    final Solver solver;

    ClusterUpdateAvailableForOptimizer(String clusterId, Solver solver) {
        this.clusterId = clusterId;
        this.solver = solver;
    }

    @Override
    public void checkUpdate(final Set<VM> vms, final Set<Host> hosts, final Set<Object> facts) {
        solver.addProblemFactChange(new ClusterFactChange(clusterId, vms, hosts, facts));
    }
}
