package tools.aqua.spout.analyses;

import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.Analysis;
import tools.aqua.spout.Annotations;
import tools.aqua.spout.Config;
import tools.aqua.spout.MetaAnalysis;

public class MetaNumericAnalysis implements NumericAnalysis<Annotations> {
    private final NumericAnalysis<?>[] numericAnalyses;
    private final Config cfg;

    public MetaNumericAnalysis(Config config) {
        cfg = (config);
        numericAnalyses = config.getNumericAnalyses();
    }

    interface BinaryOperation {
        <T> T execute(NumericAnalysis<T> analysis, int c1, int c2, T s1, T s2);
    }
    interface BinaryCharOperation {
        <T> T execute(NumericAnalysis<T> analysis, char c1, char c2, T s1, T s2);
    }

    interface BinaryStringCompOperation {
        <T> T execute(NumericAnalysis<T> analysis, boolean b1, T s1, T s2);
    }


    interface BinaryStringOperation {
        <T> T execute(NumericAnalysis<T> analysis, String self, String other, T s1, T s2);
    }

    interface TwoStringsOneIntOperation{
        <T> T execute(NumericAnalysis<T> analysis, String c1, String c2, int c3, T s1, T s2, T s3);
    }

    interface BinaryStringIntOperation {
        <T> T execute(NumericAnalysis<T> analysis, String self, int other, T s1, T s2);
    }

    interface StringSubstringOperation {
        <T> T execute(NumericAnalysis<T> analysis, boolean success, String self, int start, int end, T s1, T s2, T s3);
    }

    interface BinaryLongOperation {
        <T> T execute(NumericAnalysis<T> analysis, long c1, long c2, T s1, T s2);
    }

    interface BinaryLongShiftOperation {
        <T> T execute(NumericAnalysis<T> analysis, int c1, long c2, T s1, T s2);
    }

    interface BinaryFloatOperation {
        <T> T execute(NumericAnalysis<T> analysis, float c1, float c2, T s1, T s2);
    }

    interface BinaryDoubleOperation {
        <T> T execute(NumericAnalysis<T> analysis, double c1, double c2, T s1, T s2);
    }

    interface UnaryByteOperation {
        <T> T execute(NumericAnalysis<T> analysis, byte c1, T s1);
    }

    interface UnaryCharOperation {
        <T> T execute(NumericAnalysis<T> analysis, char c1, T s1);
    }

    interface UnaryShortOperation {
        <T> T execute(NumericAnalysis<T> analysis, short c1, T s1);
    }

    interface UnaryOperation {
        <T> T execute(NumericAnalysis<T> analysis, int c1, T s1);
    }

    interface UnaryLongOperation {
        <T> T execute(NumericAnalysis<T> analysis, long c1, T s1);
    }

    interface UnaryFloatOperation {
        <T> T execute(NumericAnalysis<T> analysis, float c1, T s1);
    }

    interface UnaryDoubleOperation {
        <T> T execute(NumericAnalysis<T> analysis, double c1, T s1);
    }

    interface UnaryStringOperation {
        <T> T execute(NumericAnalysis<T> analysis, String c1, T s1);
    }

    interface UnaryObjectOperation {
        <T> T execute(NumericAnalysis<T> analysis, StaticObject o1, T s1, boolean branch);
    }

