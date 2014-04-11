package org.ovirt.optimizer.service;

import org.apache.log4j.Logger;
import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.Network;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirt.engine.sdk.exceptions.UnsecuredConnectionAttemptError;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * This class implements a thread that monitors a cluster for
 * fact changes (new or missing VMs or hosts)
 */
public class ClusterInfoUpdater implements Runnable {
    private static Logger log = Logger.getLogger(ClusterInfoUpdater.class);

    public interface ClusterUpdateAvailable {
        void checkUpdate(Set<VM> vms, Set<Host> hosts, Set<Object> newFacts);
    }

    String clusterId;
    ClusterUpdateAvailable handler;
    volatile boolean running = true;
    OvirtClient ovirtClient;

    public ClusterInfoUpdater(OvirtClient client, String clusterId, ClusterUpdateAvailable handler) {
        this.clusterId = clusterId;
        this.handler = handler;
        this.ovirtClient = client;
    }

    public String getClusterId() {
        return clusterId;
    }

    @Override
    public void run() {
        log.info(String.format("Updater thread for %s starting", clusterId));
        while (running) {
            Set<Host> hosts = new HashSet<>();
            Set<String> hostIds = new HashSet<>();
            Set<VM> vms = new HashSet<>();
            Set<Object> facts = new HashSet<>();

            try {
                Api engine = ovirtClient.connect();

                for (Host host: engine.getHosts().list()) {
                    if (host.getCluster().getId().equals(clusterId)) {
                        log.debug(String.format("Discovered host %s (%s) on cluster %s with state %s",
                                host.getName(), host.getId(), clusterId, host.getStatus().getState()));
                        if (host.getStatus().getState().equals("up")) {
                            hosts.add(host);
                            hostIds.add(host.getId());
                        }
                    }
                }

                for (VM vm: engine.getVMs().list()) {
                    final Host runningAt = vm.getHost();
                    if (runningAt != null
                            && hostIds.contains(runningAt.getId())) {
                        log.debug(String.format("Discovered VM %s (%s) on cluster %s",
                                vm.getName(), vm.getId(), clusterId));
                        vms.add(vm);
                    }
                }

                for (Network network: engine.getNetworks().list()) {
                    if (network.getCluster() != null
                            && network.getCluster().getId().equals(clusterId)) {
                        log.debug(String.format("Discovered Network %s (%s) on cluster %s",
                                network.getName(), network.getId(), clusterId));
                        facts.add(network);
                    }
                }

            } catch (IOException ex) {
                log.error(ex);
                continue;
            } catch (ServerException ex) {
                log.error(ex);
                continue;
            } catch (UnsecuredConnectionAttemptError ex) {
                log.error(ex);
                continue;
            }

            handler.checkUpdate(vms, hosts, facts);

            // Wait, but watch for terminate command
            try {
                synchronized (this) {
                    if (running) {
                        this.wait(30000);
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


}
