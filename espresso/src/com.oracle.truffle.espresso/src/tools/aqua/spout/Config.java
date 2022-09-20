/*
 * Copyright (c) 2021 Automated Quality Assurance Group, TU Dortmund University.
 * All rights reserved. DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE
 * HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact the Automated Quality Assurance Group, TU Dortmund University
 * or visit https://aqua.engineering if you need additional information or have any
 * questions.
 */

package tools.aqua.spout;

import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.ConcolicAnalysis;
import tools.aqua.smt.Expression;
import tools.aqua.smt.OperatorComparator;
import tools.aqua.smt.Types;
import tools.aqua.smt.Variable;
import tools.aqua.taint.TaintAnalysis;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;


public class Config {

    public enum TaintType {OFF, DATA, CONTROL, INFORMATION};

    private boolean hasConcolicAnalysis = false;

    private TaintType taintType = TaintType.OFF;

    private final Trace trace;

    private ConcolicAnalysis concolicAnalysis = null;

    private TaintAnalysis taintAnalysis = null;

    private int concolicIdx = 0;

    private int taintIdx = 1;

    private int annotationLength = 2;

    public Config(String config) {
        this.trace = new Trace();
        parseConfig(config);
    }

    void configureAnalysis() {
        if (hasConcolicAnalysis) {
            this.concolicAnalysis = new ConcolicAnalysis(this);
        }
        else {
            this.concolicAnalysis = null;
            this.concolicIdx = -1;
            this.taintIdx = 0;
            this.annotationLength--;
        }

        if (!taintType.equals(TaintType.OFF)) {
            this.taintAnalysis = new TaintAnalysis(this);
        }
        else {
            this.taintAnalysis = null;
            this.taintIdx = -1;
            this.annotationLength--;
        }

        Annotations.configure(this.annotationLength);
        // native image precautions ...
        OperatorComparator.initialize();

        SPouT.log("Concolic Analysis: " + hasConcolicAnalysis);
        SPouT.log("Taint Analysis: " + taintType);
        SPouT.log("Seeded Bool Values: " + Arrays.toString(seedsBooleanValues));
        SPouT.log("Seeded Byte Values: " + Arrays.toString(seedsByteValues));
        SPouT.log("Seeded Char Values: " + Arrays.toString(seedsCharValues));
        SPouT.log("Seeded Short Values: " + Arrays.toString(seedsShortValues));
        SPouT.log("Seeded Int Values: " + Arrays.toString(seedsIntValues));
        SPouT.log("Seeded Long Values: " + Arrays.toString(seedsLongValues));
        SPouT.log("Seeded Float Values: " + Arrays.toString(seedsFloatValues));
        SPouT.log("Seeded Double Values: " + Arrays.toString(seedsDoubleValues));
        SPouT.log("Seeded String Values: " + Arrays.toString(seedStringValues));
    }

    private void parseConfig(String config) {
        if (config.trim().length() < 1) {
            return;
        }
        String[] paramsGroups = config.trim().split(" "); // not in base64
        for (String paramGroup : paramsGroups) {
            String[] keyValue = paramGroup.split(":"); // not in base64
            boolean b64 = false;
            String paramList = keyValue[1].trim();
            if (paramList.startsWith("[b64]")) {
                paramList = paramList.substring("[b64]".length());
                b64 = true;
            }
            String[] vals = splitVals(paramList);
            switch (keyValue[0]) {
                case "concolic.bools":
                    parseBools(vals, b64);
                    break;
                case "concolic.bytes":
                    parseBytes(vals, b64);
                    break;
                case "concolic.chars":
                    parseChars(vals, b64);
                    break;
                case "concolic.shorts":
                    parseShorts(vals, b64);
                    break;
                case "concolic.ints":
                    parseInts(vals, b64);
                    break;
                case "concolic.longs":
                    parseLongs(vals, b64);
                    break;
                case "concolic.floats":
                    parseFloats(vals, b64);
                    break;
                case "concolic.doubles":
                    parseDoubles(vals, b64);
                    break;
                case "concolic.strings":
                    parseStrings(vals, b64);
                    break;
                case "concolic.execution":
                    parseConcolic(vals);
                    break;
                case "taint.flow":
                    parseTaint(vals);
                    break;

            }
        }
    }

