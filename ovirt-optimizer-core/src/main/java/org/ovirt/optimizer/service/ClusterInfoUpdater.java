package org.ovirt.optimizer.service;

import org.apache.http.conn.HttpHostConnectException;
import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.decorators.Cluster;
import org.ovirt.engine.sdk.decorators.SchedulingPolicy;
import org.ovirt.engine.sdk.decorators.SchedulingPolicyBalance;
import org.ovirt.engine.sdk.decorators.SchedulingPolicyFilter;
import org.ovirt.engine.sdk.decorators.SchedulingPolicyWeight;
import org.ovirt.engine.sdk.decorators.SchedulingPolicyWeights;
import org.ovirt.engine.sdk.entities.DataCenter;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.Network;
import org.ovirt.engine.sdk.entities.Property;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirt.engine.sdk.exceptions.UnsecuredConnectionAttemptError;
import org.ovirt.optimizer.service.facts.PolicyUnitEnabled;
import org.ovirt.optimizer.service.facts.RunningVm;
import org.ovirt.optimizer.util.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * This class implements a thread that monitors a cluster for
 * fact changes (new or missing VMs or hosts)
 */
public class ClusterInfoUpdater implements Runnable {
    private static Logger log = LoggerFactory.getLogger(ClusterInfoUpdater.class);

    public interface ClusterUpdateAvailable {
        void checkUpdate(Set<VM> vms, Set<Host> hosts, Set<Object> newFacts);
    }

    String clusterId;
    Set<ClusterUpdateAvailable> handlers;
    volatile boolean running = true;
    OvirtClient ovirtClient;
    ConfigProvider configProvider;

    public ClusterInfoUpdater(OvirtClient client, ConfigProvider configProvider, String clusterId) {
        this.clusterId = clusterId;
        this.handlers = new HashSet<>();
        this.ovirtClient = client;
        this.configProvider = configProvider;
    }

    public String getClusterId() {
        return clusterId;
    }

