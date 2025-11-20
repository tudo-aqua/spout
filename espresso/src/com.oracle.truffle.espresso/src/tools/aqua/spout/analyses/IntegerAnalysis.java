package tools.aqua.spout.analyses;

import tools.aqua.spout.Analysis;

public interface IntegerAnalysis<T> extends Analysis<T> {

    default T toString(byte b, T a1) {return null;}
}
