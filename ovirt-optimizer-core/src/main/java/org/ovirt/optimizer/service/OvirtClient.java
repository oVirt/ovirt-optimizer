package org.ovirt.optimizer.service;

import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirt.engine.sdk.exceptions.UnsecuredConnectionAttemptError;
import org.ovirt.optimizer.util.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.ManagedBean;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

/**
 * The configuration is read from /etc/ovirt-optimizer/ovirt-optimizer.properties
 *
 * The keys are:
 *
 * org.ovirt.optimizer.sdk.server=fqdn of running ovirt engine
 * org.ovirt.optimizer.sdk.port=port where running engine can be contacted
 * org.ovirt.optimizer.sdk.username=username to use when logging to the engine REST API
 * org.ovirt.optimizer.sdk.password=password to use when logging to the engine REST API
 * org.ovirt.optimizer.sdk.ca.store=file with trusted certificates (used for HTTPS)
 *
 * The location of the config file can be changed with the environment variable
 *   OVIRT_OPTIMIZER_CONFIG
 */
@ManagedBean
public class OvirtClient {

    static private Logger log = LoggerFactory.getLogger(OvirtClient.class);

    String server;
    String port;
    String username;
    String password;
    String caStore;

    final Properties config;

    public OvirtClient() {
        /* Create config with default values */
        config = new ConfigProvider().load().getConfig();

        this.server = config.getProperty(ConfigProvider.SDK_SERVER);
        this.port = config.getProperty(ConfigProvider.SDK_PORT);
        this.username = config.getProperty(ConfigProvider.SDK_USERNAME);
        this.password = config.getProperty(ConfigProvider.SDK_PASSWORD);
        this.caStore = config.getProperty(ConfigProvider.SDK_CA_STORE);
    }

    public Api connect()
            throws UnsecuredConnectionAttemptError, ServerException, IOException {
        String url = String.format("http://%s:%s/ovirt-engine/api", server, port);
        log.debug(String.format("Logging to %s as %s", url, username));
        return new Api(url, username, password, false);
    }
}
