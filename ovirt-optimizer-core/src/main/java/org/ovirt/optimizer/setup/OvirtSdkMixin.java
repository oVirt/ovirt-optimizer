package org.ovirt.optimizer.setup;

import org.codehaus.jackson.annotate.JsonProperty;
import org.ovirt.engine.sdk.web.HttpProxyBroker;

/**
 * This is needed to be able to deserialize our SDK decorator classes properly
 */
public abstract class OvirtSdkMixin {
    public OvirtSdkMixin(@JsonProperty("proxy") HttpProxyBroker proxy) {
    }
}
