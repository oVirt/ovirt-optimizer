package org.ovirt.optimizer.config;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;

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
        try (FileReader reader = new FileReader(configFile)) {
            config.load(reader);
        } catch (IOException ex) {
            log.warn("Config file {} could not be opened. Using the defaults values with server: {}.",
                    configFile, config.getProperty(SDK_SERVER));
        }

        return this;
    }

    public Properties getConfig() {
        return config;
    }

    public List<Path> customRuleFiles() {
        Path dir = FileSystems.getDefault().getPath(config.getProperty(SOLVER_CUSTOM_RULE_DIR));
        final List<Path> candidateFiles;

        try {
            candidateFiles = Files.list(dir).collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Could not get a list of custom DRL files:", e);
            return Collections.emptyList();
        }

        log.debug("Found {} custom DRL candidate files", candidateFiles.size());

        return candidateFiles.stream()
                .peek(p -> log.debug("Checking out file {}", p))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".drl"))
                .peek(p -> log.info("Using {} as custom DRL file", p))
                .collect(Collectors.toList());
    }
}
