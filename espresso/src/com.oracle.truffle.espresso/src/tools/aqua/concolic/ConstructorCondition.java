package tools.aqua.concolic;

import tools.aqua.smt.Expression;
import tools.aqua.spout.TraceElement;

public class ConstructorCondition extends TraceElement {

    private final Expression condition;

    public ConstructorCondition(Expression condition) {
        this.condition = condition;
    }

    @Override
    public String toString() {
        return "[SUMMARY] " + condition;
    }
}
