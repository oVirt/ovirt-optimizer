package org.ovirt.optimizer.common;

import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

@XmlRootElement
public class Result implements Serializable {
    Map<String, ArrayList<String>> hostToVms;
    Map<String, String> vmToHost;
    Set<String> hosts;
    String cluster;

    public Result() {
    }

    public Map<String,ArrayList<String>> getHostToVms() {
        return hostToVms;
    }

    public void setHostToVms(Map<String, ArrayList<String>> hostToVms) {
        this.hostToVms = hostToVms;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public Set<String> getHosts() {
        return hosts;
    }

    public void setHosts(Set<String> hosts) {
        this.hosts = hosts;
    }

    public Map<String, String> getVmToHost() {
        return vmToHost;
    }

    public void setVmToHost(Map<String, String> vmToHost) {
        this.vmToHost = vmToHost;
    }
}
