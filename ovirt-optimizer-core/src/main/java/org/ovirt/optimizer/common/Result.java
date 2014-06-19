package org.ovirt.optimizer.common;

import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
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
    String cluster;
    int softScore;
    int hardScore;

    public Result() {
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
}
