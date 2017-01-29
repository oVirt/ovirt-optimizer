package org.ovirt.optimizer.scheduling;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.ovirt.optimizer.cdi.Autoload;

@Autoload
@ApplicationScoped
public class ExecutorServiceProducer {
    private ScheduledExecutorService scheduler;
    private ExecutorService threadPool;

    @PostConstruct
    public void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        threadPool = Executors.newCachedThreadPool();
    }

    @PreDestroy
    public void teardown() {
        scheduler.shutdownNow();
        threadPool.shutdownNow();
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }
}
