package org.ovirt.optimizer.service;

import org.apache.log4j.Logger;
import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.entities.Cluster;
import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirt.engine.sdk.exceptions.UnsecuredConnectionAttemptError;

import javax.annotation.ManagedBean;
import javax.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * This class implements a thread that periodically checks the engine for
 * new or disappearing clusterOptimizers.
 */
@ManagedBean
public class ClusterDiscovery {
    @Inject
    private Logger log;

    OvirtClient ovirtClient;

    @Inject public ClusterDiscovery(OvirtClient client) {
        this.ovirtClient = client;
    }

    Set<String> getClusters() {
        Set<String> clusters = new HashSet<>();

        try {
            log.info("Starting cluster discovery");
            Api engine = ovirtClient.connect();

            log.info("Processing clusters...");
            for (Cluster cluster: engine.getClusters().list()) {
                log.debug(String.format("Discovered cluster %s (%s)", cluster.getName(), cluster.getId()));
                clusters.add(cluster.getId());
            }
        } catch (IOException ex) {
            log.error(ex);
            return null;
        } catch (ServerException ex) {
            log.error(ex);
            return null;
        } catch (UnsecuredConnectionAttemptError ex) {
            log.error(ex);
            return null;
        }

        return clusters;
    }
}
