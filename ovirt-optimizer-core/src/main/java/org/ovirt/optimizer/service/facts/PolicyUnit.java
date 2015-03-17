package org.ovirt.optimizer.service.facts;

import java.util.UUID;

public enum PolicyUnit {
    BALANCE_VM_COUNT("3ba8c988-f779-42c0-90ce-caa8243edee7"),
    PIN_TO_HOST_FILTER("12262ab6-9690-4bc3-a2b3-35573b172d54"),
    MEMORY_FILTER("c9ddbb34-0e1d-4061-a8d7-b0893fa80932");

    final String uuid;

    PolicyUnit(UUID uuid) {
        this.uuid = uuid.toString();
    }

    PolicyUnit(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }
}
