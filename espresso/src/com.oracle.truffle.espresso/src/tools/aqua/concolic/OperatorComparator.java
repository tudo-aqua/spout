package tools.aqua.concolic;

public enum OperatorComparator {
    // Integer (maybe all numeric types?)
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
    NE,
    // Unary
    INEG,
    BNEG,
    // String
    STRINGEQ,
    STRINGNE,
    SCONCAT,
    SSUBSTR,
    SAT,
    STOSTR,
    SREPLACE,
    SREPLACEALL,
    STOLOWER,
    STOUPPER,
    SLENGTH,
    SINDEXOF,
    STOINT
    ;

    @Override
    public String toString() {
        switch(this) {
            case IADD:
                return "+";
            case ISUB:
                return "-";
            case IMUL:
                return "*";
            case IDIV:
                return "/";
            case IREM:
                return "%";
            case ISHL:
                return "<<";
            case ISHR:
                return ">>";
            case IUSHR:
                return ">>>";
            case IAND:
                return "&";
            case IOR:
                return "|";
            case IXOR:
                return "^";

            case INEG:
                return "-";
            case BNEG:
                return "not";

            case EQ:
            case STRINGEQ:
                return "=";
            case NE:
            case STRINGNE:
                return "!=";
            case GT:
                return ">";
            case GE:
                return ">=";
            case LT:
                return "<";
            case LE:
                return "<=";

            case SLENGTH:
                return "str.len";
            case SINDEXOF:
                return "str.indexof";
            case STOINT:
                return "str.to.int";

            default:
                return super.toString();
        }
    }


}