    private static String[] splitVals(String config) {
        String[] valsAsStr;
        if (config.trim().length() > 0) {
            valsAsStr = config.split(",");
        } else {
            valsAsStr = new String[] {};
        }
        return valsAsStr;
    }

    private static String b64decode(String str) {
        byte[] in = str.getBytes(StandardCharsets.UTF_8);
        byte[] out = Base64.getDecoder().decode(in);
        return new String(out);
    }

    private void parseBools(String[] valsAsStr, boolean b64) {
        seedsBooleanValues = new boolean[valsAsStr.length];
        for (int i = 0; i < valsAsStr.length; i++) {
            seedsBooleanValues[i] =
                    Boolean.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
        }
    }

    private void parseBytes(String[] valsAsStr, boolean b64) {
        seedsByteValues = new int[valsAsStr.length];
        for (int i = 0; i < valsAsStr.length; i++) {
            seedsByteValues[i] =
                    Integer.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
        }
    }

    private void parseChars(String[] valsAsStr, boolean b64) {
        seedsCharValues = new int[valsAsStr.length];
        for (int i = 0; i < valsAsStr.length; i++) {
            seedsCharValues[i] =
                    Integer.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
        }
    }

    private void parseShorts(String[] valsAsStr, boolean b64) {
        seedsShortValues = new int[valsAsStr.length];
        for (int i = 0; i < valsAsStr.length; i++) {
            seedsShortValues[i] =
                    Integer.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
        }
    }

    private void parseInts(String[] valsAsStr, boolean b64) {
        seedsIntValues = new int[valsAsStr.length];
        for (int i = 0; i < valsAsStr.length; i++) {
            seedsIntValues[i] =
                    Integer.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
        }
    }

    private void parseLongs(String[] valsAsStr, boolean b64) {
        seedsLongValues = new long[valsAsStr.length];
        for (int i = 0; i < valsAsStr.length; i++) {
            seedsLongValues[i] = Long.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
        }
    }

    private void parseFloats(String[] valsAsStr, boolean b64) {
        seedsFloatValues = new float[valsAsStr.length];
        for (int i = 0; i < valsAsStr.length; i++) {
            seedsFloatValues[i] =
                    Float.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
        }
    }

    private void parseDoubles(String[] valsAsStr, boolean b64) {
        seedsDoubleValues = new double[valsAsStr.length];
        for (int i = 0; i < valsAsStr.length; i++) {
            seedsDoubleValues[i] =
                    Double.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
        }
    }

    private void parseStrings(String[] valsAsStr, boolean b64) {
        seedStringValues = new String[valsAsStr.length];
        for (int i = 0; i < valsAsStr.length; i++) {
            seedStringValues[i] = b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim();
        }
    }

    private void parseConcolic(String[] valsAsStr) {
        hasConcolicAnalysis = Boolean.valueOf(valsAsStr[0].trim());
    }

    private void parseTaint(String[] valsAsStr) {
        taintType = TaintType.valueOf(valsAsStr[0].trim());
    }

    // --------------------------------------------------------------------------
    //
    // Section symbolic values

    private int[] seedsIntValues = new int[] {};
    private int countIntSeeds = 0;

    public AnnotatedValue nextSymbolicInt() {
        int concrete = 0;
        if (countIntSeeds < seedsIntValues.length) {
            concrete = seedsIntValues[countIntSeeds];
        }
        Variable symbolic = new Variable(Types.INT, countIntSeeds);
        Object[] annotations = new Object[annotationLength];
        annotations[concolicIdx] = symbolic;
        AnnotatedValue a = new AnnotatedValue(concrete, annotations);
        countIntSeeds++;
        return a;
    }

