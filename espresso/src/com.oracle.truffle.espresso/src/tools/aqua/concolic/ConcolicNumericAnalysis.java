package tools.aqua.concolic;

import tools.aqua.smt.ComplexExpression;
import tools.aqua.smt.Constant;
import tools.aqua.smt.Expression;
import tools.aqua.smt.OperatorComparator;
import tools.aqua.spout.SPouT;
import tools.aqua.spout.analyses.NumericAnalysis;

import static tools.aqua.smt.OperatorComparator.I2L;
import static tools.aqua.smt.OperatorComparator.I2S;

public class ConcolicNumericAnalysis implements NumericAnalysis<Expression> {

    @Override
    public Expression byteToLong(byte b, Expression a1) {
        return new ComplexExpression(I2L, a1);
    }

    @Override
    public Expression byteToShort(byte b, Expression a1) {
        SPouT.debug("byte to short check sign extend", a1);
        return new ComplexExpression(I2S, a1);
    }

    @Override
    public Expression doubleToShort(double d, Expression a1) {
        ComplexExpression intMinAsFloat =
                new ComplexExpression(OperatorComparator.I2D_RTZ, Constant.INT_MIN);
        ComplexExpression intMaxAsFloat =
                new ComplexExpression(OperatorComparator.I2D_RTZ, Constant.INT_MAX);
        ComplexExpression rtz = new ComplexExpression(OperatorComparator.D2I_RTZ, a1);
        SPouT.debug("Generating double to Short Expression", a1);
        return new ComplexExpression(I2S, ConcolicAnalysis.getExpressionInt(a1, intMinAsFloat, intMaxAsFloat, rtz));
    }
}
