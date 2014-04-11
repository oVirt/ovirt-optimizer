package org.ovirt.optimizer.service.problemspace;

import org.optaplanner.core.impl.heuristic.selector.move.factory.MoveListFactory;
import org.optaplanner.core.impl.move.Move;
import org.optaplanner.core.impl.move.NoChangeMove;
import org.optaplanner.core.impl.solution.Solution;

import java.util.ArrayList;
import java.util.List;

/**
 * This class serves as workaround for https://issues.jboss.org/browse/PLANNER-235
 */
public class DummyMoveListFactory implements MoveListFactory {
    @Override
    public List<? extends Move> createMoveList(Solution solution) {
        List<Move> moveList = new ArrayList<>();
        moveList.add(new NoChangeMove());
        return moveList;
    }
}
