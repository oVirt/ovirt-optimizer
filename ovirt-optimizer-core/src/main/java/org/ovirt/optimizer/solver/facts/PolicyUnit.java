package org.ovirt.optimizer.solver.facts;

import java.util.UUID;

public enum PolicyUnit {
    PIN_TO_HOST_FILTER("12262ab6-9690-4bc3-a2b3-35573b172d54"),
    MEMORY_FILTER("c9ddbb34-0e1d-4061-a8d7-b0893fa80932"),
    HOSTED_ENGINE_FILTER("e659c871-0bf1-4ccc-b748-f28f5d08dffd"),
    HOSTED_ENGINE_WEIGHT("98e92667-6161-41fb-b3fa-34f820ccbc4b"),
    CPU_FILTER("6d636bf6-a35c-4f9d-b68d-0731f720cddc"),
    NETWORK_FILTER("72163d1c-9468-4480-99d9-0888664eb143"),

    VM_AFFINITY_FILTER("84e6ddee-ab0d-42dd-82f0-c297779db566"),
    VM_AFFINITY_WEIGHT("84e6ddee-ab0d-42dd-82f0-c297779db567"),

    EVEN_DIST_BALANCE("7db4ab05-81ab-42e8-868a-aee2df483ed2"),
    EVEN_DIST_WEIGHT("7db4ab05-81ab-42e8-868a-aee2df483edb"),
    EVEN_DIST_WEIGHT_CPU("7db4ab05-81ab-42e8-868a-aee2df483edb"),
    EVEN_DIST_WEIGHT_MEMORY("4134247a-9c58-4b9a-8593-530bb9e37c59"),

    NONE_BALANCE("38440000-8cf0-14bd-c43e-10b96e4ef00a"),
    NONE_WEIGHT("38440000-8cf0-14bd-c43e-10b96e4ef00b"),


    POWER_SAVING_BALANCE("736999d0-1023-46a4-9a75-1316ed50e151"),
    POWER_SAVING_WEIGHT("736999d0-1023-46a4-9a75-1316ed50e15b"),
    POWER_SAVING_WEIGHT_CPU("736999d0-1023-46a4-9a75-1316ed50e15b"),
    POWER_SAVING_WEIGHT_MEMORY("9dfe6086-646d-43b8-8eef-4d94de8472c8"),

    EVEN_GUEST_DIST_BALANCE("d58c8e32-44e1-418f-9222-52cd887bf9e0"),
    EVEN_GUEST_DIST_WEIGHT("3ba8c988-f779-42c0-90ce-caa8243edee7"),

    AFFINITY_LABEL_FILTER("27846536-f653-11e5-9ce9-5e5517507c66");

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
