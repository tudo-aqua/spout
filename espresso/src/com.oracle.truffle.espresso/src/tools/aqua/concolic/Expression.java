package tools.aqua.concolic;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.EspressoError;

public interface Expression {

    static boolean isBoolean(Expression e) {
        if (e instanceof Atom) {
            Atom a = (Atom) e;
            return a.getType().equals(PrimitiveTypes.BOOL);
        }
        else if (e instanceof ComplexExpression) {
            ComplexExpression c = (ComplexExpression) e;
            return c.getOperator().isBoolean();
        }
        else {
            return false;
        }
    }

    static boolean isCmpExpression(Expression e) {
        if (e instanceof ComplexExpression) {
            ComplexExpression c = (ComplexExpression) e;
            return c.getOperator().isCmp();
        }
        else {
            return false;
        }
    }

}
