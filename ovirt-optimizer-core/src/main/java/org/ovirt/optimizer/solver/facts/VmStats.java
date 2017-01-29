package org.ovirt.optimizer.solver.facts;


import java.math.BigDecimal;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.ovirt.engine.sdk.entities.Statistic;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY,
        getterVisibility=JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=JsonAutoDetect.Visibility.NONE)
public class VmStats {
    private static final String MEM_INSTALLED = "memory.installed";
    private static final String MEM_USED = "memory.used";
    private static final String MEM_FREE = "memory.free";
    private static final String MEM_BUFFERED = "memory.buffered";
    private static final String MEM_CACHED = "memory.cached";
    private static final String CPU_CURREMT_GUEST = "cpu.current.guest";
    private static final String CPU_CURRENT_HYPERVISOR = "cpu.current.hypervisor";
    private static final String CPU_CURRENT_TOTAL = "cpu.current.total";
    private static final String MIGRATION_PROGRESS = "migration.progress";

    private String id;
    private Long memInstalled;
    private Long memUsed;
    private Long memFree;
    private Long memBuffered;
    private Long memCached;
    private Float cpuCurrentGuest;
    private Float cpuCurrentHypervisor;
    private Float cpuCurrentTotal;
    private Float migrationProgress;

    protected VmStats() {
    }

    public VmStats(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getMemInstalled() {
        return memInstalled;
    }

    public void setMemInstalled(Long memInstalled) {
        this.memInstalled = memInstalled;
    }

    public Long getMemUsed() {
        return memUsed;
    }

    public void setMemUsed(Long memUsed) {
        this.memUsed = memUsed;
    }

    public Long getMemBuffered() {
        return memBuffered;
    }

    public void setMemBuffered(Long memBuffered) {
        this.memBuffered = memBuffered;
    }

    public Long getMemCached() {
        return memCached;
    }

    public void setMemCached(Long memCached) {
        this.memCached = memCached;
    }

    public Long getMemFree() {
        return memFree;
    }

    public void setMemFree(Long memFree) {
        this.memFree = memFree;
    }

    public Float getCpuCurrentGuest() {
        return cpuCurrentGuest;
    }

    public void setCpuCurrentGuest(Float cpuCurrentGuest) {
        this.cpuCurrentGuest = cpuCurrentGuest;
    }

    public Float getCpuCurrentHypervisor() {
        return cpuCurrentHypervisor;
    }

    public void setCpuCurrentHypervisor(Float cpuCurrentHypervisor) {
        this.cpuCurrentHypervisor = cpuCurrentHypervisor;
    }

    public Float getCpuCurrentTotal() {
        return cpuCurrentTotal;
    }

    public void setCpuCurrentTotal(Float cpuCurrentTotal) {
        this.cpuCurrentTotal = cpuCurrentTotal;
    }

    public Float getMigrationProgress() {
        return migrationProgress;
    }

    public void setMigrationProgress(Float migrationProgress) {
        this.migrationProgress = migrationProgress;
    }


    public void loadValue(Statistic stat) {
        if (stat.getValues() == null || stat.getValues().getValues() == null) { return; }
        BigDecimal val = stat.getValues().getValues().get(0).getDatum();

        // Stores the value to an attribute based on the name of the statistic
        if (MEM_INSTALLED.equals(stat.getName())) {
            setMemInstalled( val.longValue() );
        } else if (MEM_USED.equals(stat.getName())) {
            setMemUsed(val.longValue());
        } else if (MEM_FREE.equals(stat.getName())) {
            setMemFree(val.longValue());
        } else if (MEM_BUFFERED.equals(stat.getName())) {
            setMemBuffered(val.longValue());
        } else if (MEM_CACHED.equals(stat.getName())) {
            setMemCached(val.longValue());
        } else if (CPU_CURREMT_GUEST.equals(stat.getName())) {
            setCpuCurrentGuest( val.floatValue() );
        } else if (CPU_CURRENT_HYPERVISOR.equals(stat.getName())) {
            setCpuCurrentHypervisor( val.floatValue() );
        } else if (CPU_CURRENT_TOTAL.equals(stat.getName())) {
            setCpuCurrentTotal( val.floatValue() );
        } else if (MIGRATION_PROGRESS.equals(stat.getName())) {
            setMigrationProgress( val.floatValue() );
        }
    }

}
