package org.ovirt.optimizer.util;

import org.slf4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class ConfigProvider {
    static private Logger log = org.slf4j.LoggerFactory.getLogger(ConfigProvider.class);

    public static final String SDK_SERVER = "org.ovirt.optimizer.sdk.server";
    public static final String SDK_PORT = "org.ovirt.optimizer.sdk.port";
    public static final String SDK_USERNAME = "org.ovirt.optimizer.sdk.username";
    public static final String SDK_PASSWORD = "org.ovirt.optimizer.sdk.password";
    public static final String SDK_CA_STORE = "org.ovirt.optimizer.sdk.ca.store";

    public static final String SOLVER_STEPS = "org.ovirt.optimizer.solver.steps";

    private String configFile;
    private Properties config;

    public ConfigProvider() {
        this.config = new Properties();

        configFile = System.getenv("OVIRT_OPTIMIZER_CONFIG");
        if (configFile == null) {
            configFile = "/etc/ovirt-optimizer/ovirt-optimizer.properties";
        }

        config.setProperty(SDK_SERVER, "localhost");
        config.setProperty(SDK_PORT, "8080");
        config.setProperty(SDK_USERNAME, "admin@internal");
        config.setProperty(SDK_PASSWORD, "letmein");
        config.setProperty(SDK_CA_STORE, "/etc/ovirt-optimizer/ca.store");
        config.setProperty(SOLVER_STEPS, "10");
    }

    public ConfigProvider load() {
        try {
            config.load(new FileReader(configFile));
        } catch (IOException ex) {
            log.error("Connection to oVirt REST server failed", ex);
        }

        return this;
    }

    public Properties getConfig() {
        return config;
    }
}
