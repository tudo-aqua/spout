package tools.aqua.concolic;

public class Assumption extends TraceElement {

    private final Expression condition;

    private final boolean satisfied;

    public Assumption(Expression condition, boolean sat) {
        this.condition = condition;
        this.satisfied = sat;
    }

    @Override
    public String toString() {
        return "[ASSUMPTION] (assert " + condition + ") // sat=" + satisfied;
    }
}
