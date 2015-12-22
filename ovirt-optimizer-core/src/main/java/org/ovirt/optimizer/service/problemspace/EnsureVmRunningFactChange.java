package org.ovirt.optimizer.service.problemspace;

import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.impl.solver.ProblemFactChange;
import org.ovirt.optimizer.service.facts.RunningVm;

/**
 * This Problem Fact Change routine adds a new fact that will
 * ensure some VM is running.
 *
 * The fact will be added to fixed facts only if the rule is
 * not there already.
 */
public class EnsureVmRunningFactChange implements ProblemFactChange {
    final String uuid;

    public EnsureVmRunningFactChange(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public void doChange(ScoreDirector scoreDirector) {
        OptimalDistributionStepsSolution space = (OptimalDistributionStepsSolution) scoreDirector.getWorkingSolution();

        for (Object fact: space.getFixedFacts()) {
            if (fact instanceof RunningVm
                    && ((RunningVm)fact).getId().equals(uuid)) {
                return;
            }
        }

        RunningVm fact = new RunningVm(uuid);

        scoreDirector.beforeProblemFactAdded(fact);
        space.getFixedFacts().add(fact);
        scoreDirector.afterProblemFactAdded(fact);

        /* Required since Optaplanner 6.3.0 */
        scoreDirector.triggerVariableListeners();
    }
}
