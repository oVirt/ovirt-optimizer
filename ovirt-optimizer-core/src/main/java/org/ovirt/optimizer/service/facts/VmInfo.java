package org.ovirt.optimizer.service.facts;

import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.service.InfoUtils;

public class VmInfo {

    private String id;
    private Integer totalCores;

    public VmInfo(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getTotalCores() {
        return totalCores;
    }

    public void setTotalCores(Integer totalCores) {
        this.totalCores = totalCores;
    }

    public static VmInfo createFromVm( VM vm , boolean threadsAsCores){
        VmInfo info = new VmInfo(vm.getId());
        info.setTotalCores(InfoUtils.coresFromTopology(vm.getCpu().getTopology(), threadsAsCores));
        return info;
    }
}
