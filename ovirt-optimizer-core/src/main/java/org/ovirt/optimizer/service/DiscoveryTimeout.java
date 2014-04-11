package org.ovirt.optimizer.service;


import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.inject.Inject;

@DisallowConcurrentExecution
public class DiscoveryTimeout implements Job {
    @Inject
    OptimizerServiceBean optimizer;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        optimizer.discoveryTimeout(context.getJobDetail());
    }
}