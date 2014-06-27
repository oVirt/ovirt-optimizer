package org.ovirt.optimizer.service.facts;

/**
 * This fact represents a request that ensures a Vm with certain
 * UUID is running in the result.
 */
public class RunningVm {
    private String id;

    public RunningVm(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
