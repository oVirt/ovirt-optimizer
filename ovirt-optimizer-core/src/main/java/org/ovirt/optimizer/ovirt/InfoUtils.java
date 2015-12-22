package org.ovirt.optimizer.ovirt;


import org.ovirt.engine.sdk.entities.CpuTopology;


public class InfoUtils {

    /**
     * Calculates total number of cores from CpuTopology object
     *
     * Multiplies all fields of CpuTopology together.
     * If any field is null, it is considered as 1.
     */
    public static int coresFromTopology(CpuTopology top, boolean threadsAsCores){
        if (top == null) { return 1; }

        int threads = (top.getSockets() != null) ? top.getSockets() : 1;
        threads *= (top.getCores() != null) ? top.getCores() : 1;
        threads *= (threadsAsCores && top.getThreads() != null) ? top.getThreads() : 1;
        return threads;
    }

}
