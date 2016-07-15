package org.ovirt.optimizer.solver.facts;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonAutoDetect;

/**
 * A fact representing one affinity label. This is needed because we are still using
 * oVirt SDK 3.6 that does not support affinity labels yet.
 */
@XmlRootElement
@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY,
        getterVisibility=JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=JsonAutoDetect.Visibility.NONE)
public class AffinityLabel {
    /**
     * Label ID
     */
    private String id;

    /**
     * ID of the referenced object
     */
    private String references;

    public AffinityLabel(String id, String references) {
        this.id = id;
        this.references = references;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReferences() {
        return references;
    }

    public void setReferences(String references) {
        this.references = references;
    }
}
