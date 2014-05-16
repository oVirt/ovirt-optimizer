package org.ovirt.optimizer.util;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.phase.custom.CustomPhaseCommand;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.slf4j.Logger;

public class ScoreOnlyPhase implements CustomPhaseCommand {
    static private final Logger logger = org.slf4j.LoggerFactory.getLogger(ScoreOnlyPhase.class);

    @Override
    public void changeWorkingSolution(ScoreDirector scoreDirector) {
        /* empty phase, just calculate score */
        Score score = scoreDirector.calculateScore();
        scoreDirector.getWorkingSolution().setScore(score);
        logger.debug(scoreDirector.getWorkingSolution().getScore().toString());
    }
}
