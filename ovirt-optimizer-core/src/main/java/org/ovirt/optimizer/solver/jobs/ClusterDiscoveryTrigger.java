package org.ovirt.optimizer.solver.jobs;


import org.ovirt.optimizer.solver.OptimizerServiceBean;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.inject.Inject;

@DisallowConcurrentExecution
public class ClusterDiscoveryTrigger implements Job {
    @Inject
    OptimizerServiceBean optimizer;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        optimizer.discoveryTimeout(context.getJobDetail());
    }
}
