package org.ovirt.optimizer.service.facts;


import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.optimizer.service.InfoUtils;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY,
        getterVisibility=JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=JsonAutoDetect.Visibility.NONE)
public class HostInfo {

    private String id;
    private Integer totalCores;

    protected HostInfo() {
    }

    public HostInfo(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getTotalCores() {
        return totalCores;
    }

    public void setTotalCores(Integer totalCores) {
        this.totalCores = totalCores;
    }

    public static HostInfo createFromHost(Host host, boolean threadsAsCores){
        HostInfo info = new HostInfo(host.getId());
        info.setTotalCores( InfoUtils.coresFromTopology(host.getCpu().getTopology(), threadsAsCores) );

        return info;
    }
}
