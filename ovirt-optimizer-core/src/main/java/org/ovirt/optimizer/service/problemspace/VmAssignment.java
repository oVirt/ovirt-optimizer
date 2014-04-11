package org.ovirt.optimizer.service.problemspace;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;

@PlanningEntity
public class VmAssignment {
    private VM vm;
    private Host host;

    public VmAssignment() {
        
    }

    public VmAssignment(VM vm) {
        this.vm = vm;
    }

    public VM getVm() {
        return vm;
    }

    public void setVm(VM vm) {
        this.vm = vm;
    }

    public String getId() {
        return vm.getId();
    }

    @PlanningVariable(valueRangeProviderRefs = {"hosts"})
    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }
}
