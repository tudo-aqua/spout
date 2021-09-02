package tools.aqua.concolic;

public class UnaryPrimitiveExpression extends PrimitiveExpression{

    public enum UnaryPrimitiveOperator {
        INEG
    }

    private final UnaryPrimitiveExpression.UnaryPrimitiveOperator operator;

    private final Expression inner;

    UnaryPrimitiveExpression(PrimitiveTypes returnType, UnaryPrimitiveExpression.UnaryPrimitiveOperator op, Expression inner) {
        super(returnType);
        this.operator = op;
        this.inner = inner;
    }

    @Override
    public String toString() {
        return "BinaryPrimitiveExpression{" +
                "operator=" + operator +
                ", inner=" + inner +
                '}';
    }
}
