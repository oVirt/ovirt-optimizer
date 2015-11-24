package org.ovirt.optimizer.service.facts;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.ovirt.engine.sdk.entities.Statistic;
import java.math.BigDecimal;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY,
        getterVisibility=JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=JsonAutoDetect.Visibility.NONE)
public class HostStats {
    private static final String MEMORY_TOTAL = "memory.total";
    private static final String MEMORY_USED = "memory.used";
    private static final String MEMORY_FREE = "memory.free";
    private static final String MEMORY_SHARED = "memory.shared";
    private static final String MEMORY_BUFFERS = "memory.buffers";
    private static final String MEMORY_CHACHED = "memory.cached";
    private static final String SWAP_TOTAL = "swap.total";
    private static final String SWAP_FREE = "swap.free";
    private static final String SWAP_USED = "swap.used";
    private static final String SWAP_CACHED = "swap.cached";
    private static final String KSM_CPU_CURRENT = "ksm.cpu.current";
    private static final String CPU_CURRENT_USER= "cpu.current.user";
    private static final String CPU_CURRENT_SYSTEM= "cpu.current.system";
    private static final String CPU_CURRENT_IDLE= "cpu.current.idle";
    private static final String CPU_LOAD_AVG= "cpu.load.avg.5m";
    private static final String BOOT_TIME= "boot.time";

    private String id;
    private Long memTotal;
    private Long memUsed;
    private Long memFree;
    private Long memShared;
    private Long ioBuffers;
    private Long osCaches;
    private Long swapTotal;
    private Long swapFree;
    private Long swapUsed;
    private Long swapChached;
    private Float ksmCpuCurrent;
    private Float cpuCurrentUser;
    private Float cpuCurrentSystem;
    private Float cpuCurrentIdle;
    private Float cpuCurrentAvg;
    private Long bootTime;

    protected HostStats() {
    }

    public HostStats(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getSwapUsed() {
        return swapUsed;
    }

    public void setSwapUsed(Long swapUsed) {
        this.swapUsed = swapUsed;
    }

    public Long getBootTime() {
        return bootTime;
    }

    public void setBootTime(Long bootTime) {
        this.bootTime = bootTime;
    }

    public Float getCpuCurrentAvg() {
        return cpuCurrentAvg;
    }

    public void setCpuCurrentAvg(Float cpuCurrentAvg) {
        this.cpuCurrentAvg = cpuCurrentAvg;
    }

    public Float getCpuCurrentIdle() {
        return cpuCurrentIdle;
    }

    public void setCpuCurrentIdle(Float cpuCurrentIdle) {
        this.cpuCurrentIdle = cpuCurrentIdle;
    }

    public Float getCpuCurrentSystem() {
        return cpuCurrentSystem;
    }

    public void setCpuCurrentSystem(Float cpuCurrentSystem) {
        this.cpuCurrentSystem = cpuCurrentSystem;
    }

    public Float getCpuCurrentUser() {
        return cpuCurrentUser;
    }

    public void setCpuCurrentUser(Float cpuCurrentUser) {
        this.cpuCurrentUser = cpuCurrentUser;
    }

    public Long getIoBuffers() {
        return ioBuffers;
    }

    public void setIoBuffers(Long ioBuffers) {
        this.ioBuffers = ioBuffers;
    }

    public Float getKsmCpuCurrent() {
        return ksmCpuCurrent;
    }

    public void setKsmCpuCurrent(Float ksmCpuCurrent) {
        this.ksmCpuCurrent = ksmCpuCurrent;
    }

    public Long getMemFree() {
        return memFree;
    }

    public void setMemFree(Long memFree) {
        this.memFree = memFree;
    }

    public Long getMemShared() {
        return memShared;
    }

    public void setMemShared(Long memShared) {
        this.memShared = memShared;
    }

    public Long getMemTotal() {
        return memTotal;
    }

    public void setMemTotal(Long memTotal) {
        this.memTotal = memTotal;
    }

    public Long getMemUsed() {
        return memUsed;
    }

    public void setMemUsed(Long memUsed) {
        this.memUsed = memUsed;
    }

    public Long getOsCaches() {
        return osCaches;
    }

    public void setOsCaches(Long osCaches) {
        this.osCaches = osCaches;
    }

    public Long getSwapChached() {
        return swapChached;
    }

    public void setSwapChached(Long swapChached) {
        this.swapChached = swapChached;
    }

    public Long getSwapFree() {
        return swapFree;
    }

    public void setSwapFree(Long swapFree) {
        this.swapFree = swapFree;
    }

    public Long getSwapTotal() {
        return swapTotal;
    }

    public void setSwapTotal(Long swapTotal) {
        this.swapTotal = swapTotal;
    }


    public void loadValue(Statistic stat) {
        if (stat.getValues() == null || stat.getValues().getValues() == null) { return; }
        BigDecimal val = stat.getValues().getValues().get(0).getDatum();

        // Stores the value to an attribute based on the name of the statistic
        if (MEMORY_TOTAL.equals( stat.getName() )){
            setMemTotal( val.longValue() );
        } else if (MEMORY_USED.equals( stat.getName() )){
            setMemUsed( val.longValue() );
        } else if (MEMORY_FREE.equals( stat.getName() )){
            setMemFree( val.longValue() );
        } else if (MEMORY_SHARED.equals( stat.getName() )){
            setMemShared( val.longValue() );
        } else if (MEMORY_BUFFERS.equals( stat.getName() )){
            setIoBuffers( val.longValue() );
        } else if (MEMORY_CHACHED.equals( stat.getName() )){
            setOsCaches( val.longValue() );
        } else if (SWAP_TOTAL.equals( stat.getName() )){
            setSwapTotal( val.longValue() );
        } else if (SWAP_USED.equals( stat.getName() )){
            setSwapUsed( val.longValue() );
        } else if (SWAP_FREE.equals( stat.getName() )){
            setSwapFree( val.longValue() );
        } else if (SWAP_CACHED.equals( stat.getName() )){
            setSwapChached( val.longValue() );
        } else if (KSM_CPU_CURRENT.equals( stat.getName() )){
            setKsmCpuCurrent( val.floatValue() );
        } else if (CPU_CURRENT_USER.equals( stat.getName() )){
            setCpuCurrentUser( val.floatValue() );
        } else if (CPU_CURRENT_SYSTEM.equals( stat.getName() )){
            setCpuCurrentSystem( val.floatValue() );
        } else if (CPU_CURRENT_IDLE.equals( stat.getName() )){
            setCpuCurrentIdle( val.floatValue() );
        } else if (CPU_LOAD_AVG.equals( stat.getName() )){
            setCpuCurrentAvg( val.floatValue() );
        } else if (BOOT_TIME.equals( stat.getName() )){
            setBootTime( val.longValue() );
        }
    }
}
