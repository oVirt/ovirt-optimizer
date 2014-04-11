package org.ovirt.optimizer.service;

import org.apache.log4j.Logger;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.optimizer.common.Result;
import org.ovirt.optimizer.service.problemspace.OptimalDistributionSolution;
import org.ovirt.optimizer.service.problemspace.VmAssignment;
import org.ovirt.optimizer.util.Autoload;
import org.ovirt.optimizer.util.SchedulerService;
import org.quartz.JobDetail;

import javax.annotation.ManagedBean;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * This is the main service class for computing the optimized
 * Vm to host assignments.
 */
@Autoload
@ApplicationScoped
@ManagedBean
public class OptimizerServiceBean implements OptimizerServiceRemote {
    @Inject
    private Logger log;

    @Inject
    SchedulerService scheduler;

    @Inject
    ClusterDiscovery discovery;

    @Inject
    OvirtClient client;

    SchedulerService.Timer discoveryTimer;
    Set<Thread> threads;

    // This attribute is used by the exported API and has to
    // be used in thread safe way
    final Map<String, ClusterOptimizer> clusterOptimizers = new HashMap<>();

    @PostConstruct
    public void create() {
        log.info("oVirt optimizer service starting");
        threads = new HashSet<>();
        discoveryTimer = scheduler.createTimer(120, DiscoveryTimeout.class);
    }

    // Synchronized should not be needed, but is here as a
    // safeguard for prevention from threading mistakes
    public synchronized void discoveryTimeout(final JobDetail timer){
        // Check for possible spurious timeouts from old instances
        if (timer.getKey() != discoveryTimer.getJobDetail().getKey()) {
            log.warn(String.format("Unknown timeout from %s", timer.toString()));
            return;
        }

        log.info("Discovering clusters...");
        Set<String> availableClusters = discovery.getClusters();
        if (availableClusters == null) {
            log.error("Cluster discovery failed");
            return;
        }

        Set<String> missingClusters;

        synchronized (clusterOptimizers) {
            /* Compute a set of clusters that disappeared */
            missingClusters = new HashSet<>(clusterOptimizers.keySet());
            missingClusters.removeAll(availableClusters);

            /* Compute a set of new clusters */
            availableClusters.removeAll(clusterOptimizers.keySet());
        }

        for (String clusterId: availableClusters) {
            log.info(String.format("New cluster %s detected", clusterId));

            ClusterOptimizer planner = new ClusterOptimizer(client, clusterId, new ClusterOptimizer.Finished() {
                @Override
                public void solvingFinished(ClusterOptimizer planner, Thread thread) {
                    threads.remove(thread);
                }
            });

            Thread updater = new Thread(planner.getUpdaterInstance());
            Thread solver = new Thread(planner);

            updater.start();
            threads.add(updater);

            solver.start();
            threads.add(solver);

            synchronized (clusterOptimizers) {
                clusterOptimizers.put(clusterId, planner);
            }
        }

        synchronized (clusterOptimizers) {
            for (String clusterId: missingClusters) {
                clusterOptimizers.get(clusterId).terminate();
                clusterOptimizers.get(clusterId).getUpdaterInstance().terminate();
                log.info(String.format("Cluster %s was removed", clusterId));
            }
        }
    }

    @PreDestroy
    public void stop() {
        log.info("oVirt service service stopping");
        discoveryTimer.cancel();

        synchronized (clusterOptimizers) {
            for (ClusterOptimizer clusterOptimizer: clusterOptimizers.values()) {
                clusterOptimizer.getUpdaterInstance().terminate();
                clusterOptimizer.terminate();
            }
        }

        log.info("Waiting for threads to finish");
        // Iterate over copy of the set as the ending threads will
        // be removed by callback
        for (Thread thread: new ArrayList<>(threads)) {
            try {
                thread.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("oVirt service service stopped");
    }

    public Set<String> getAllClusters() {
        synchronized (clusterOptimizers) {
            return clusterOptimizers.keySet();
        }
    }

    public Set<String> getActiveClusters() {
        synchronized (clusterOptimizers) {
            return clusterOptimizers.keySet();
        }
    }

    @Override
    public Result getCurrentResult(String cluster) {
        ClusterOptimizer clusterOptimizer;
        Result r = new Result();
        r.setCluster(cluster);

        synchronized (clusterOptimizers) {
            clusterOptimizer = clusterOptimizers.get(cluster);
        }

        if (clusterOptimizer == null) {
            log.error(String.format("Cluster %s does not exist", cluster));
            r.setHostToVms(new HashMap<String, ArrayList<String>>());
            r.setVmToHost(new HashMap<String, String>());
        }
        else {
            Map<String, ArrayList<String>> hostToVms = new HashMap<>();
            Map<String, String> vmToHost = new HashMap<>();

            OptimalDistributionSolution best = clusterOptimizer.getBestSolution();
            r.setHosts(new HashSet<String>());
            for (Host host: best.getHosts()) {
                hostToVms.put(host.getId(), new ArrayList<String>());
                r.getHosts().add(host.getId());
            }
            for (VmAssignment vm: best.getVms()) {
                hostToVms.get(vm.getHost().getId()).add(vm.getVm().getId());
                vmToHost.put(vm.getId(), vm.getHost().getId());
            }
            r.setHostToVms(hostToVms);
            r.setVmToHost(vmToHost);
        }

        return r;
    }
}
