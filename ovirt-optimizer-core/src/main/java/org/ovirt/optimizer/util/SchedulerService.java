package org.ovirt.optimizer.util;

import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import javax.annotation.ManagedBean;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

@Autoload
@ManagedBean
@ApplicationScoped
public class SchedulerService {
    @Inject
    SchedulerJobFactory jobFactory;

    @Inject
    Logger log;

    public class Timer {
        Trigger trigger;
        JobDetail jobDetail;

        Timer(int seconds, final Class<? extends Job> job) {
            jobDetail = newJob(job)
                    .build();

            trigger = newTrigger()
                    .startNow()
                    .withSchedule(simpleSchedule()
                                  .withIntervalInSeconds(seconds)
                                  .repeatForever())
                    .forJob(jobDetail)
                    .build();

            try {
                scheduler.scheduleJob(jobDetail, trigger);
            } catch (SchedulerException e) {

            }
        }

        public void cancel() {
            try {
                scheduler.deleteJob(jobDetail.getKey());
            } catch (SchedulerException ex) {

            }
        }

        public JobDetail getJobDetail() {
            return jobDetail;
        }
    }

    public Timer createTimer(int seconds, Class<? extends Job> job) {
        return new Timer(seconds, job);
    }

    final SchedulerFactory schedFact;
    Scheduler scheduler;

    SchedulerService() {
        schedFact = new StdSchedulerFactory();
        try {
            scheduler = schedFact.getScheduler();
        } catch (SchedulerException e) {

        }
    }

    @PostConstruct
    void start() {
        try {
            scheduler.setJobFactory(jobFactory);
            scheduler.start();
            log.info("Quartz scheduler started");
        } catch (SchedulerException e) {
            log.error(e);
        }
    }

    @PreDestroy
    void stop() {
        try {
            scheduler.shutdown(true);
            log.info("Quartz scheduler stopped");
        } catch (SchedulerException e) {
            log.error(e);
        }
    }
}
