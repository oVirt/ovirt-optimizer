package org.ovirt.optimizer.solver.facts;

import org.ovirt.engine.sdk.entities.VM;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This fact object represents one space reservation.
 * One primary reservation has to be created for each VM.
 * Additional non-primary reservations might be created for
 * the purpose of reserving backup space for HA VMs.
 */
public class Instance {
    private static AtomicLong nextId = new AtomicLong(0);

    private Instance() {
    }

    public Instance(String vmId) {
        this.primary = true;
        this.vmId = vmId;
        this.id = Instance.getNewId();
    }

    public Instance(VM vm) {
        this(vm.getId());
    }

    /**
     * ID of the instance, MUST be unique
     */
    Long id;

    /**
     * ID of the VM this instance represents
     */
    String vmId;

    /**
     * Primary instance represents a real VM, secondary (false) instance
     * represents a space reservation for HA Reservation purposes.
     */
    Boolean primary;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVmId() {
        return vmId;
    }

    public void setVmId(String vmId) {
        this.vmId = vmId;
    }

    public Boolean getPrimary() {
        return primary;
    }

    public void setPrimary(Boolean primary) {
        this.primary = primary;
    }

    public static long getNewId() {
        return nextId.incrementAndGet();
    }
}
