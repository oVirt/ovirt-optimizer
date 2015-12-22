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

        SolverFactory solverFactory = SolverFactory.createFromXmlResource("org/ovirt/optimizer/solver/rules/scoreonly.xml");
        addCustomDrlFiles(solverFactory.getSolverConfig().getScoreDirectorFactoryConfig(), customDrlFiles);
        scoreOnlySolver = solverFactory.buildSolver();
        recordedCustomDrlFiles = customDrlFiles;
        return scoreOnlySolver;
    }

    public static HardSoftScore computeScore(OptimalDistributionStepsSolution sourceSolution,
            List<Map<String, String>> migrationIds,
            Set<String> runningIds,
            List<File> customDrlFiles) {
        log.debug("Reevaluating solution {}", migrationIds);

        Solver solver = getScoreOnlySolver(customDrlFiles);

        /* Reconstruct the Solution object with current facts */
        OptimalDistributionStepsSolution solution = new OptimalDistributionStepsSolution();
        solution.setHosts(sourceSolution.getHosts());
        solution.setVms(sourceSolution.getVms());
        solution.setOtherFacts(sourceSolution.getOtherFacts());
        solution.setFixedFacts(sourceSolution.getFixedFacts());

        /* Allow an expected VM start to be specified */
        for (String id: runningIds) {
            solution.getOtherFacts().add(new RunningVm(id));
        }

        /* Get id to object mappings for hosts and VMs */
        Map<String, Host> hosts = new HashMap<>();
        for (Host h: solution.getHosts()) {
            hosts.put(h.getId(), h);
            log.debug("Found host {}", h.getId());
        }

        Map<String, VM> vms = new HashMap<>();
        for (VM vm: solution.getVms()) {
            vms.put(vm.getId(), vm);
            log.debug("Found VM {}", vm.getId());
        }

        /* Recreate the migration objects */
        List<Migration> migrations = new ArrayList<>();
        for (Map<String, String> migrationStep: migrationIds) {
            for (Map.Entry<String, String> singleMigration: migrationStep.entrySet()) {
                // Create new migration step
                Migration migration = new Migration();
                String hostId = singleMigration.getValue();

                // Inject current host data
                if (hosts.containsKey(hostId)) {
                    migration.setDestination(hosts.get(hostId));
                    log.debug("Setting destination for {} to {}", migration, hostId);
                }
                else {
                    log.debug("Host {} is no longer valid", hostId);
                }

                // Inject current VM data
                String vmId = singleMigration.getKey();
                if (vms.containsKey(vmId)) {
                    migration.setVm(vms.get(vmId));
                    log.debug("Setting VM for {} to {}", migration, vmId);
                }
                else {
                    log.debug("VM {} is no longer valid", vmId);
                }

                // Add the step to the list of steps
                migrations.add(migration);
            }
        }

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

        for (Migration m: migrations) {
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

    /**
     * Recompute solution's score and log all rules that affected it (in debug mode)
     * This method uses new score director from the passed solver
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
                for (Object item: justificationList) {
                    log.debug("Justified by {}", item);
                }
            }
        }
        log.debug("Final score {}", score);
    }
}
