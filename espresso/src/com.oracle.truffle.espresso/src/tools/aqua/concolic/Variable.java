package tools.aqua.concolic;

public class Variable extends PrimitiveExpression {

    private final int id;

    public Variable(PrimitiveTypes type, int id) {
        super(type);
        this.id = id;
    }

    @Override
    public String toString() {
        switch (this.getType()) {
            case INT:
                return "__int_" + this.id;
            default:
                return "Variable{" +
                        "id=" + id +
                        "id=" + id +
                        '}';
        }

    }
}
