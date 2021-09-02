package tools.aqua.concolic;

public class BinaryPrimitiveExpression extends PrimitiveExpression {

    public enum BinaryPrimitiveOperator {
        IADD,
        ISUB,
        IMUL,
        IDIV,
        IREM,
        ISHR,
        ISHL,
        IUSHR,
        IAND,
        IOR,
        IXOR,
        GT,
        LT,
        GE,
        LE,
        EQ,
        NE;

        @Override
        public String toString() {
            switch(this) {
                case IADD:
                    return "bvadd";
                case EQ:
                    return "=";
                default:
                    return super.toString();
            }
        }
    }

    private final BinaryPrimitiveOperator operator;

    private final Expression left;

    private final Expression right;

    BinaryPrimitiveExpression(PrimitiveTypes returnType, BinaryPrimitiveOperator op,
                                      Expression left, Expression right) {
        super(returnType);
        this.operator = op;
        this.left = left;
        this.right = right;
    }

    @Override
    public String toString() {
        return "(" + operator + " " + left + " " + right + ")";
    }

}
