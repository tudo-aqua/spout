package tools.aqua.concolic;

import java.util.Iterator;

public class ComplexExpression implements Expression {

    private final OperatorComparator operator;

    private final Expression[] subExpressions;

    public ComplexExpression(OperatorComparator operator, Expression ... subExpressions) {
        this.operator = operator;
        this.subExpressions = subExpressions;
    }

    public OperatorComparator getOperator() {
        return operator;
    }

    public Expression[] getSubExpressions() {
        return subExpressions;
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