    @Override
    public void run() {
        log.info(String.format("Updater thread for %s starting", clusterId));
        while (running) {
            int refresh = Integer.valueOf(configProvider.load().getConfig().getProperty(ConfigProvider.SOLVER_DATA_REFRESH));

            Set<Host> hosts = new HashSet<>();
            Set<String> hostIds = new HashSet<>();
            Set<VM> vms = new HashSet<>();
            Set<Object> facts = new HashSet<>();

            try {
                Api engine = ovirtClient.connect();

                Cluster clusterInstance = engine.getClusters().getById(clusterId);
                DataCenter dataCenter = clusterInstance.getDataCenter();

                // Ask for all content (needed to get hosted engine info)
                for (Host host: engine.getHosts().list(null, null, null, "true")) {
                    if (host.getCluster().getId().equals(clusterId)) {
                        log.debug(String.format("Discovered host %s (%s) on cluster %s with state %s",
                                host.getName(), host.getId(), clusterId, host.getStatus().getState()));
                        if (host.getStatus().getState().equals("up")) {
                            /* Reconstruct references to other structures */
                            host.setCluster(clusterInstance);

                            /* Add the host to fact database */
                            hosts.add(host);
                            hostIds.add(host.getId());
                        }
                    }
                }

                for (VM vm: engine.getVMs().list()) {
                    final Host runningAt = vm.getHost();
                    if (runningAt == null ||
                            hostIds.contains(runningAt.getId())) {
                        log.debug(String.format("Discovered VM %s (%s) on cluster %s",
                                vm.getName(), vm.getId(), clusterId));
                        vms.add(vm);
                        if (runningAt != null) {
                            facts.add(new RunningVm(vm.getId()));
                        }
                    }
                }

                for (Network network: clusterInstance.getNetworks().list()) {
                    if (network.getDataCenter() != null
                            && network.getDataCenter().getId().equals(clusterInstance.getDataCenter().getId())) {
                        log.debug(String.format("Discovered Network %s (%s) on cluster %s [datacenter %s]",
                                network.getName(), network.getId(), clusterInstance.getId(), dataCenter.getId()));
                        facts.add(network);
                    }
                }

                String schedulingPolicyId = clusterInstance.getSchedulingPolicy().getId();
                SchedulingPolicy schedulingPolicy = engine.getSchedulingPolicies().getById(schedulingPolicyId);
                for (SchedulingPolicyWeight weight: schedulingPolicy.getWeights().list()) {
                    log.debug("Found policy unit weight {} with factor {}", weight.getId(), weight.getFactor());
                    PolicyUnitEnabled policyUnitEnabled = new PolicyUnitEnabled(weight.getId(), weight.getFactor());
                    facts.add(policyUnitEnabled);
                }

                for (SchedulingPolicyFilter filter: schedulingPolicy.getFilters().list()) {
                    log.debug("Found policy unit filter {}", filter.getId());
                    PolicyUnitEnabled policyUnitEnabled = new PolicyUnitEnabled(filter.getId(), 1);
                    facts.add(policyUnitEnabled);
                }

                for (SchedulingPolicyBalance balance: schedulingPolicy.getBalances().list()) {
                    log.debug("Found policy unit balancer {}", balance.getId());
                    PolicyUnitEnabled policyUnitEnabled = new PolicyUnitEnabled(balance.getId(), 1);
                    facts.add(policyUnitEnabled);
                }

                /* Find scheduling policy properties - try cluster configuration first
                   and policy defaults as backup
                 */
                Collection<Property> schedulingPolicyProperties = null;

                if (clusterInstance.getSchedulingPolicy().getProperties() != null
                        && clusterInstance.getSchedulingPolicy().getProperties().getProperties() != null) {
                    schedulingPolicyProperties = clusterInstance.getSchedulingPolicy().getProperties().getProperties();
                } else if (schedulingPolicy.getProperties() != null
                        && schedulingPolicy.getProperties().getProperties() != null) {
                    schedulingPolicyProperties = schedulingPolicy.getProperties().getProperties();
                } else {
                    log.debug("No cluster policy properties found");
                }


                if (schedulingPolicyProperties != null) {
                    if (log.isDebugEnabled()) {
                        for (Property p: schedulingPolicyProperties) {
                            log.debug("Found policy property {} with value '{}'", p.getName(), p.getValue());
                        }
                    }
                    facts.addAll(schedulingPolicyProperties);
                }
            } catch (ConnectException ex) {
                log.error("Cluster update failed", ex);
                continue;
            } catch (IOException ex) {
                log.error("Cluster update failed", ex);
                continue;
            } catch (ServerException ex) {
                log.error("Cluster update failed", ex);
                continue;
            } catch (UnsecuredConnectionAttemptError ex) {
                log.error("Cluster update failed", ex);
                continue;
            }

            Set<ClusterUpdateAvailable> currentHandlers = new HashSet<>();

            synchronized (handlers) {
                currentHandlers.addAll(handlers);
            }

            for (ClusterUpdateAvailable handler: currentHandlers) {
                handler.checkUpdate(vms, hosts, facts);
            }

            // Wait, but watch for terminate command
            try {
                synchronized (this) {
                    if (running) {
                        this.wait(refresh * 1000);
                    }
                }
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        log.info(String.format("Updater thread for %s finished", clusterId));
    }

    public void terminate() {
        log.info(String.format("Updater thread for %s was asked to terminate", clusterId));
        synchronized (this) {
            running = false;
            this.notifyAll();
        }
        log.info(String.format("Updater thread for %s sent the termination command", clusterId));
    }

    void addHandler(ClusterUpdateAvailable handler) {
        synchronized (handlers) {
            handlers.add(handler);
        }
    }

    void removeHandler(ClusterUpdateAvailable handler) {
        synchronized (handlers) {
            handlers.remove(handler);
        }
    }
}
