package org.ovirt.optimizer.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ConfigProvider {
    static private Logger log = org.slf4j.LoggerFactory.getLogger(ConfigProvider.class);

    public static final String CONFIG_FILE = "org.ovirt.optimizer.config";

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

    public static final String DEBUG_ENDPOINT_ENABLED = "org.ovirt.optimizer.debug";

    private String configFile;
    private Properties config;

    public ConfigProvider() {
        this.config = System.getProperties();

        configFile = System.getenv("OVIRT_OPTIMIZER_CONFIG");

        if (configFile == null) {
            configFile = config.getProperty(CONFIG_FILE, null);
        }

        if (configFile == null) {
            configFile = "/etc/ovirt-optimizer/ovirt-optimizer.properties";
        }

        config.putIfAbsent(SDK_SERVER, "localhost");
        config.putIfAbsent(SDK_PROTOCOL, "https");
        config.putIfAbsent(SDK_PORT, "443");
        config.putIfAbsent(SDK_USERNAME, "admin@internal");
        config.putIfAbsent(SDK_PASSWORD, "letmein");
        config.putIfAbsent(SDK_CA_STORE, "/etc/ovirt-optimizer/ca.store");
        config.putIfAbsent(SDK_REQUEST_TIMEOUT, "10");
        config.putIfAbsent(SOLVER_STEPS, "10");
        config.putIfAbsent(SOLVER_TIMEOUT, "30");
        config.putIfAbsent(SOLVER_DATA_REFRESH, "60");
        config.putIfAbsent(SOLVER_CLUSTER_REFRESH, "300");
        config.putIfAbsent(SOLVER_CUSTOM_RULE_DIR, "/etc/ovirt-optimizer/rules.d");
        config.putIfAbsent(DEBUG_ENDPOINT_ENABLED, "false");
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public ConfigProvider load() {
        FileReader reader = null;

        try {
            reader = new FileReader(configFile);
            config.load(reader);
        } catch (IOException ex) {
            log.warn("Config file {} could not be opened. Using the defaults values with server: {}.",
                    configFile, config.getProperty(SDK_SERVER));
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                log.error("The config file could not be closed", ex);
            }
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
