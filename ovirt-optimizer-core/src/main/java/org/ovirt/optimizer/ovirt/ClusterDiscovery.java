package org.ovirt.optimizer.ovirt;

import javax.annotation.ManagedBean;
import javax.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.entities.Cluster;
import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirt.engine.sdk.exceptions.UnsecuredConnectionAttemptError;
import org.slf4j.Logger;

/**
 * This class implements a thread that periodically checks the engine for
 * new or disappearing clusterOptimizers.
 */
@ManagedBean
public class ClusterDiscovery {
    @Inject
    private Logger log;

    private OvirtClient ovirtClient;

    @Inject
    public ClusterDiscovery(OvirtClient client) {
        this.ovirtClient = client;
    }

    public Set<String> getClusters() {
        Set<String> clusters = new HashSet<>();

        try {
            log.debug("Starting cluster discovery");
            Api engine = ovirtClient.getConnection();

            log.debug("Processing clusters...");
            for (Cluster cluster: engine.getClusters().list()) {
                log.debug(String.format("Discovered cluster %s (%s)", cluster.getName(), cluster.getId()));
                clusters.add(cluster.getId());
            }
        } catch (IOException | ServerException | UnsecuredConnectionAttemptError ex) {
            log.error("Cluster discovery failed", ex);
            return null;
        }

        return clusters;
    }
}
