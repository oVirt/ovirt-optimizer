package org.ovirt.optimizer.util;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import javax.annotation.ManagedBean;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

/* Based on http://vijaykiran.com/2013/01/a-quick-way-to-integrate-seam3-cdi-and-quartz-jobs/ */
@ApplicationScoped
@ManagedBean
public class SchedulerJobFactory implements JobFactory {
    @Inject
    private BeanManager beanManager;

    @Override
    public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
        final JobDetail jobDetail = bundle.getJobDetail();
        final Class<? extends Job> jobClass = jobDetail.getJobClass();

        return getBean(jobClass);
    }

    private Job getBean(Class jobClazz) {
        final BeanManager bm = getBeanManager();
        final Bean<?> bean = bm.getBeans(jobClazz).iterator().next();
        final CreationalContext<?> ctx = bm.createCreationalContext(bean);
        return (Job) bm.getReference(bean, jobClazz, ctx);
    }

    public BeanManager getBeanManager() {
        return beanManager;
    }
}
