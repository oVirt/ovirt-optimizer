package org.ovirt.optimizer.solver.facts;

import org.codehaus.jackson.annotate.JsonAutoDetect;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * This fact represents a request that ensures a Vm with certain
 * UUID is running in the result.
 */
@XmlRootElement
@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY,
        getterVisibility=JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=JsonAutoDetect.Visibility.NONE)
public class RunningVm {
    private String id;

    protected RunningVm() {
    }

    public RunningVm(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
