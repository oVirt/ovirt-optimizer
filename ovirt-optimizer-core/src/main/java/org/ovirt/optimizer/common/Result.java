package org.ovirt.optimizer.common;

import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@XmlRootElement
public class Result implements Serializable {
    Map<String, Set<String>> hostToVms;
    Map<String, String> vmToHost;
    Map<String, String> currentVmToHost;
    List<Map<String, String>> migrations;
    Set<String> hosts;
    Set<String> vms;
    Set<String> requestedVms;
    String cluster;
    int softScore;
    int hardScore;

    public Result(String cluster) {
        this.cluster = cluster;
    }

    static public Result createEmpty(String cluster) {
        Result result = new Result(cluster);
        result.setHostToVms(new HashMap<String, Set<String>>());
        result.setVmToHost(new HashMap<String, String>());
        result.setCurrentVmToHost(new HashMap<String, String>());
        result.setMigrations(new ArrayList<Map<String, String>>());
        result.setHosts(new HashSet<String>());
        result.setVms(new HashSet<String>());
        result.setRequestedVms(new HashSet<String>());
        return result;
    }

    public Map<String, Set<String>> getHostToVms() {
        return hostToVms;
    }

    public void setHostToVms(Map<String, Set<String>> hostToVms) {
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

    public List<Map<String, String>> getMigrations() {
        return migrations;
    }

    public void setMigrations(List<Map<String, String>> migrations) {
        this.migrations = migrations;
    }

    public Map<String, String> getCurrentVmToHost() {
        return currentVmToHost;
    }

    public void setCurrentVmToHost(Map<String, String> currentVmToHost) {
        this.currentVmToHost = currentVmToHost;
    }

    public int getSoftScore() {
        return softScore;
    }

    public void setSoftScore(int softScore) {
        this.softScore = softScore;
    }

    public int getHardScore() {
        return hardScore;
    }

    public void setHardScore(int hardScore) {
        this.hardScore = hardScore;
    }

    public Set<String> getVms() {
        return vms;
    }

    public void setVms(Set<String> vms) {
        this.vms = vms;
    }

    public Set<String> getRequestedVms() {
        return requestedVms;
    }

    public void setRequestedVms(Set<String> requestedVms) {
        this.requestedVms = requestedVms;
    }
}
