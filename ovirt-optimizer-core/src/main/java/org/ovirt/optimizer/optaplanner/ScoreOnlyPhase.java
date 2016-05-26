package org.ovirt.optimizer.optaplanner;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.phase.custom.AbstractCustomPhaseCommand;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.slf4j.Logger;

public class ScoreOnlyPhase extends AbstractCustomPhaseCommand {
    static private final Logger logger = org.slf4j.LoggerFactory.getLogger(ScoreOnlyPhase.class);

    @Override
    public void changeWorkingSolution(ScoreDirector scoreDirector) {
        /* empty phase, just calculate score */
        Score score = scoreDirector.calculateScore();
        scoreDirector.getWorkingSolution().setScore(score);
        logger.debug(scoreDirector.getWorkingSolution().getScore().toString());
    }
}
