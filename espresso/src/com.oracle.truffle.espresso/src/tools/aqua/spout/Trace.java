package tools.aqua.spout;

import com.oracle.truffle.api.CompilerDirectives;
import tools.aqua.concolic.PathCondition;
import tools.aqua.smt.Expression;

/**
 * functions for recording traces of executions
 */
public class Trace {

    private TraceElement traceHead = null;
    private TraceElement traceTail = null;

    Trace() {
    }

    @CompilerDirectives.TruffleBoundary
    public void addElement(TraceElement tNew) {
        // TODO: maybe this can be caught earlier?
        if (tNew instanceof PathCondition) {
            if (!Expression.isFormula( ((PathCondition) tNew).getCondition() )) {
                return;
            }
        }

        //ifLog(tNew.toString());
        if (traceHead == null) {
            traceHead = tNew;
        } else {
            traceTail.setNext(tNew);
        }
        traceTail = tNew;
    }

    /*
     * print trace to shell.
     */
    public void printTrace() {
        TraceElement cur = traceHead;
        while (cur != null) {
            System.out.println(cur);
            cur = cur.getNext();
        }
    }

}
