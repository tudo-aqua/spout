package tools.aqua.concolic;

public class PrimitiveExpression implements Expression {

    private final PrimitiveTypes returnType;

    public PrimitiveExpression(PrimitiveTypes returnType) {
        this.returnType = returnType;
    }

    public PrimitiveTypes getType() {
        return returnType;
    }
}
