package org.ovirt.optimizer.solver.facts;

import org.codehaus.jackson.annotate.JsonAutoDetect;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY,
        getterVisibility=JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=JsonAutoDetect.Visibility.NONE)
public class PolicyUnitEnabled {
    final String uuid;
    final int factor;

    protected PolicyUnitEnabled() {
        this(null, 0);
    }

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
