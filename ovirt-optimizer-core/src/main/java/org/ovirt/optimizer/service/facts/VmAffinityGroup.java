package org.ovirt.optimizer.service.facts;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.ovirt.engine.sdk.entities.AffinityGroup;
import org.ovirt.engine.sdk.entities.VM;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY,
        getterVisibility=JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=JsonAutoDetect.Visibility.NONE)
public class VmAffinityGroup {

    private String id;
    private boolean positive;
    private boolean enforcing;
    private Set<String> vmIds;

    protected VmAffinityGroup() {
    }

    public VmAffinityGroup(String id) {
        this.id = id;
    }

    public boolean isEnforcing() {
        return enforcing;
    }

    public void setEnforcing(boolean enforcing) {
        this.enforcing = enforcing;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isPositive() {
        return positive;
    }

    public void setPositive(boolean positive) {
        this.positive = positive;
    }

    public Set<String> getVmIds() {
        return vmIds;
    }

    public void setVmIds(Set<String> vmIds) {
        this.vmIds = vmIds;
    }

    public static VmAffinityGroup create( AffinityGroup group, List<? extends VM> vms){
        VmAffinityGroup vmGroup = new VmAffinityGroup(group.getId());
        vmGroup.setEnforcing(group.isEnforcing());
        vmGroup.setPositive(group.isPositive());

        Set<String> ids = new HashSet<>();
        for( VM vm : vms ){
            ids.add(vm.getId());
        }

        vmGroup.setVmIds(ids);

        return vmGroup;
    }
}
