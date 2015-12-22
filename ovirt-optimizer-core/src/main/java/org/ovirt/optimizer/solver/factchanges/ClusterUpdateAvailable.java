package org.ovirt.optimizer.solver.factchanges;

import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;

import java.util.Set;

public interface ClusterUpdateAvailable {
    void checkUpdate(Set<VM> vms, Set<Host> hosts, Set<Object> newFacts);
}
