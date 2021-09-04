package tools.aqua.concolic;

public class PathCondition extends TraceElement {

    private final Expression condition;

    private final int branchCount;

    private final int branchId;

    public PathCondition(Expression condition, int branchId, int branchCount) {
        this.condition = condition;
        this.branchId = branchId;
        this.branchCount = branchCount;
    }

    @Override
    public String toString() {
        return "[DECISION] (assert " + condition + ")" +
                " // branchCount=" + branchCount +
                ", branchId=" + branchId;
    }
}
