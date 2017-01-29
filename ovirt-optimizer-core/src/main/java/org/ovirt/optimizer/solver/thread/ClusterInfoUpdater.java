package org.ovirt.optimizer.solver.thread;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.jboss.resteasy.util.HttpResponseCodes;
import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.decorators.Cluster;
import org.ovirt.engine.sdk.decorators.ClusterAffinityGroup;
import org.ovirt.engine.sdk.decorators.HostStatistics;
import org.ovirt.engine.sdk.decorators.SchedulingPolicy;
import org.ovirt.engine.sdk.decorators.SchedulingPolicyBalance;
import org.ovirt.engine.sdk.decorators.SchedulingPolicyFilter;
import org.ovirt.engine.sdk.decorators.SchedulingPolicyWeight;
import org.ovirt.engine.sdk.decorators.VMStatistics;
import org.ovirt.engine.sdk.entities.DataCenter;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.Network;
import org.ovirt.engine.sdk.entities.Property;
import org.ovirt.engine.sdk.entities.Statistic;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.engine.sdk.exceptions.OvirtSdkException;
import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirt.engine.sdk.exceptions.UnsecuredConnectionAttemptError;
import org.ovirt.engine.sdk4.Connection;
import org.ovirt.engine.sdk4.Error;
import org.ovirt.engine.sdk4.services.AffinityLabelsService;
import org.ovirt.optimizer.ovirt.OvirtClient;
import org.ovirt.optimizer.solver.facts.AffinityLabel;
import org.ovirt.optimizer.solver.facts.HostInfo;
import org.ovirt.optimizer.solver.facts.HostStats;
import org.ovirt.optimizer.solver.facts.PolicyUnitEnabled;
import org.ovirt.optimizer.solver.facts.RunningVm;
import org.ovirt.optimizer.solver.facts.VmAffinityGroup;
import org.ovirt.optimizer.solver.facts.VmInfo;
import org.ovirt.optimizer.solver.facts.VmStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements a thread that monitors a cluster for
 * fact changes (new or missing VMs or hosts)
 */
public class ClusterInfoUpdater implements Runnable {
    private static Logger log = LoggerFactory.getLogger(ClusterInfoUpdater.class);

    private String clusterId;
    private OvirtClient ovirtClient;
    private ClusterOptimizer optimizer;

    public ClusterInfoUpdater(OvirtClient client, ClusterOptimizer optimizer) {
        this.clusterId = optimizer.getClusterId();
        this.optimizer = optimizer;
        this.ovirtClient = client;
    }

    @Override
    public void run() {
        log.info("Updater thread for {} starting", clusterId);

        Set<Host> hosts = new HashSet<>();
        Set<String> hostIds = new HashSet<>();
        Set<VM> vms = new HashSet<>();
        Set<Object> facts = new HashSet<>();

        final Api engine;
        final Cluster clusterInstance;
        final DataCenter dataCenter;

        try {
            engine = ovirtClient.getConnection();
            clusterInstance = engine.getClusters().getById(clusterId);
        } catch (UnsecuredConnectionAttemptError | IOException ex) {
            log.error("Could not connect to the server: {}", ex);
            return;
        } catch (ServerException ex) {
            if (ex.getCode() == HttpResponseCodes.SC_NOT_FOUND) {
                log.info("Cluster {} was removed from server.", clusterId);
                optimizer.terminate();
                return;
            } else {
                log.error("Server returned an error: {}", ex);
            }
            return;
        }

        try {
            dataCenter = clusterInstance.getDataCenter();

            // Add affinity groups to fact database
            for(ClusterAffinityGroup group : clusterInstance.getAffinityGroups().list()){
                VmAffinityGroup fact = VmAffinityGroup.create(group, group.getVMs().list());
                facts.add(fact);
            }

            try {
                // Import affinity labels (available since engine 4.0, handle failure gracefully)
                if (engine.getProductInfo().getVersion().getMajor() >= 4) {
                    Connection apiv4 = ovirtClient.getApi4Connection();
                    final AffinityLabelsService affinityLabelsService =
                            apiv4.systemService().affinityLabelsService();

                    affinityLabelsService
                            .list().send().labels().stream().forEach(label -> {
                        log.debug("Discovered AffinityLabel {} ({})",
                                label.name(), label.id());

                        affinityLabelsService.labelService(label.id())
                                .hostsService().list().send().hosts().stream()
                                .forEach(host -> facts.add(new AffinityLabel(label.id(), host.id())));
                        affinityLabelsService.labelService(label.id())
                                .vmsService().list().send().vms().stream()
                                .forEach(vm -> facts.add(new AffinityLabel(label.id(), vm.id())));
                    });
                }
            } catch (IOException|Error ex) {
                log.error("SDKv4 threw an error and affinity labels won't work.", ex);
            }

            boolean threadsAsCores = clusterInstance.getThreadsAsCores();

            // Ask for all content (needed to get hosted engine info)
            for (Host host: engine.getHosts().list(null, null, null, "true")) {
                if (host.getCluster().getId().equals(clusterId)) {
                    log.debug("Discovered host {} ({}) on cluster {} with state {}",
                            host.getName(), host.getId(), clusterId, host.getStatus().getState());
                    if (host.getStatus().getState().equals("up")) {
                                /* Reconstruct references to other structures */
                        host.setCluster(clusterInstance);

                                /* Add the host to fact database */
                        hosts.add(host);
                        hostIds.add(host.getId());

                        // Compile host statistics and add them to fact database

                        // Interestingly the typecast has to be to different object than
                        // the parameter of Host.setStatistics()
                        HostStatistics stats = (HostStatistics) host.getStatistics();

                        HostStats hostStats = new HostStats(host.getId());
                        for (Statistic stat : stats.list()){
                            hostStats.loadValue(stat);
                        }
                        facts.add(hostStats);

                        // Add additional host info
                        facts.add(HostInfo.createFromHost(host, threadsAsCores));
                    }
                }
            }

            for (VM vm: engine.getVMs().list()) {
                final Host runningAt = vm.getHost();
                if (runningAt == null ||
                        hostIds.contains(runningAt.getId())) {
                    log.debug("Discovered VM {} ({}) on cluster {}",
                            vm.getName(), vm.getId(), clusterId);

                    // Add VM to fact database
                    vms.add(vm);
                    if (runningAt != null) {
                        facts.add(new RunningVm(vm.getId()));
                    }

                    // Compile VM statistics and add them to fact database

                    // Typecast is needed; like for host statistics
                    VMStatistics stats = (VMStatistics) vm.getStatistics();

                    VmStats vmStats = new VmStats(vm.getId());
                    for(Statistic stat : stats.list()){
                        vmStats.loadValue(stat);
                    }
                    facts.add(vmStats);

                    // Add additional VM info
                    facts.add(VmInfo.createFromVm(vm, threadsAsCores));
                }
            }

            for (Network network: clusterInstance.getNetworks().list()) {
                if (network.getDataCenter() != null
                        && network.getDataCenter().getId().equals(clusterInstance.getDataCenter().getId())) {
                    log.debug("Discovered Network {} ({}) on cluster {} [datacenter {}]",
                            network.getName(), network.getId(), clusterInstance.getId(), dataCenter.getId());
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
        } catch (IOException|OvirtSdkException ex) {
            log.error("Cluster update failed", ex);
            return;
        }

        // Notify optimizer about the current data
        log.info("Notifying optimizer about new data");
        optimizer.processUpdate(vms, hosts, facts);

        log.info("Updater thread for {} finished", clusterId);
    }
}
