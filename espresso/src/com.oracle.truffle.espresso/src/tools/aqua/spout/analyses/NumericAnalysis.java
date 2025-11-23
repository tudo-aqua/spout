package tools.aqua.spout.analyses;

import tools.aqua.spout.Analysis;

public interface NumericAnalysis<T> extends Analysis<T> {

    default T toString(byte b, T a1) {return null;}
    default T byteToShort(byte b, T a1) {return null;}
     default T byteToLong(byte b, T a1) {return null;}
    default T doubleToShort(double d, T a1) {return null;}
    default T longToDouble(long d, T a1) {return null;}
    default T longToFloat(long d, T a1) {return null;}
}
