package tools.aqua.concolic;

import com.oracle.truffle.espresso.runtime.StaticObject;

public interface Expression {

    public static BinaryPrimitiveExpression intOp(BinaryPrimitiveExpression.BinaryPrimitiveOperator op, Expression left, Expression right) {
        assert left instanceof PrimitiveExpression;
        assert right instanceof PrimitiveExpression;
        return new BinaryPrimitiveExpression(PrimitiveTypes.INT, op, left, right);
    }

    public static UnaryPrimitiveExpression unaryIntOp(UnaryPrimitiveExpression.UnaryPrimitiveOperator op, Expression inner) {
        assert inner instanceof PrimitiveExpression;
        return new UnaryPrimitiveExpression(PrimitiveTypes.INT, op, inner);
    }

    public static BinaryPrimitiveExpression intComp(BinaryPrimitiveExpression.BinaryPrimitiveOperator comp, Expression left, Expression right) {
        assert left instanceof PrimitiveExpression;
        assert right instanceof PrimitiveExpression;
        return new BinaryPrimitiveExpression(PrimitiveTypes.BOOL, comp, left, right);
    }

}
