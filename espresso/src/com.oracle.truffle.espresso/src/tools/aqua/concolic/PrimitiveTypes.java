package tools.aqua.concolic;

public enum PrimitiveTypes {
    INT, BOOL, CHAR, BYTE, SHORT, LONG, FLOAT, DOUBLE, STRING;

    @Override
    public String toString() {
        switch(this) {
            case BOOL:
                return "Bool";
            case BYTE:
                return "(_ BitVec 8)";
            case CHAR:
            case SHORT:
                return "(_ BitVec 16)";
            case INT:
                return "(_ BitVec 32)";
            case LONG:
                return "(_ BitVec 64)";
            case FLOAT:
                return "(Float32 0)";
            case DOUBLE:
                return "(Float64 0)";
            case STRING:
                return "String";
            default:
                return super.toString();
        }
    }
}
