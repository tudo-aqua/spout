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
            case BOOL:
                return "__bool_" + this.id;
            case BYTE:
                return "__byte_" + this.id;
            case CHAR:
                return "__char_" + this.id;
            case SHORT:
                return "__short_" + this.id;
            case INT:
                return "__int_" + this.id;
            case LONG:
                return "__long_" + this.id;
            case FLOAT:
                return "__float_" + this.id;
            case DOUBLE:
                return "__double_" + this.id;
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
