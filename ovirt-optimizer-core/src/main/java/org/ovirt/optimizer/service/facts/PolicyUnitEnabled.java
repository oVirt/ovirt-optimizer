package org.ovirt.optimizer.service.facts;

public class PolicyUnitEnabled {
    final String uuid;
    final int factor;

    public PolicyUnitEnabled(String uuid, int factor) {
        this.uuid = uuid;
        this.factor = factor;
    }

    public String getUuid() {
        return uuid;
    }

    public int getFactor() {
        return factor;
    }
}
