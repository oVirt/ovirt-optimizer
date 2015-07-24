package org.ovirt.optimizer.util;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ConfigProvider {
    static private Logger log = org.slf4j.LoggerFactory.getLogger(ConfigProvider.class);

    public static final String SDK_SERVER = "org.ovirt.optimizer.sdk.server";
    public static final String SDK_PORT = "org.ovirt.optimizer.sdk.port";
    public static final String SDK_PROTOCOL = "org.ovirt.optimizer.sdk.protocol";
    public static final String SDK_REQUEST_TIMEOUT = "org.ovirt.optimizer.sdk.connection.timeout";
    public static final String SDK_USERNAME = "org.ovirt.optimizer.sdk.username";
    public static final String SDK_PASSWORD = "org.ovirt.optimizer.sdk.password";
    public static final String SDK_CA_STORE = "org.ovirt.optimizer.sdk.ca.store";

    public static final String SOLVER_STEPS = "org.ovirt.optimizer.solver.steps";
    public static final String SOLVER_TIMEOUT = "org.ovirt.optimizer.solver.timeout";
    public static final String SOLVER_DATA_REFRESH = "org.ovirt.optimizer.solver.data.refresh";
    public static final String SOLVER_CLUSTER_REFRESH = "org.ovirt.optimizer.solver.cluster.refresh";
    public static final String SOLVER_CUSTOM_RULE_DIR = "org.ovirt.optimizer.solver.custom.rules.d";


    private String configFile;
    private Properties config;

    public ConfigProvider() {
        this.config = new Properties();

        configFile = System.getenv("OVIRT_OPTIMIZER_CONFIG");
        if (configFile == null) {
            configFile = "/etc/ovirt-optimizer/ovirt-optimizer.properties";
        }

        config.setProperty(SDK_SERVER, "localhost");
        config.setProperty(SDK_PROTOCOL, "http");
        config.setProperty(SDK_PORT, "8080");
        config.setProperty(SDK_USERNAME, "admin@internal");
        config.setProperty(SDK_PASSWORD, "letmein");
        config.setProperty(SDK_CA_STORE, "/etc/ovirt-optimizer/ca.store");
        config.setProperty(SDK_REQUEST_TIMEOUT, "10");
        config.setProperty(SOLVER_STEPS, "10");
        config.setProperty(SOLVER_TIMEOUT, "30");
        config.setProperty(SOLVER_DATA_REFRESH, "60");
        config.setProperty(SOLVER_CLUSTER_REFRESH, "300");
        config.setProperty(SOLVER_CUSTOM_RULE_DIR, "/etc/ovirt-optimizer/rules.d");
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

    public List<File> customRuleFiles() {
        File dir = new File(config.getProperty(SOLVER_CUSTOM_RULE_DIR));
        List<File> listOfDrlFiles = new ArrayList<>();

        File[] candidateFiles = dir.listFiles();

        if (candidateFiles == null) {
            log.warn("Could not get a list of custom DRL files");
            return listOfDrlFiles;
        }

        log.debug("Found {} custom DRL candidate files", candidateFiles.length);

        for (File input: candidateFiles) {
            if (input.isFile()
                    && input.getName().endsWith(".drl")) {
                listOfDrlFiles.add(input);
                log.debug("Using {} custom DRL file", input.getName());
            }
        }

        return listOfDrlFiles;
    }
}