    interface BinaryObjectOperation {
        <T> T execute(NumericAnalysis<T> analysis, StaticObject o1, StaticObject o2, T s1, T s2);
    }

//    protected Annotations oexecute(StaticObject c1, StaticObject c2, Annotations a1, Annotations a2, BinaryObjectOperation executor) {
//        return oexecute(c1, c2, a1, a2, executor);
//    }
//
//    protected Annotations oexecute(StaticObject o, Annotations a, boolean branch, UnaryObjectOperation executor) {
//        return oexecute(o, a, branch, executor);
//    }
//
//    protected Annotations sexecute(char c1, char c2, Annotations a1, Annotations a2, BinaryCharOperation executor) {
//        return sexecute(c1, c2, a1, a2, executor);
//    }
//
//    protected Annotations sexecute(char c1, Annotations a1, UnaryCharOperation executor) {
//        return sexecute(c1, a1, executor);
//    }
//
//    protected Annotations sexecute(String self, String other, int index, Annotations a1, Annotations a2, Annotations a3, TwoStringsOneIntOperation executor) {
//        return sexecute(self, other, index, a1, a2, a3, executor);
//    }
//
//    protected Annotations sexecute(String self, Annotations a1, UnaryStringOperation executor) {
//        return sexecute(self, a1, executor);
//    }
//
//    protected Annotations sexecute(boolean success, String self, int index, int end, Annotations a1, Annotations a2, Annotations a3, StringSubstringOperation executor) {
//        return sexecute(success, self, index, end, a1, a2, a3, executor);
//    }
//
//    protected Annotations sexecute(String self, int index, Annotations a1, Annotations a2, BinaryStringIntOperation executor) {
//        return sexecute(self, index, a1, a2, executor);
//    }
//
//    protected Annotations sexecute(boolean comp, Annotations a1, Annotations a2, BinaryStringCompOperation executor) {
//        return sexecute(comp, a1, a2, executor);
//    }
//
    protected Annotations dexecute(double c1, Annotations a1, UnaryDoubleOperation executor) {
        int i = 0;
        boolean hasResult = false;
        Object[] annotations = new Object[numericAnalyses.length];
        for (NumericAnalysis<?> analysis : numericAnalyses) {
            Object result = executor.execute(analysis, c1, Annotations.annotation(a1, i));
            if (result != null) {
                annotations[i] = result;
                hasResult = true;
            }
            i++;
        }
        return hasResult ? Annotations.create(annotations) : null;
    }
//
//    protected Annotations fexecute(float c1, Annotations a1, UnaryFloatOperation executor) {
//        return fexecute(c1, a1, executor);
//    }
//
//
//    protected Annotations lexecute(long c1, Annotations a1, UnaryLongOperation executor) {
//        return lexecute(c1, a1, executor);
//    }
//
//
//    protected Annotations execute(int c1, Annotations a1, UnaryOperation executor) {
//        return execute(c1, a1, executor);
//    }
//
//
//    protected Annotations sexecute(String self, String other, Annotations a1, Annotations a2, BinaryStringOperation executor) {
//        return sexecute(self, other, a1, a2, executor);
//    }
//
//
//    protected Annotations sexecute(short c1, Annotations a1, UnaryShortOperation executor) {
//        return sexecute(c1, a1, executor);
//    }
//
//
//    protected Annotations cexecute(char c1, Annotations a1, UnaryCharOperation executor) {
//        return cexecute(c1, a1, executor);
//    }

    protected Annotations bexecute(byte c1, Annotations a1, UnaryByteOperation executor) {
        int i = 0;
        boolean hasResult = false;
        Object[] annotations = new Object[numericAnalyses.length];
        for (NumericAnalysis<?> analysis : numericAnalyses) {
            Object result = executor.execute(analysis, c1, Annotations.annotation(a1, i));
            if (result != null) {
                annotations[i] = result;
                hasResult = true;
            }
            i++;
        }
        return hasResult ? Annotations.create(annotations) : null;
    }
//
//    protected Annotations dexecute(double c1, double c2, Annotations a1, Annotations a2, BinaryDoubleOperation executor) {
//        return dexecute(c1, c2, a1, a2, executor);
//    }
//
//    protected Annotations fexecute(float c1, float c2, Annotations a1, Annotations a2, BinaryFloatOperation executor) {
//        return fexecute(c1, c2, a1, a2, executor);
//    }
//
//
//    protected Annotations lShiftExecute(int c1, long c2, Annotations a1, Annotations a2, BinaryLongShiftOperation executor) {
//        return lShiftExecute(c1, c2, a1, a2, executor);
//    }
//
//    protected Annotations lexecute(long c1, long c2, Annotations a1, Annotations a2, BinaryLongOperation executor) {
//        return lexecute(c1, c2, a1, a2, executor);
//    }
//
//    protected Annotations execute(int c1, int c2, Annotations a1, Annotations a2, BinaryOperation executor) {
//        return execute(c1, c2, a1, a2, executor);
//    }

    @Override
    public Annotations byteToLong(byte b, Annotations a1) {
        return bexecute(b, a1, NumericAnalysis::byteToLong);
    }

    @Override
    public Annotations byteToShort(byte b, Annotations a1) {
        return bexecute(b, a1, NumericAnalysis::byteToShort);
    }

    @Override
    public Annotations doubleToShort(double d, Annotations a1) {
        return dexecute(d, a1, NumericAnalysis::doubleToShort);
    }
}
