package org.ovirt.optimizer.solver.facts;

public enum PolicyProperty {
    CPU_OVERCOMMIT_MINUTES("CpuOverCommitDurationMinutes"),
    HIGH_UTIL("HighUtilization"),
    LOW_UTIL("LowUtilization"),
    MAX_FREE_MEM_OVER_UTIL("MaxFreeMemoryForOverUtilized"),
    MIN_FREE_MEM_UNDER_UTIL("MinFreeMemoryForUnderUtilized"),
    HIGH_VM_COUNT("HighVmCount"),
    MIGRATION_THRESHOLD("MigrationThreshold"),
    SPM_VM_GRACE("SpmVmGrace"),
    HOSTS_IN_RESERVE("HostsInReserve");


    private final String name;

    PolicyProperty(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
