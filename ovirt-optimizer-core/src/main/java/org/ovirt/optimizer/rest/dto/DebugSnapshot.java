package org.ovirt.optimizer.rest.dto;

import javax.xml.bind.annotation.XmlRootElement;

import org.ovirt.optimizer.solver.problemspace.OptimalDistributionStepsSolution;

@XmlRootElement
public class DebugSnapshot {
    String cluster;
    OptimalDistributionStepsSolution state;
    Result result;

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public OptimalDistributionStepsSolution getState() {
        return state;
    }

    public void setState(OptimalDistributionStepsSolution state) {
        this.state = state;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }
}
