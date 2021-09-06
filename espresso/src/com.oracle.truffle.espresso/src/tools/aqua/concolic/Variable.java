package tools.aqua.concolic;

public class Variable extends Atom {

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
            case STRING:
                return "__string_" + this.id;
            default:
                return "Variable{" +
                        "id=" + id +
                        "id=" + id +
                        '}';
        }

    }
}
