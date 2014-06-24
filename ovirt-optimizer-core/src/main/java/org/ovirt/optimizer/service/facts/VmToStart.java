package org.ovirt.optimizer.service.facts;

/**
 * This fact represents a request that ensures a Vm with certain
 * UUID is running in the result.
 */
public class VmToStart {
    private String uuid;

    public VmToStart(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
