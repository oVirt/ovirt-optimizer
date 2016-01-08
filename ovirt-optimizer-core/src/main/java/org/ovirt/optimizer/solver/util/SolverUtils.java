package org.ovirt.optimizer.solver.util;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.constraint.ConstraintMatch;
import org.optaplanner.core.api.score.constraint.ConstraintMatchTotal;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.ovirt.engine.sdk.entities.Host;
import org.ovirt.engine.sdk.entities.VM;
import org.ovirt.optimizer.rest.dto.Result;
import org.ovirt.optimizer.solver.facts.Instance;
import org.ovirt.optimizer.solver.facts.RunningVm;
import org.ovirt.optimizer.solver.problemspace.ClusterSituation;
import org.ovirt.optimizer.solver.problemspace.Migration;
import org.ovirt.optimizer.solver.problemspace.OptimalDistributionStepsSolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SolverUtils {
    private static final Logger log = LoggerFactory.getLogger(SolverUtils.class);

    // Cache for the score only solver
    private static List<File> recordedCustomDrlFiles = Collections.emptyList();
    private static Solver scoreOnlySolver = null;

    public static void addCustomDrlFiles(ScoreDirectorFactoryConfig config, List<File> customDrlFiles) {
        if (config.getScoreDrlFileList() != null) {
            config.getScoreDrlFileList().addAll(customDrlFiles);
        } else {
            config.setScoreDrlFileList(customDrlFiles);
        }
    }

    /**
     * Return new or cached score only solver. Detect changes to the rule list
     * and re-create the solver in that case.
     *
     * @param customDrlFiles the list of user provided DRL files
     * @return score only solver
     */
    public static Solver getScoreOnlySolver(List<File> customDrlFiles) {
        Set<File> changeDetector = new HashSet<>(customDrlFiles);
        changeDetector.retainAll(recordedCustomDrlFiles);

        if (scoreOnlySolver != null
                && customDrlFiles.size() == changeDetector.size()
                && recordedCustomDrlFiles.size() == changeDetector.size()) {
            return scoreOnlySolver;
        }

        SolverFactory solverFactory =
                SolverFactory.createFromXmlResource("org/ovirt/optimizer/solver/rules/scoreonly.xml");
        addCustomDrlFiles(solverFactory.getSolverConfig().getScoreDirectorFactoryConfig(), customDrlFiles);
        scoreOnlySolver = solverFactory.buildSolver();
        recordedCustomDrlFiles = customDrlFiles;
        return scoreOnlySolver;
    }

    public static HardSoftScore computeScore(OptimalDistributionStepsSolution sourceSolution,
            Result result,
            Set<String> runningIds,
            List<File> customDrlFiles) {
        log.debug("Reevaluating solution {}", result);

        Solver solver = getScoreOnlySolver(customDrlFiles);

        // Get primary instances and VM -> ID map
        Map<String, Long> vmToInst = new HashMap<>();
        Set<Instance> primaryInstances = new HashSet<>();
        for (Instance i : sourceSolution.getInstances()) {
            if (!i.getPrimary()) {
                continue;
            }

            vmToInst.put(i.getVmId(), i.getId());
            primaryInstances.add(i);
        }

        /* Reconstruct the Solution object with current facts */
        OptimalDistributionStepsSolution solution = new OptimalDistributionStepsSolution();
        solution.setHosts(sourceSolution.getHosts());
        solution.setInstances(primaryInstances);
        solution.setOtherFacts(sourceSolution.getOtherFacts());
        solution.setFixedFacts(sourceSolution.getFixedFacts());
        solution.setVms(solution.getVms());

        /* Allow an expected VM start to be specified */
        for (String id : runningIds) {
            solution.getOtherFacts().add(new RunningVm(id));
        }

        /* Get id to object mappings for hosts and VMs */
        Map<String, Host> hosts = new HashMap<>();
        for (Host h : solution.getHosts()) {
            hosts.put(h.getId(), h);
            log.debug("Found host {}", h.getId());
        }

        Map<Long, Instance> instances = new HashMap<>();
        for (Instance inst : solution.getInstances()) {
            instances.put(inst.getId(), inst);
            log.debug("Found instance {} for VM {}", inst.getId(), inst.getVmId());
        }

        /* Recreate secondary instances */
        Map<Long, Long> old2New = new HashMap<>();
        for (Map.Entry<Long, String> inst: result.getBackups().entrySet()) {
            if (!vmToInst.containsKey(inst.getValue())) {
                continue;
            }

            Instance newInst = new Instance(inst.getValue());
            newInst.setPrimary(false);

            old2New.put(inst.getKey(), newInst.getId());
            instances.put(newInst.getId(), newInst);
        }

        /* Recreate the migration objects from real and secondary instances */
        List<Migration> migrations = recreateMigrations(result, hosts, instances, vmToInst, old2New);

        // It is necessary to have at least one migration object to make
        // the rules work. It does not have to describe any valid action,
        // it just needs to be there.
        if (migrations.isEmpty()) {
            migrations.add(new Migration());
        }

        /* Compute the migration ordering cache */
        solution.setSteps(migrations);
        solution.establishStepOrdering();

        /* Prepare shadow variables */
        ClusterSituation previous = solution;

        for (Migration m : migrations) {
            m.recomputeSituationAfter(previous);
            previous = m;
        }

        /* Recompute score */
        solver.solve(solution);

        if (log.isDebugEnabled()) {
            recomputeScoreUsingScoreDirector(solver, solution);
        }

        return solution.getScore();
    }

    private static List<Migration> recreateMigrations(Result result,
            Map<String, Host> hosts,
            Map<Long, Instance> instances, Map<String, Long> vmToInst, Map<Long, Long> old2New) {
        List<Migration> migrations = new ArrayList<>();
        List<List<Map<Long, String>>> backupReorgs = new ArrayList<>(result.getBackupReorg());

        for (Map<String, String> migrationStep : result.getMigrations()) {
            popAndInsertBackupSpaceMigrations(hosts, instances, old2New, migrations, backupReorgs);

            for (Map.Entry<String, String> singleMigration : migrationStep.entrySet()) {
                // Create new migration step
                Migration migration = new Migration();
                String hostId = singleMigration.getValue();

                // Inject current host data
                if (hosts.containsKey(hostId)) {
                    migration.setDestination(hosts.get(hostId));
                    log.debug("Setting destination for {} to {}", migration, hostId);
                } else {
                    log.debug("Host {} is no longer valid", hostId);
                }

                // Inject current VM data
                String vmId = singleMigration.getKey();
                if (vmToInst.containsKey(vmId) && instances.containsKey(vmToInst.get(vmId))) {
                    Long instId = vmToInst.get(vmId);
                    migration.setInstance(instances.get(instId));
                    log.debug("Setting VM for {} to {}", migration, vmId);
                } else {
                    log.debug("VM {} is no longer valid", vmId);
                }

                // Add the step to the list of steps
                migrations.add(migration);
            }
        }

        popAndInsertBackupSpaceMigrations(hosts, instances, old2New, migrations, backupReorgs);
        return migrations;
    }

    private static void popAndInsertBackupSpaceMigrations(Map<String, Host> hosts,
            Map<Long, Instance> instances,
            Map<Long, Long> old2New, List<Migration> migrations, List<List<Map<Long, String>>> backupReorgs) {

        // Do nothing when there is less backup space migrations steps than needed
        if (backupReorgs.isEmpty()) {
            return;
        }

        List<Map<Long, String>> reorgStep = backupReorgs.get(0);
        backupReorgs.remove(0);

        for (Map<Long, String> backupMigrations: reorgStep) {
            for (Map.Entry<Long, String> singleBackupMigration: backupMigrations.entrySet()) {
                if (!hosts.containsKey(singleBackupMigration.getValue())) {
                    continue;
                }

                Migration migration = new Migration();
                migration.setDestination(hosts.get(singleBackupMigration.getValue()));
                Long oldInstanceId = singleBackupMigration.getKey();
                migration.setInstance(instances.get(old2New.getOrDefault(oldInstanceId, oldInstanceId)));
                migrations.add(migration);
            }
        }
    }

    /**
     * Recompute solution's score and log all rules that affected it (in debug mode)
     * This method uses new score director from the passed solver
     *
     * @param solver
     * @param solution
     */
    public static void recomputeScoreUsingScoreDirector(Solver solver, OptimalDistributionStepsSolution solution) {
        ScoreDirector director = solver.getScoreDirectorFactory().buildScoreDirector();
        director.setWorkingSolution(solution);
        Score score = director.calculateScore();

        for (ConstraintMatchTotal constraintMatchTotal : director.getConstraintMatchTotals()) {
            String constraintName = constraintMatchTotal.getConstraintName();
            Number weightTotal = constraintMatchTotal.getWeightTotalAsNumber();
            for (ConstraintMatch constraintMatch : constraintMatchTotal.getConstraintMatchSet()) {
                List<Object> justificationList = constraintMatch.getJustificationList();
                Number weight = constraintMatch.getWeightAsNumber();
                log.debug("Constraint match {} with weight {}", constraintMatch, weight);
                for (Object item : justificationList) {
                    log.debug("Justified by {}", item);
                }
            }
        }
        log.debug("Final score {}", score);
    }
}
