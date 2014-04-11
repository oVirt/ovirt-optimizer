package org.ovirt.optimizer.service;

import org.apache.log4j.Logger;
import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirt.engine.sdk.exceptions.UnsecuredConnectionAttemptError;

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
    public static final String SDK_SERVER = "org.ovirt.optimizer.sdk.server";
    public static final String SDK_PORT = "org.ovirt.optimizer.sdk.port";
    public static final String SDK_USERNAME = "org.ovirt.optimizer.sdk.username";
    public static final String SDK_PASSWORD = "org.ovirt.optimizer.sdk.password";
    public static final String SDK_CA_STORE = "org.ovirt.optimizer.sdk.ca.store";

    static private Logger log = Logger.getLogger(OvirtClient.class);

    String server;
    String port;
    String username;
    String password;
    String caStore;

    final Properties config;

    public OvirtClient() {
        String configFile = System.getenv("OVIRT_OPTIMIZER_CONFIG");
        if (configFile == null) {
            configFile = "/etc/ovirt-optimizer/ovirt-optimizer.properties";
        }

        /* Create config with default values */
        config = new Properties();
        config.setProperty(SDK_SERVER, "localhost");
        config.setProperty(SDK_PORT, "8080");
        config.setProperty(SDK_USERNAME, "admin@internal");
        config.setProperty(SDK_PASSWORD, "letmein");
        config.setProperty(SDK_CA_STORE, "/etc/ovirt-optimizer/ca.store");

        try {
            config.load(new FileReader(configFile));
        } catch (IOException ex) {
            log.error(ex);
        }

        this.server = config.getProperty(SDK_SERVER);
        this.port = config.getProperty(SDK_PORT);
        this.username = config.getProperty(SDK_USERNAME);
        this.password = config.getProperty(SDK_PASSWORD);
        this.caStore = config.getProperty(SDK_CA_STORE);
    }

    public Api connect()
            throws UnsecuredConnectionAttemptError, ServerException, IOException {
        String url = String.format("http://%s:%s/ovirt-engine/api", server, port);
        log.debug(String.format("Logging to %s as %s", url, username));
        return new Api(url, username, password, false);
    }
}
