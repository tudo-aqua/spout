package tools.aqua.concolic;

public class Atom implements Expression {

    private final PrimitiveTypes type;

    public Atom(PrimitiveTypes type) {
        this.type = type;
    }

    public PrimitiveTypes getType() {
        return type;
    }
}