    public SymbolicStringValue nextSymbolicString(){
        String concrete = "";
        if(countStringSeeds < seedStringValues.length){
            concrete = seedStringValues[countStringSeeds];
        }
        Variable symbolic = new Variable(Types.STRING, countStringSeeds);
        countStringSeeds++;
        return new SymbolicStringValue(concrete, symbolic);
    }

    private boolean[] seedsBooleanValues = new boolean[] {};
    private int countBooleanSeeds = 0;
/*
    @CompilerDirectives.TruffleBoundary
    public AnnotatedValue nextSymbolicBoolean() {
        boolean concrete = false;
        if (countBooleanSeeds < seedsBooleanValues.length) {
            concrete = seedsBooleanValues[countBooleanSeeds];
        }
        Variable symbolic = new Variable(Types.BOOL, countBooleanSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        countBooleanSeeds++;
        Analysis.getInstance().getTrace().addElement(new SymbolDeclaration(symbolic));
        GWIT.trackLocationForWitness("" + concrete);
        return a;
    }
*/
    private int[] seedsByteValues = new int[] {};
    private int countByteSeeds = 0;
/*
    @CompilerDirectives.TruffleBoundary
    public AnnotatedValue nextSymbolicByte() {
        int concrete = 0;
        if (countByteSeeds < seedsByteValues.length) {
            concrete = seedsByteValues[countByteSeeds];
        }
        Variable symbolic = new Variable(Types.BYTE, countByteSeeds);
        countByteSeeds++;
        Analysis.getInstance().getTrace().addElement(new SymbolDeclaration(symbolic));
        GWIT.trackLocationForWitness("" + concrete);
        // upcast byte to int
        return new AnnotatedValue(concrete, new ComplexExpression(OperatorComparator.B2I, symbolic));
    }
*/
    private int[] seedsCharValues = new int[] {};
    private int countCharSeeds = 0;
/*
    @CompilerDirectives.TruffleBoundary
    public AnnotatedValue nextSymbolicChar() {
        int concrete = 0;
        if (countCharSeeds < seedsCharValues.length) {
            concrete = seedsCharValues[countCharSeeds];
        }
        Variable symbolic = new Variable(Types.CHAR, countCharSeeds);
        countCharSeeds++;
        Analysis.getInstance().getTrace().addElement(new SymbolDeclaration(symbolic));
        GWIT.trackLocationForWitness("" + concrete);
        // upcast char to int
        return new AnnotatedValue(concrete, new ComplexExpression(OperatorComparator.C2I, symbolic));
    }
*/
    private int[] seedsShortValues = new int[] {};
    private int countShortSeeds = 0;
/*
    @CompilerDirectives.TruffleBoundary
    public AnnotatedValue nextSymbolicShort() {
        int concrete = 0;
        if (countShortSeeds < seedsShortValues.length) {
            concrete = seedsShortValues[countShortSeeds];
        }
        Variable symbolic = new Variable(Types.SHORT, countShortSeeds);
        countShortSeeds++;
        Analysis.getInstance().getTrace().addElement(new SymbolDeclaration(symbolic));
        GWIT.trackLocationForWitness("" + concrete);
        // upcast short to int
        return new AnnotatedValue(concrete, new ComplexExpression(OperatorComparator.S2I, symbolic));
    }
*/
    private long[] seedsLongValues = new long[] {};
    private int countLongSeeds = 0;
/*
    @CompilerDirectives.TruffleBoundary
    public AnnotatedValue nextSymbolicLong() {
        long concrete = 0L;
        if (countLongSeeds < seedsLongValues.length) {
            concrete = seedsLongValues[countLongSeeds];
        }
        Variable symbolic = new Variable(Types.LONG, countLongSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        countLongSeeds++;
        Analysis.getInstance().getTrace().addElement(new SymbolDeclaration(symbolic));
        GWIT.trackLocationForWitness("" + concrete + "L");
        return a;
    }
*/
    private float[] seedsFloatValues = new float[] {};
    private int countFloatSeeds = 0;
/*
    @CompilerDirectives.TruffleBoundary
    public AnnotatedValue nextSymbolicFloat() {
        float concrete = 0f;
        if (countFloatSeeds < seedsFloatValues.length) {
            concrete = seedsFloatValues[countFloatSeeds];
        }
        Variable symbolic = new Variable(Types.FLOAT, countFloatSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        countFloatSeeds++;
        Analysis.getInstance().getTrace().addElement(new SymbolDeclaration(symbolic));
        GWIT.trackLocationForWitness("Float.parseFloat(\"" + concrete + "\")");
        return a;
    }
*/
    private  double[] seedsDoubleValues = new double[] {};
    private int countDoubleSeeds = 0;
/*
    @CompilerDirectives.TruffleBoundary
    public AnnotatedValue nextSymbolicDouble() {
        double concrete = 0d;
        if (countDoubleSeeds < seedsDoubleValues.length) {
            concrete = seedsDoubleValues[countDoubleSeeds];
        }
        Variable symbolic = new Variable(Types.DOUBLE, countDoubleSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        countDoubleSeeds++;
        Analysis.getInstance().getTrace().addElement(new SymbolDeclaration(symbolic));
        GWIT.trackLocationForWitness("Double.parseDouble(\"" + concrete + "\")");
        return a;
    }
*/
    private String[] seedStringValues = new String[] {};
    private int countStringSeeds = 0;
/*
    @CompilerDirectives.TruffleBoundary
    public StaticObject nextSymbolicString(Meta meta) {
        String concreteHost = "";
        if (countStringSeeds < seedStringValues.length) {
            concreteHost = seedStringValues[countStringSeeds];
        }
        StaticObject concrete = meta.toGuestString(concreteHost);
        Variable symbolic = new Variable(Types.STRING, countStringSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        AnnotatedValue length =
                new AnnotatedValue(
                        concreteHost.length(),
                        new ComplexExpression(
                                OperatorComparator.NAT2BV32, new ComplexExpression(SLENGTH, symbolic)));

        //concrete.setAnnotationId(symbolicObjects.size());

        int lengthAnnotations = ((ObjectKlass) concrete.getKlass()).getFieldTable().length + 2;
        AnnotatedValue[] annotation = new AnnotatedValue[lengthAnnotations];
        annotation[annotation.length - 2] = a;
        annotation[annotation.length - 1] = length;
        //symbolicObjects.add(annotation);
        Analysis.getInstance().getAnnotatedVM().registerAnnotation(concrete, annotation);
        countStringSeeds++;
        Analysis.getInstance().getTrace().addElement(new SymbolDeclaration(symbolic));
        GWIT.trackLocationForWitness("\"" + concrete + "\"");
        return concrete;
    }
    */

