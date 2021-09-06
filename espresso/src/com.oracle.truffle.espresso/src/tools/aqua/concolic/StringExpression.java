package tools.aqua.concolic;

import java.util.Arrays;
import java.util.Iterator;

public class StringExpression implements Expression {

    public enum StringOperator {
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
        STOINT;

        @Override
        public String toString() {
            switch (this) {
                case STRINGEQ:
                    return "=";
                case STRINGNE:
                    return "!=";
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

    private final StringOperator operator;

    private final Expression[] subExpressions;

    public StringExpression(StringOperator operator, Expression ... subExpressions) {
        this.operator = operator;
        this.subExpressions = subExpressions;
    }

    @Override
    public String toString() {
        return "(" + operator + " " + String.join(" ", new Iterable<CharSequence>() {
            @Override
            public Iterator<CharSequence> iterator() {
                return new Iterator<CharSequence>() {
                    int i = 0;
                    @Override
                    public boolean hasNext() {
                        return i < subExpressions.length;
                    }
                    @Override
                    public CharSequence next() {
                        return subExpressions[i++].toString();
                    }
                };
            }
        }) + ")";
    }
}
