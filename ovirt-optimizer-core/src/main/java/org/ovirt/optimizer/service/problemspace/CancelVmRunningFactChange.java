package org.ovirt.optimizer.service.problemspace;

import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.impl.solver.ProblemFactChange;
import org.ovirt.optimizer.service.facts.RunningVm;

import java.util.Iterator;

/**
 * This Problem Fact Change routine removes facts that will
 * ensure some VM is running.
 */
public class CancelVmRunningFactChange implements ProblemFactChange {
    final String uuid;

    public CancelVmRunningFactChange(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public void doChange(ScoreDirector scoreDirector) {
        OptimalDistributionStepsSolution space = (OptimalDistributionStepsSolution) scoreDirector.getWorkingSolution();

        for (Iterator<Object> i = space.getFixedFacts().iterator(); i.hasNext(); ) {
            Object fact = i.next();
            if (fact instanceof RunningVm
                    && ((RunningVm)fact).getId().equals(uuid)) {
                scoreDirector.beforeProblemFactRemoved(fact);
                i.remove();
                scoreDirector.afterProblemFactRemoved(fact);
            }
        }
    }
}