    public int getConcolicIdx() {
        return concolicIdx;
    }

    public int getTaintIdx() {
        return taintIdx;
    }

    public TaintType getTaintType() {
        return taintType;
    }

    public boolean hasConcolicAnalysis() {
        return hasConcolicAnalysis;
    }

    public boolean hasTaintAnalysis() {
        return !taintType.equals(TaintType.OFF);
    }

    public boolean analyzeControlFlowTaint() {
        return taintType.equals(TaintType.CONTROL) || taintType.equals(TaintType.INFORMATION);
    }

    public Trace getTrace() {
        return trace;
    }

    public ConcolicAnalysis getConcolicAnalysis() {
        return concolicAnalysis;
    }

    public TaintAnalysis getTaintAnalysis() {
        return taintAnalysis;
    }

    public Analysis<?>[] getAnalyses() {
        Analysis<?>[] analyses = new Analysis<?>[this.annotationLength];
        if (hasConcolicAnalysis()) analyses[this.concolicIdx] = this.concolicAnalysis;
        if (hasTaintAnalysis()) analyses[this.taintIdx] = this.taintAnalysis;
        return analyses;
    }

    // This should be a record, but SPouT cannot compile records yet.
    public class SymbolicStringValue{
        public String concrete;
        public Variable symbolic;
        public SymbolicStringValue(String c, Variable s){
            concrete = c;
            symbolic = s;
        }
    };
}
