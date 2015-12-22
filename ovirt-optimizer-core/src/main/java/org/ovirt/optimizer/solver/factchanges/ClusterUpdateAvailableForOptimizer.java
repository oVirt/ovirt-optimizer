package org.ovirt.optimizer.solver.factchanges;

import org.optaplanner.core.api.solver.Solver;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;

import java.util.Set;

public class ClusterUpdateAvailableForOptimizer implements ClusterUpdateAvailable {
    final String clusterId;
    final Solver solver;

    public ClusterUpdateAvailableForOptimizer(String clusterId, Solver solver) {
        this.clusterId = clusterId;
        this.solver = solver;
    }

    @Override
    public void checkUpdate(final Set<VM> vms, final Set<Host> hosts, final Set<Object> facts) {
        solver.addProblemFactChange(new ClusterFactChange(clusterId, vms, hosts, facts));
    }
}
