package tools.aqua.spout;

import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;


public class Config {

    public enum TaintType {OFF, DATA, CONTROL, INFORMATION};

    private boolean concolicAnalysis = false;

    private TaintType taintAnalysis = TaintType.OFF;

    public TaintType getTaintAnalysis() {
        return taintAnalysis;
    }

    public boolean isConcolicAnalysis() {
        return concolicAnalysis;
    }

    public Config() {
    }

    public Config(String config) {
        parseConfig(config);
        System.out.println("Concolic Analysis: " + concolicAnalysis);
        System.out.println("Taint Analysis: " + taintAnalysis);
        System.out.println("Seeded Bool Values: " + Arrays.toString(seedsBooleanValues));
        System.out.println("Seeded Byte Values: " + Arrays.toString(seedsByteValues));
        System.out.println("Seeded Char Values: " + Arrays.toString(seedsCharValues));
        System.out.println("Seeded Short Values: " + Arrays.toString(seedsShortValues));
        System.out.println("Seeded Int Values: " + Arrays.toString(seedsIntValues));
        System.out.println("Seeded Long Values: " + Arrays.toString(seedsLongValues));
        System.out.println("Seeded Float Values: " + Arrays.toString(seedsFloatValues));
        System.out.println("Seeded Double Values: " + Arrays.toString(seedsDoubleValues));
        System.out.println("Seeded String Values: " + Arrays.toString(seedStringValues));
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
        concolicAnalysis = Boolean.valueOf(valsAsStr[0].trim());
    }

    private void parseTaint(String[] valsAsStr) {
        taintAnalysis = TaintType.valueOf(valsAsStr[0].trim());
    }

    // --------------------------------------------------------------------------
    //
    // Section symbolic values

    private int[] seedsIntValues = new int[] {};
    private int countIntSeeds = 0;
/*
    @CompilerDirectives.TruffleBoundary
    public AnnotatedValue nextSymbolicInt() {
        int concrete = 0;
        if (countIntSeeds < seedsIntValues.length) {
            concrete = seedsIntValues[countIntSeeds];
        }
        Variable symbolic = new Variable(Types.INT, countIntSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        countIntSeeds++;
        Analysis.getInstance().getTrace().addElement(new SymbolDeclaration(symbolic));
        GWIT.trackLocationForWitness("" + concrete);
        return a;
    }
*/
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
}
