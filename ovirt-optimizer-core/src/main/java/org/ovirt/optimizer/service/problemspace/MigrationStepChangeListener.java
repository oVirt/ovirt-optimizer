package org.ovirt.optimizer.service.problemspace;

import org.optaplanner.core.impl.domain.variable.listener.VariableListener;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationStepChangeListener implements VariableListener<Migration> {
    Logger logger = LoggerFactory.getLogger(MigrationStepChangeListener.class);

    @Override
    public void beforeEntityAdded(ScoreDirector scoreDirector, Migration entity) {
    }

    @Override
    public void afterEntityAdded(ScoreDirector scoreDirector, Migration entity) {
        afterVariableChanged(scoreDirector, entity);

        // recompute the last step flag
        OptimalDistributionStepsSolution solution = (OptimalDistributionStepsSolution)scoreDirector.getWorkingSolution();
        solution.establishStepOrdering();
    }

    @Override
    public void beforeVariableChanged(ScoreDirector scoreDirector, Migration entity) {
        /* not important for us */
    }

    /**
     * After any variable change, recompute the cluster situation during
     * migration steps.
     */
    @Override
    public void afterVariableChanged(ScoreDirector scoreDirector, Migration entity) {
        logger.trace("Variable changed in {} ({})", entity.toString(), entity.getStepsToFinish());
        OptimalDistributionStepsSolution solution = (OptimalDistributionStepsSolution)scoreDirector.getWorkingSolution();
        ClusterSituation situation = (ClusterSituation)solution;

        boolean stillOK = true;

        for (Migration m: solution.getSteps()) {
            if (entity == m) {
                stillOK = false;
            }

            if (!stillOK) {
                logger.trace("Recomputing shadow variables in {} ({})", m.toString(), m.getStepsToFinish());
                scoreDirector.beforeVariableChanged(m, "vmToHostAssignments");
                scoreDirector.beforeVariableChanged(m, "hostToVmAssignments");

                m.recomputeSituationAfter(situation);

                scoreDirector.afterVariableChanged(m, "vmToHostAssignments");
                scoreDirector.afterVariableChanged(m, "hostToVmAssignments");
            }

            situation = m;
        }
    }

    @Override
    public void beforeEntityRemoved(ScoreDirector scoreDirector, Migration entity) {
    }

    @Override
    public void afterEntityRemoved(ScoreDirector scoreDirector, Migration entity) {
        // recompute the last step flag
        OptimalDistributionStepsSolution solution = (OptimalDistributionStepsSolution)scoreDirector.getWorkingSolution();
        solution.establishStepOrdering();
    }
}
