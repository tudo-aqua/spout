package tools.aqua.concolic;

public enum PrimitiveTypes {
    INT, BOOL, CHAR, BYTE, SHORT, LONG, FLOAT, DOUBLE;

    @Override
    public String toString() {
        switch(this) {
            case INT:
                return "Int";
            default:
                return super.toString();
        }
    }
}
