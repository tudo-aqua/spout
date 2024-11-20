package tools.aqua.concolic;

import tools.aqua.smt.ComplexExpression;
import tools.aqua.smt.Expression;
import tools.aqua.spout.TraceElement;

public class SymTaint extends TraceElement {

    private final Expression condition;

    public SymTaint(Expression condition) {
        this.condition = condition;
    }

    public Expression getCondition() {
        return condition;
    }

    @Override
    public String toString() {
        return "[SYMTAINT] (assert " + condition + ")";
    }

}
