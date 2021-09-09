package tools.aqua.concolic;

import java.util.EnumSet;

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
    INEG,
    // Boolean
    BNEG,
    BAND,
    BOR,
    BXOR,
    BEQUIV,
    BIMPLIES,
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
        //TODO: someone needs to check these!
        switch(this) {
            case IADD:
                return "bvadd";
            case ISUB:
                return "bvsub";
            case IMUL:
                return "bvmul";
            case IDIV:
                return "bvsdiv";
            case IREM:
                return "bvsrem";
            case ISHL:
                return "bvshl";
            case ISHR:
                return "bvashr";
            case IUSHR:
                return "bvlshr";
            case IAND:
                return "bvand";
            case IOR:
                return "bvor";
            case IXOR:
                return "bvxor";

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
                return "bvsgt";
            case GE:
                return "bvsge";
            case LT:
                return "bvslt";
            case LE:
                return "bvsle";

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

    static EnumSet<OperatorComparator> boolOps = EnumSet.of(EQ, STRINGEQ, NE, STRINGNE, LT, LE, GT, GE, BNEG, BAND, BOR, BXOR, BEQUIV, BIMPLIES);

    public boolean isBoolean() {
        return boolOps.contains(this);
    }
}
