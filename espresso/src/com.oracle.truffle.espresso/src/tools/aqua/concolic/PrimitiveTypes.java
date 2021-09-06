package tools.aqua.concolic;

public enum PrimitiveTypes {
    INT, BOOL, CHAR, BYTE, SHORT, LONG, FLOAT, DOUBLE, STRING;

    @Override
    public String toString() {
        switch(this) {
            case INT:
                return "(_ BitVec 32)";
            case STRING:
                return "String";
            default:
                return super.toString();
        }
    }
}
