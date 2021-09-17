package tools.aqua.concolic;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFGE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFGT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFLE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFLT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPGE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPGT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPNE;

public class Concolic {

    // --------------------------------------------------------------------------
    //
    // Section trace functions

    private static TraceElement traceHead = null;
    private static TraceElement traceTail = null;
    private static boolean recordTrace = true;

    public static void addTraceElement(TraceElement tNew) {
        if (!recordTrace) {
            return;
        }
        if (traceHead == null) {
            traceHead = tNew;
        }
        else {
            traceTail.setNext(tNew);
        }
        traceTail = tNew;
    }

    @CompilerDirectives.TruffleBoundary
    public static void assume(Object condition, Meta meta) {
        boolean cont;
        if (condition instanceof AnnotatedValue) {
            AnnotatedValue a = (AnnotatedValue) condition;
            cont = a.asBoolean();
            //FIXME: can be more optimal by making a path element "assumption"
            addTraceElement(new Assumption(a.symbolic(), a.asBoolean()));
        }
        else {
            cont = (boolean) condition;
        }
        if (!cont) {
            stopRecording("assumption violation", meta);
        }
    }

    private static void printTrace() {
        TraceElement cur = traceHead;
        while (cur != null) {
            System.out.println(cur);
            cur = cur.getNext();
        }
    }

    // --------------------------------------------------------------------------
    //
    // Section symbolic values

    private static int[] seedsIntValues = new int[] {};
    private static int countIntSeeds = 0;

    public static AnnotatedValue nextSymbolicInt() {
        int concrete = 0;
        if (countIntSeeds < seedsIntValues.length) {
            concrete = seedsIntValues[countIntSeeds];
        }
        Variable symbolic = new Variable(PrimitiveTypes.INT, countIntSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        countIntSeeds++;
        addTraceElement(new SymbolDeclaration(symbolic));
        return a;
    }

    private static boolean[] seedsBooleanValues = new boolean[] {};
    private static int countBooleanSeeds = 0;

    public static AnnotatedValue nextSymbolicBoolean() {
        boolean concrete = false;
        if (countBooleanSeeds < seedsBooleanValues.length) {
            concrete = seedsBooleanValues[countBooleanSeeds];
        }
        Variable symbolic = new Variable(PrimitiveTypes.BOOL, countBooleanSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        countBooleanSeeds++;
        addTraceElement(new SymbolDeclaration(symbolic));
        return a;
    }

    private static byte[] seedsByteValues = new byte[] {};
    private static int countByteSeeds = 0;

    public static AnnotatedValue nextSymbolicByte() {
        byte concrete = 0;
        if (countByteSeeds < seedsByteValues.length) {
            concrete = seedsByteValues[countByteSeeds];
        }
        Variable symbolic = new Variable(PrimitiveTypes.BYTE, countByteSeeds);
        countByteSeeds++;
        addTraceElement(new SymbolDeclaration(symbolic));
        // upcast byte to int
        return new AnnotatedValue(concrete, new ComplexExpression(OperatorComparator.B2I, symbolic));
    }

    private static char[] seedsCharValues = new char[] {};
    private static int countCharSeeds = 0;

    public static AnnotatedValue nextSymbolicChar() {
        char concrete = 0;
        if (countCharSeeds < seedsCharValues.length) {
            concrete = seedsCharValues[countCharSeeds];
        }
        Variable symbolic = new Variable(PrimitiveTypes.CHAR, countCharSeeds);
        countCharSeeds++;
        addTraceElement(new SymbolDeclaration(symbolic));
        // upcast char to int
        return new AnnotatedValue(concrete, new ComplexExpression(OperatorComparator.C2I, symbolic));
    }

    private static short[] seedsShortValues = new short[] {};
    private static int countShortSeeds = 0;

    public static AnnotatedValue nextSymbolicShort() {
        short concrete = 0;
        if (countShortSeeds < seedsShortValues.length) {
            concrete = seedsShortValues[countShortSeeds];
        }
        Variable symbolic = new Variable(PrimitiveTypes.SHORT, countShortSeeds);
        countShortSeeds++;
        addTraceElement(new SymbolDeclaration(symbolic));
        // upcast short to int
        return new AnnotatedValue(concrete, new ComplexExpression(OperatorComparator.S2I, symbolic));
    }

    private static long[] seedsLongValues = new long[] {};
    private static int countLongSeeds = 0;

    public static AnnotatedValue nextSymbolicLong() {
        long concrete = 0L;
        if (countLongSeeds < seedsLongValues.length) {
            concrete = seedsLongValues[countLongSeeds];
        }
        Variable symbolic = new Variable(PrimitiveTypes.LONG, countLongSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        countLongSeeds++;
        addTraceElement(new SymbolDeclaration(symbolic));
        return a;
    }

    private static float[] seedsFloatValues = new float[] {};
    private static int countFloatSeeds = 0;

    public static AnnotatedValue nextSymbolicFloat() {
        float concrete = 0f;
        if (countFloatSeeds < seedsFloatValues.length) {
            concrete = seedsFloatValues[countFloatSeeds];
        }
        Variable symbolic = new Variable(PrimitiveTypes.FLOAT, countFloatSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        countFloatSeeds++;
        addTraceElement(new SymbolDeclaration(symbolic));
        return a;
    }

    private static double[] seedsDoubleValues = new double[] {};
    private static int countDoubleSeeds = 0;

    public static AnnotatedValue nextSymbolicDouble() {
        double concrete = 0d;
        if (countDoubleSeeds < seedsDoubleValues.length) {
            concrete = seedsDoubleValues[countDoubleSeeds];
        }
        Variable symbolic = new Variable(PrimitiveTypes.DOUBLE, countDoubleSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        countDoubleSeeds++;
        addTraceElement(new SymbolDeclaration(symbolic));
        return a;
    }

    private static String[] seedStringValues = new String[] {};
    private static int countStringSeeds = 0;

    @CompilerDirectives.TruffleBoundary
    public static StaticObject nextSymbolicString(Meta meta) {
        String concreteHost = "";
        if (countStringSeeds < seedStringValues.length) {
            concreteHost = seedStringValues[countStringSeeds];
        }
        StaticObject concrete = meta.toGuestString(concreteHost);
        Variable symbolic = new Variable(PrimitiveTypes.STRING, countStringSeeds);
        AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
        concrete.setConcolicId(symbolicObjects.size());
        symbolicObjects.add(new AnnotatedValue[] { a });
        countStringSeeds++;
        addTraceElement(new SymbolDeclaration(symbolic));
        return concrete;
    }


    // --------------------------------------------------------------------------
    //
    // Section symbolic fields of objects / arrays

    private static ArrayList<AnnotatedValue[]> symbolicObjects = new ArrayList<>();

    public static void setFieldAnnotation(StaticObject obj, Field f, AnnotatedValue a) {
        if (obj.getConcolicId() < 0 && a == null) {
            return;
        }
        if (obj.getConcolicId() < 0) {
            obj.setConcolicId(symbolicObjects.size());
            symbolicObjects.add(new AnnotatedValue[ ((ObjectKlass)obj.getKlass()).getFieldTable().length ]);
        }
        AnnotatedValue[] annotations = symbolicObjects.get(obj.getConcolicId());
        annotations[f.getSlot()] = a;
    }

    public static void getFieldAnnotation(StaticObject obj, Field f, Object[] symbolic, int at) {
        if (obj.getConcolicId() < 0) {
            return;
        }
        AnnotatedValue[] annotations = symbolicObjects.get(obj.getConcolicId());
        AnnotatedValue a = annotations[f.getSlot()];
        putSymbolic(symbolic, at, a);
    }

    public static void getArrayAnnotation(StaticObject array, int concIndex, Object[] symbolic, int from, int to) {
        AnnotatedValue symbIndex = popSymbolic(symbolic, from);
        if (!checkArrayAccessPathConstraint(array, concIndex, symbIndex) || array.getConcolicId() < 0) {
            return;
        }
        AnnotatedValue[] annotations = symbolicObjects.get(array.getConcolicId());
        AnnotatedValue a = annotations[concIndex];
        putSymbolic(symbolic, to, a);
    }

    public static void setArrayAnnotation(StaticObject array, int concIndex, Object[] symbolic, int fromValue, int fromIndex) {
        AnnotatedValue symbIndex = popSymbolic(symbolic, fromIndex);
        AnnotatedValue symbValue = popSymbolic(symbolic, fromValue);
        if (!checkArrayAccessPathConstraint(array, concIndex, symbIndex) || symbValue == null) {
            return;
        }

        if (array.getConcolicId() < 0) {
            array.setConcolicId(symbolicObjects.size());
            symbolicObjects.add(new AnnotatedValue[ arrayLength(array) + 1]);
        }
        AnnotatedValue[] annotations = symbolicObjects.get(array.getConcolicId());
        annotations[concIndex] = symbValue;
    }

    private static boolean checkArrayAccessPathConstraint(StaticObject array, int concIndex, AnnotatedValue annotatedIndex) {
        assert array.isArray();
        int concLen = arrayLength(array);
        boolean safe = 0 <= concIndex && concIndex < concLen;
        if ((array.getConcolicId() >= 0 && symbolicObjects.get(array.getConcolicId())[concLen] != null) || annotatedIndex != null) {

            Expression symbIndex = (annotatedIndex == null) ?
                    Constant.fromConcreteValue(concIndex) :
                    annotatedIndex.symbolic();
            Expression symbLen;
            if (array.getConcolicId() < 0) {
                symbLen = Constant.fromConcreteValue(concLen);
            }
            else {
                AnnotatedValue a = symbolicObjects.get(array.getConcolicId())[concLen];
                symbLen = (a != null) ? a.symbolic() : Constant.fromConcreteValue(concLen);
            }

            Expression arrayBound = new ComplexExpression(OperatorComparator.BAND,
                    new ComplexExpression(OperatorComparator.BVLE, Constant.INT_ZERO, symbIndex ),
                    new ComplexExpression(OperatorComparator.BVLT, symbIndex, symbLen));

            addTraceElement(new PathCondition(safe ? arrayBound : new ComplexExpression(OperatorComparator.BNEG, arrayBound), safe ? 1 : 0, 2));
        }
        return safe;
    }

    private static int arrayLength(StaticObject array) {
        return array.length();
    }

    // --------------------------------------------------------------------------
    //
    // symbolic array sizes

    public static void newArray(long[] primitives, Object[] symbolic, byte jvmPrimitiveType, int top, Meta meta, BytecodeNode bytecodeNode) {
        int concLen = BytecodeNode.popInt(primitives, top - 1);
        AnnotatedValue symbLen = popSymbolic(symbolic, top -1);
        addArrayCreationPathConstraint(concLen, symbLen);
        StaticObject array = InterpreterToVM.allocatePrimitiveArray(jvmPrimitiveType, concLen, meta, bytecodeNode);
        if (symbLen != null) {
            array.setConcolicId(symbolicObjects.size());
            symbolicObjects.add(new AnnotatedValue[ concLen + 1]);
            symbolicObjects.get(array.getConcolicId())[concLen] = symbLen;
        }
        BytecodeNode.putObject(symbolic, top - 1, array);
    }

    private static void addArrayCreationPathConstraint(int conclen, AnnotatedValue symbLen) {
        if (symbLen == null) {
            return;
        }
        boolean holds = (0 <= conclen);
        Expression lengthConstraint = new ComplexExpression(OperatorComparator.BVLE, Constant.INT_ZERO, symbLen.symbolic());

        addTraceElement(new PathCondition(
                holds ? lengthConstraint : new ComplexExpression(OperatorComparator.BNEG, lengthConstraint),
                holds ? 1 : 0, 2));
    }

    // --------------------------------------------------------------------------
    //
    // start / end analysis functions

    @CompilerDirectives.TruffleBoundary
    public static void newPath(String config) {
        System.out.println("======================== START PATH [BEGIN].");
        traceHead = null;
        traceTail = null;
        recordTrace = true;
        countBooleanSeeds = 0;
        countByteSeeds = 0;
        countCharSeeds = 0;
        countShortSeeds = 0;
        countIntSeeds = 0;
        countLongSeeds = 0;
        countFloatSeeds = 0;
        countDoubleSeeds = 0;
        countStringSeeds = 0;
        symbolicObjects.clear();
        parseConfig(config);
        System.out.println("Seeded Bool Values: " + Arrays.toString(seedsBooleanValues));
        System.out.println("Seeded Byte Values: " + Arrays.toString(seedsByteValues));
        System.out.println("Seeded Char Values: " + Arrays.toString(seedsCharValues));
        System.out.println("Seeded Short Values: " + Arrays.toString(seedsShortValues));
        System.out.println("Seeded Int Values: " + Arrays.toString(seedsIntValues));
        System.out.println("Seeded Long Values: " + Arrays.toString(seedsLongValues));
        System.out.println("Seeded Float Values: " + Arrays.toString(seedsFloatValues));
        System.out.println("Seeded Double Values: " + Arrays.toString(seedsDoubleValues));
        System.out.println("Seeded String Values: " + Arrays.toString(seedStringValues));
        System.out.println("======================== START PATH [END].");
    }

    private static void parseConfig(String config) {
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
            }
        }
    }

    private static String[] splitVals(String config) {
        String[] valsAsStr;
        if (config.trim().length() > 0) {
            valsAsStr = config.split(",");
        }
        else {
            valsAsStr = new String[] {};
        }
        return valsAsStr;
    }

    private static String b64decode(String str) {
        byte[] in = str.getBytes(StandardCharsets.UTF_8);
        byte[] out = Base64.getDecoder().decode(in);
        return new String(out);
    }

    private static void parseBools(String[] valsAsStr, boolean b64) {
        seedsBooleanValues = new boolean[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            seedsBooleanValues[i] = Boolean.valueOf(b64 ? b64decode(valsAsStr[i].trim()) :  valsAsStr[i].trim());
        }
    }

    private static void parseBytes(String[] valsAsStr, boolean b64) {
        seedsByteValues = new byte[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            seedsByteValues[i] = Byte.valueOf(b64 ? b64decode(valsAsStr[i].trim()) :  valsAsStr[i].trim());
        }
    }

    private static void parseChars(String[] valsAsStr, boolean b64) {
        seedsCharValues = new char[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            //TODO: not sure if this is correct
            seedsCharValues[i] = valsAsStr[i].trim().charAt(0);
        }
    }

    private static void parseShorts(String[] valsAsStr, boolean b64) {
        seedsShortValues = new short[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            seedsShortValues[i] = Short.valueOf(b64 ? b64decode(valsAsStr[i].trim()) :  valsAsStr[i].trim());
        }
    }

    private static void parseInts(String[] valsAsStr, boolean b64) {
        seedsIntValues = new int[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            seedsIntValues[i] = Integer.valueOf(b64 ? b64decode(valsAsStr[i].trim()) :  valsAsStr[i].trim());
        }
    }

    private static void parseLongs(String[] valsAsStr, boolean b64) {
        seedsLongValues = new long[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            seedsLongValues[i] = Long.valueOf(b64 ? b64decode(valsAsStr[i].trim()) :  valsAsStr[i].trim());
        }
    }

    private static void parseFloats(String[] valsAsStr, boolean b64) {
        seedsFloatValues = new float[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            seedsFloatValues[i] = Float.valueOf(b64 ? b64decode(valsAsStr[i].trim()) :  valsAsStr[i].trim());
        }
    }

    private static void parseDoubles(String[] valsAsStr, boolean b64) {
        seedsDoubleValues = new double[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            seedsDoubleValues[i] = Double.valueOf(b64 ? b64decode(valsAsStr[i].trim()) :  valsAsStr[i].trim());
        }
    }

    private static void parseStrings(String[] valsAsStr, boolean b64) {
        seedStringValues = new String[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            seedStringValues[i] = b64 ? b64decode(valsAsStr[i].trim()) :  valsAsStr[i].trim();
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static void endPath() {
        System.out.println("======================== END PATH [BEGIN].");
        printTrace();
        System.out.println("======================== END PATH [END].");
        System.out.println("[ENDOFTRACE]");
    }

    @CompilerDirectives.TruffleBoundary
    public static void uncaughtException(StaticObject pendingException) {
        String errorMessage = pendingException.getKlass().getNameAsString();
        addTraceElement(new ErrorEvent(errorMessage));
    }

    @CompilerDirectives.TruffleBoundary
    public static void stopRecording(String message, Meta meta) {
        addTraceElement(new ExceptionalEvent(message));
        recordTrace = false;
        meta.throwException(meta.java_lang_RuntimeException);
    }

    // --------------------------------------------------------------------------
    //
    // bytecode functions

    private static AnnotatedValue binarySymbolicOp(OperatorComparator op, PrimitiveTypes type,
                                                   Object cLeft, Object cRight, Object concResult, AnnotatedValue sLeft, AnnotatedValue sRight) {

        return binarySymbolicOp(op, type, type, cLeft, cRight, concResult, sLeft, sRight);
    }

    private static AnnotatedValue binarySymbolicOp(OperatorComparator op, PrimitiveTypes typeLeft, PrimitiveTypes typeRight,
                Object cLeft, Object cRight, Object concResult, AnnotatedValue sLeft, AnnotatedValue sRight) {

        if (sLeft == null && sRight == null) {
            return null;
        }
        if (sLeft == null) sLeft = AnnotatedValue.fromConstant(typeLeft, cLeft);
        if (sRight == null) sRight = AnnotatedValue.fromConstant(typeRight, cRight);
        return new AnnotatedValue( concResult, new ComplexExpression(op, sLeft.symbolic(), sRight.symbolic() ));
    }

    private static AnnotatedValue unarySymbolicOp(OperatorComparator op, Object concResult, AnnotatedValue s1) {
        if (s1 == null) {
            return null;
        }
        return new AnnotatedValue( concResult, new ComplexExpression(op, s1.symbolic()));
    }

    private static AnnotatedValue binarySymbolicOp(OperatorComparator op, Object concResult, AnnotatedValue s1, Constant c) {
        if (s1 == null) {
            return null;
        }
        return new AnnotatedValue( concResult, new ComplexExpression(op, s1.symbolic(), c));
    }

    // case IADD: putInt(stack, top - 2, popInt(stack, top - 1) + popInt(stack, top - 2)); break;
    public static void iadd(long[] primitives,  Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c1 + c2;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.IADD, PrimitiveTypes.INT,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-2)));
    }

    // case LADD: putLong(stack, top - 4, popLong(stack, top - 1) + popLong(stack, top - 3)); break;
    public static void ladd(long[] primitives,  Object[] symbolic, int top) {
        long c1 = BytecodeNode.popLong(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 3);
        long concResult = c1 + c2;
        BytecodeNode.putLong(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.LADD, PrimitiveTypes.LONG,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-3)));
    }

    // case FADD: putFloat(stack, top - 2, popFloat(stack, top - 1) + popFloat(stack, top - 2)); break;
    public static void fadd(long[] primitives,  Object[] symbolic, int top) {
        float c1 = BytecodeNode.popFloat(primitives, top - 1);
        float c2 = BytecodeNode.popFloat(primitives, top - 2);
        float concResult = c1 + c2;
        BytecodeNode.putFloat(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.FADD, PrimitiveTypes.FLOAT,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-2)));
    }

    // case DADD: putDouble(stack, top - 4, popDouble(stack, top - 1) + popDouble(stack, top - 3)); break;
    public static void dadd(long[] primitives,  Object[] symbolic, int top) {
        double c1 = BytecodeNode.popDouble(primitives, top - 1);
        double c2 = BytecodeNode.popDouble(primitives, top - 3);
        double concResult = c1 + c2;
        BytecodeNode.putDouble(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.DADD, PrimitiveTypes.DOUBLE,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-3)));
    }

    //case ISUB: putInt(stack, top - 2, -popInt(stack, top - 1) + popInt(stack, top - 2)); break;
    public static void isub(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c2 - c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.ISUB, PrimitiveTypes.INT,
                c2, c1, concResult, popSymbolic(symbolic, top-2), popSymbolic(symbolic,top-1)));
    }

    //case LSUB: putLong(stack, top - 4, -popLong(stack, top - 1) + popLong(stack, top - 3)); break;
    public static void lsub(long[] primitives,  Object[] symbolic, int top) {
        long c1 = BytecodeNode.popLong(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 3);
        long concResult = c2 - c1;
        BytecodeNode.putLong(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.LSUB, PrimitiveTypes.LONG,
                c2, c1, concResult, popSymbolic(symbolic, top-3), popSymbolic(symbolic,top-1)));
    }

    //case FSUB: putFloat(stack, top - 2, -popFloat(stack, top - 1) + popFloat(stack, top - 2)); break;
    public static void fsub(long[] primitives,  Object[] symbolic, int top) {
        float c1 = BytecodeNode.popFloat(primitives, top - 1);
        float c2 = BytecodeNode.popFloat(primitives, top - 2);
        float concResult = c2 - c1;
        BytecodeNode.putFloat(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.FSUB, PrimitiveTypes.FLOAT,
                c2, c1, concResult, popSymbolic(symbolic, top-2), popSymbolic(symbolic,top-1)));
    }

    //case DSUB: putDouble(stack, top - 4, -popDouble(stack, top - 1) + popDouble(stack, top - 3)); break;
    public static void dsub(long[] primitives,  Object[] symbolic, int top) {
        double c1 = BytecodeNode.popDouble(primitives, top - 1);
        double c2 = BytecodeNode.popDouble(primitives, top - 3);
        double concResult = c2 - c1;
        BytecodeNode.putDouble(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.DSUB, PrimitiveTypes.DOUBLE,
                c2, c1, concResult, popSymbolic(symbolic, top-3), popSymbolic(symbolic,top-1)));
    }

    // case IMUL: putInt(stack, top - 2, popInt(stack, top - 1) * popInt(stack, top - 2)); break;
    public static void imul(long[] primitives,  Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c1 * c2;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.IMUL, PrimitiveTypes.INT,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-2)));
    }

    // case LMUL: putLong(stack, top - 4, popLong(stack, top - 1) * popLong(stack, top - 3)); break;
    public static void lmul(long[] primitives,  Object[] symbolic, int top) {
        long c1 = BytecodeNode.popLong(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 3);
        long concResult = c1 * c2;
        BytecodeNode.putLong(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.LMUL, PrimitiveTypes.LONG,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-3)));
    }

    // case FMUL: putFloat(stack, top - 2, popFloat(stack, top - 1) * popFloat(stack, top - 2)); break;
    public static void fmul(long[] primitives,  Object[] symbolic, int top) {
        float c1 = BytecodeNode.popFloat(primitives, top - 1);
        float c2 = BytecodeNode.popFloat(primitives, top - 2);
        float concResult = c1 * c2;
        BytecodeNode.putFloat(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.FMUL, PrimitiveTypes.FLOAT,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-2)));
    }

    // case DMUL: putDouble(stack, top - 4, popDouble(stack, top - 1) * popDouble(stack, top - 3)); break;
    public static void dmul(long[] primitives,  Object[] symbolic, int top) {
        double c1 = BytecodeNode.popDouble(primitives, top - 1);
        double c2 = BytecodeNode.popDouble(primitives, top - 3);
        double concResult = c1 * c2;
        BytecodeNode.putDouble(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.DMUL, PrimitiveTypes.DOUBLE,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-3)));
    }

    private static void checkNonZero(int value, AnnotatedValue a, BytecodeNode bn) {
        if (value != 0) {
            if (a != null) {
                addTraceElement(new PathCondition( new ComplexExpression(
                        OperatorComparator.BVNE, a.symbolic(), Constant.INT_ZERO),0, 2));
            }
        }
        else {
            if (a != null) {
                addTraceElement(new PathCondition(new ComplexExpression(
                        OperatorComparator.BVEQ, a.symbolic(), Constant.INT_ZERO), 1, 2));
            }
            bn.enterImplicitExceptionProfile();
            Meta meta = bn.getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArithmeticException, "/ by zero");
        }
    }

    private static void checkNonZero(long value, AnnotatedValue a, BytecodeNode bn) {
        if (value != 0L) {
            if (a != null) {
                addTraceElement(new PathCondition( new ComplexExpression(
                        OperatorComparator.BVNE, a.symbolic(), Constant.LONG_ZERO),0, 2));
            }
        }
        else {
            if (a != null) {
                addTraceElement(new PathCondition(new ComplexExpression(
                        OperatorComparator.BVEQ, a.symbolic(), Constant.LONG_ZERO), 1, 2));
            }
            bn.enterImplicitExceptionProfile();
            Meta meta = bn.getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArithmeticException, "/ by zero");
        }
    }

    // case IDIV: putInt(stack, top - 2, divInt(checkNonZero(popInt(stack, top - 1)), popInt(stack, top - 2))); break;
    public static void idiv(long[] primitives, Object[] symbolic, int top, BytecodeNode bn) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        checkNonZero(c1, peekSymbolic(symbolic, top -1), bn);
        int concResult = c2 / c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.IDIV, PrimitiveTypes.INT,
                c2, c1, concResult, popSymbolic(symbolic, top-2), popSymbolic(symbolic,top-1)));
    }

    // case LDIV: putLong(stack, top - 4, divLong(checkNonZero(popLong(stack, top - 1)), popLong(stack, top - 3))); break;
    public static void ldiv(long[] primitives,  Object[] symbolic, int top, BytecodeNode bn) {
        long c1 = BytecodeNode.popLong(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 3);
        checkNonZero(c1, peekSymbolic(symbolic, top -1), bn);
        long concResult = c2 / c1;
        BytecodeNode.putLong(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.LDIV, PrimitiveTypes.LONG,
                c2, c1, concResult, popSymbolic(symbolic, top-3), popSymbolic(symbolic,top-1)));
    }

    // case FDIV: putFloat(stack, top - 2, divFloat(popFloat(stack, top - 1), popFloat(stack, top - 2))); break;
    public static void fdiv(long[] primitives,  Object[] symbolic, int top) {
        float c1 = BytecodeNode.popFloat(primitives, top - 1);
        float c2 = BytecodeNode.popFloat(primitives, top - 2);
        float concResult = c2 / c1;
        BytecodeNode.putFloat(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.FDIV, PrimitiveTypes.FLOAT,
                c2, c1, concResult, popSymbolic(symbolic, top-2), popSymbolic(symbolic,top-1)));
    }

    // case DDIV: putDouble(stack, top - 4, divDouble(popDouble(stack, top - 1), popDouble(stack, top - 3))); break;
    public static void ddiv(long[] primitives,  Object[] symbolic, int top) {
        double c1 = BytecodeNode.popDouble(primitives, top - 1);
        double c2 = BytecodeNode.popDouble(primitives, top - 3);
        double concResult = c2 / c1;
        BytecodeNode.putDouble(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.DDIV, PrimitiveTypes.DOUBLE,
                c2, c1, concResult, popSymbolic(symbolic, top-3), popSymbolic(symbolic,top-1)));
    }

    // case IREM: putInt(stack, top - 2, remInt(checkNonZero(popInt(stack, top - 1)), popInt(stack, top - 2))); break;
    public static void irem(long[] primitives, Object[] symbolic, int top, BytecodeNode bn) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        checkNonZero(c1, peekSymbolic(symbolic, top -1), bn);
        int concResult = c2 % c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.IREM, PrimitiveTypes.INT,
                c2, c1, concResult, popSymbolic(symbolic, top-2), popSymbolic(symbolic,top-1)));
    }

    // case LREM: putLong(stack, top - 4, remLong(checkNonZero(popLong(stack, top - 1)), popLong(stack, top - 3))); break;
    public static void lrem(long[] primitives,  Object[] symbolic, int top, BytecodeNode bn) {
        long c1 = BytecodeNode.popLong(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 3);
        checkNonZero(c1, peekSymbolic(symbolic, top -1), bn);
        long concResult = c2 % c1;
        BytecodeNode.putLong(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.LREM, PrimitiveTypes.LONG,
                c2, c1, concResult, popSymbolic(symbolic, top-3), popSymbolic(symbolic,top-1)));
    }

    // case FREM: putFloat(stack, top - 2, remFloat(popFloat(stack, top - 1), popFloat(stack, top - 2))); break;
    public static void frem(long[] primitives,  Object[] symbolic, int top) {
        float c1 = BytecodeNode.popFloat(primitives, top - 1);
        float c2 = BytecodeNode.popFloat(primitives, top - 2);
        float concResult = c2 % c1;
        BytecodeNode.putFloat(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.FREM, PrimitiveTypes.FLOAT,
                c2, c1, concResult, popSymbolic(symbolic, top-2), popSymbolic(symbolic,top-1)));
    }

    // case DREM: putDouble(stack, top - 4, remDouble(popDouble(stack, top - 1), popDouble(stack, top - 3))); break;
    public static void drem(long[] primitives,  Object[] symbolic, int top) {
        double c1 = BytecodeNode.popDouble(primitives, top - 1);
        double c2 = BytecodeNode.popDouble(primitives, top - 3);
        double concResult = c2 % c1;
        BytecodeNode.putDouble(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.DREM, PrimitiveTypes.DOUBLE,
                c2, c1, concResult, popSymbolic(symbolic, top-3), popSymbolic(symbolic,top-1)));
    }

    // case INEG: putInt(stack, top - 1, -popInt(stack, top - 1)); break;
    public static void ineg(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int concResult = -c1;
        BytecodeNode.putInt(primitives, top - 1, concResult);
        putSymbolic(symbolic, top-1, unarySymbolicOp(OperatorComparator.INEG, concResult, popSymbolic(symbolic, top-1)));
    }

    // case LNEG: putLong(stack, top - 2, -popLong(stack, top - 1)); break;
    public static void lneg(long[] primitives, Object[] symbolic, int top) {
        long c1 = BytecodeNode.popLong(primitives, top - 1);
        long concResult = -c1;
        BytecodeNode.putLong(primitives, top - 2, concResult);
        putSymbolic(symbolic, top-1, unarySymbolicOp(OperatorComparator.LNEG, concResult, popSymbolic(symbolic, top-1)));
    }

    // case FNEG: putFloat(stack, top - 1, -popFloat(stack, top - 1)); break;
    public static void fneg(long[] primitives, Object[] symbolic, int top) {
        float c1 = BytecodeNode.popFloat(primitives, top - 1);
        float concResult = -c1;
        BytecodeNode.putFloat(primitives, top - 1, concResult);
        putSymbolic(symbolic, top-1, unarySymbolicOp(OperatorComparator.FNEG, concResult, popSymbolic(symbolic, top-1)));
    }

    // case DNEG: putDouble(stack, top - 2, -popDouble(stack, top - 1)); break;
    public static void dneg(long[] primitives, Object[] symbolic, int top) {
        double c1 = BytecodeNode.popDouble(primitives, top - 1);
        double concResult = -c1;
        BytecodeNode.putDouble(primitives, top - 2, concResult);
        putSymbolic(symbolic, top-1, unarySymbolicOp(OperatorComparator.DNEG, concResult, popSymbolic(symbolic, top-1)));
    }

    // case ISHL: putInt(stack, top - 2, shiftLeftInt(popInt(stack, top - 1), popInt(stack, top - 2))); break;
    public static void ishl(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c2 << c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);

        AnnotatedValue sRight = popSymbolic(symbolic,top-1);
        AnnotatedValue sLeft = popSymbolic(symbolic, top-2);
        if (sLeft != null || sRight != null) {
            sRight = new AnnotatedValue(c1, new ComplexExpression(OperatorComparator.IAND,
                    sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                    Constant.INT_0x1F));
        }
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.ISHL, PrimitiveTypes.INT,
                c2, c1, concResult, sLeft, sRight));
    }

    // case LSHL: putLong(stack, top - 3, shiftLeftLong(popInt(stack, top - 1), popLong(stack, top - 2))); break;
    public static void lshl(long[] primitives,  Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 2);
        long concResult = c2 << c1;
        BytecodeNode.putLong(primitives, top - 3, concResult);

        AnnotatedValue sRight = popSymbolic(symbolic,top-1);
        AnnotatedValue sLeft = popSymbolic(symbolic, top-2);
        if (sLeft != null || sRight != null) {
            sRight = new AnnotatedValue(c1, new ComplexExpression(OperatorComparator.IAND,
                    sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                    Constant.INT_0x3F));
        }
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.LSHL, PrimitiveTypes.LONG, PrimitiveTypes.INT,
                c2, c1, concResult, sLeft, sRight));
    }

    // case ISHR: putInt(stack, top - 2, shiftRightSignedInt(popInt(stack, top - 1), popInt(stack, top - 2))); break;
    public static void ishr(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c2 >> c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);

        AnnotatedValue sRight = popSymbolic(symbolic,top-1);
        AnnotatedValue sLeft = popSymbolic(symbolic, top-2);
        if (sLeft != null || sRight != null) {
            sRight = new AnnotatedValue(c1, new ComplexExpression(OperatorComparator.IAND,
                    sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                    Constant.INT_0x1F));
        }
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.ISHR, PrimitiveTypes.INT,
                c2, c1, concResult, sLeft, sRight));
    }

    // case LSHR: putLong(stack, top - 3, shiftRightSignedLong(popInt(stack, top - 1), popLong(stack, top - 2))); break;
    public static void lshr(long[] primitives,  Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 2);
        long concResult = c2 >> c1;
        BytecodeNode.putLong(primitives, top - 3, concResult);

        AnnotatedValue sRight = popSymbolic(symbolic,top-1);
        AnnotatedValue sLeft = popSymbolic(symbolic, top-2);
        if (sLeft != null || sRight != null) {
            sRight = new AnnotatedValue(c1, new ComplexExpression(OperatorComparator.IAND,
                    sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                    Constant.INT_0x3F));
        }
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.LSHR, PrimitiveTypes.LONG, PrimitiveTypes.INT,
                c2, c1, concResult, sLeft, sRight));
    }

    // case IUSHR: putInt(stack, top - 2, shiftRightUnsignedInt(popInt(stack, top - 1), popInt(stack, top - 2))); break;
    public static void iushr(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c2 >>> c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);

        AnnotatedValue sRight = popSymbolic(symbolic,top-1);
        AnnotatedValue sLeft = popSymbolic(symbolic, top-2);
        if (sLeft != null || sRight != null) {
            sRight = new AnnotatedValue(c1, new ComplexExpression(OperatorComparator.IAND,
                    sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                    Constant.INT_0x1F));
        }

        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.IUSHR, PrimitiveTypes.INT,
                c2, c1, concResult, sLeft, sRight));
    }

    // case LUSHR: putLong(stack, top - 3, shiftRightUnsignedLong(popInt(stack, top - 1), popLong(stack, top - 2))); break;
    public static void lushr(long[] primitives,  Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 2);
        long concResult = c2 >>> c1;
        BytecodeNode.putLong(primitives, top - 3, concResult);

        AnnotatedValue sRight = popSymbolic(symbolic,top-1);
        AnnotatedValue sLeft = popSymbolic(symbolic, top-2);
        if (sLeft != null || sRight != null) {
            sRight = new AnnotatedValue(c1, new ComplexExpression(OperatorComparator.IAND,
                    sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                    Constant.INT_0x3F));
        }

        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.LUSHR, PrimitiveTypes.LONG, PrimitiveTypes.INT,
                c2, c1, concResult, sLeft, sRight));
    }

    // case IAND: putInt(stack, top - 2, popInt(stack, top - 1) & popInt(stack, top - 2)); break;
    public static void iand(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c1 & c2;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.IAND, PrimitiveTypes.INT,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-2)));
    }

    // case LAND: putLong(stack, top - 4, popLong(stack, top - 1) & popLong(stack, top - 3)); break;
    public static void land(long[] primitives,  Object[] symbolic, int top) {
        long c1 = BytecodeNode.popLong(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 3);
        long concResult = c1 & c2;
        BytecodeNode.putLong(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.LAND, PrimitiveTypes.LONG,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-3)));
    }

    //  case IOR: putInt(stack, top - 2, popInt(stack, top - 1) | popInt(stack, top - 2)); break;
    public static void ior(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c1 | c2;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.IOR, PrimitiveTypes.INT,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-2)));
    }

    // case LOR: putLong(stack, top - 4, popLong(stack, top - 1) | popLong(stack, top - 3)); break;
    public static void lor(long[] primitives,  Object[] symbolic, int top) {
        long c1 = BytecodeNode.popLong(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 3);
        long concResult = c1 | c2;
        BytecodeNode.putLong(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.LOR, PrimitiveTypes.LONG,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-3)));
    }

    // case IXOR: putInt(stack, top - 2, popInt(stack, top - 1) ^ popInt(stack, top - 2)); break;
    public static void ixor(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c1 ^ c2;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        putSymbolic(symbolic, top -2, binarySymbolicOp(OperatorComparator.IXOR, PrimitiveTypes.INT,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-2)));
    }

    // case LXOR: putLong(stack, top - 4, popLong(stack, top - 1) ^ popLong(stack, top - 3)); break;
    public static void lxor(long[] primitives,  Object[] symbolic, int top) {
        long c1 = BytecodeNode.popLong(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 3);
        long concResult = c1 ^ c2;
        BytecodeNode.putLong(primitives, top - 4, concResult);
        putSymbolic(symbolic, top -3, binarySymbolicOp(OperatorComparator.LXOR, PrimitiveTypes.LONG,
                c1, c2, concResult, popSymbolic(symbolic, top-1), popSymbolic(symbolic,top-3)));
    }

    // case IINC: setLocalInt(stack, bs.readLocalIndex(curBCI), getLocalInt(stack, bs.readLocalIndex(curBCI)) + bs.readIncrement(curBCI)); break;
    public static void iinc(long[] primitives, Object[] symbolic, BytecodeStream bs, int curBCI) {
        int index = bs.readLocalIndex(curBCI);
        int incr =  bs.readIncrement(curBCI);
        AnnotatedValue s1 = getLocalSymbolic(symbolic, index);
        int c1 = BytecodeNode.getLocalInt(primitives, index);

        // concrete
        int concResult = c1 + incr;
        BytecodeNode.setLocalInt(primitives, index, c1 + incr);

        // symbolic
        if (s1 == null) {
            return;
        }

        Constant symbIncr = Constant.fromConcreteValue(incr);
        AnnotatedValue symbResult = new AnnotatedValue(concResult,
                new ComplexExpression(OperatorComparator.IADD, s1.symbolic(), symbIncr));
        setLocalSymbolic(symbolic, index, symbResult);
    }

    // case I2L: putLong(stack, top - 1, popInt(stack, top - 1)); break;
    public static void i2l(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        BytecodeNode.putLong(primitives, top -1, c1);
        putSymbolic(symbolic, top-1, unarySymbolicOp(OperatorComparator.I2L, c1, popSymbolic(symbolic, top -1)));
    }

    // case I2F: putFloat(stack, top - 1, popInt(stack, top - 1)); break;
    public static void i2f(long[] primitives, Object[] symbolic, int top) {
        float c1 = BytecodeNode.popInt(primitives, top - 1);
        BytecodeNode.putFloat(primitives, top -1, c1);
        putSymbolic(symbolic, top-1, unarySymbolicOp(OperatorComparator.I2F, c1, popSymbolic(symbolic, top -1)));
    }

    // case I2D: putDouble(stack, top - 1, popInt(stack, top - 1)); break;
    public static void i2d(long[] primitives, Object[] symbolic, int top) {
        double c1 = BytecodeNode.popInt(primitives, top - 1);
        BytecodeNode.putDouble(primitives, top -1, c1);
        putSymbolic(symbolic, top, unarySymbolicOp(OperatorComparator.I2D, c1, popSymbolic(symbolic, top -1)));
    }

    // case L2I: putInt(stack, top - 2, (int) popLong(stack, top - 1)); break;
    public static void l2i(long[] primitives, Object[] symbolic, int top) {
        int c1 = (int) BytecodeNode.popLong(primitives, top - 1);
        BytecodeNode.putInt(primitives, top -2, c1);
        putSymbolic(symbolic, top-2, unarySymbolicOp(OperatorComparator.L2I, c1, popSymbolic(symbolic, top -1)));
    }

    // case L2F: putFloat(stack, top - 2, popLong(stack, top - 1)); break;
    public static void l2f(long[] primitives, Object[] symbolic, int top) {
        float c1 = (float) BytecodeNode.popLong(primitives, top - 1);
        BytecodeNode.putFloat(primitives, top -2, c1);
        putSymbolic(symbolic, top-2, unarySymbolicOp(OperatorComparator.L2F, c1, popSymbolic(symbolic, top -1)));
    }

    // case L2D: putDouble(stack, top - 2, popLong(stack, top - 1)); break;
    public static void l2d(long[] primitives, Object[] symbolic, int top) {
        double c1 = (double) BytecodeNode.popLong(primitives, top - 1);
        BytecodeNode.putDouble(primitives, top -2, c1);
        putSymbolic(symbolic, top-1, unarySymbolicOp(OperatorComparator.L2D, c1, popSymbolic(symbolic, top -1)));
    }

    // case F2I: putInt(stack, top - 1, (int) popFloat(stack, top - 1)); break;
    public static void f2i(long[] primitives, Object[] symbolic, int top) {
        int c1 = (int) BytecodeNode.popFloat(primitives, top - 1);
        BytecodeNode.putInt(primitives, top -1, c1);
        putSymbolic(symbolic, top-1, unarySymbolicOp(OperatorComparator.F2I, c1, popSymbolic(symbolic, top -1)));
    }

    // case F2L: putLong(stack, top - 1, (long) popFloat(stack, top - 1)); break;
    public static void f2l(long[] primitives, Object[] symbolic, int top) {
        long c1 = (long) BytecodeNode.popFloat(primitives, top - 1);
        BytecodeNode.putLong(primitives, top -1, c1);
        putSymbolic(symbolic, top, unarySymbolicOp(OperatorComparator.F2L, c1, popSymbolic(symbolic, top -1)));
    }

    // case F2D: putDouble(stack, top - 1, popFloat(stack, top - 1)); break;
    public static void f2d(long[] primitives, Object[] symbolic, int top) {
        double c1 = BytecodeNode.popFloat(primitives, top - 1);
        BytecodeNode.putDouble(primitives, top -1, c1);
        putSymbolic(symbolic, top, unarySymbolicOp(OperatorComparator.I2L, c1, popSymbolic(symbolic, top -1)));
    }

    // case D2I: putInt(stack, top - 2, (int) popDouble(stack, top - 1)); break;
    public static void d2i(long[] primitives, Object[] symbolic, int top) {
        int c1 = (int) BytecodeNode.popDouble(primitives, top - 1);
        BytecodeNode.putInt(primitives, top -2, c1);
        putSymbolic(symbolic, top-2, unarySymbolicOp(OperatorComparator.D2I, c1, popSymbolic(symbolic, top -1)));
    }

    // case D2L: putLong(stack, top - 2, (long) popDouble(stack, top - 1)); break;
    public static void d2l(long[] primitives, Object[] symbolic, int top) {
        long c1 = (long) BytecodeNode.popDouble(primitives, top - 1);
        BytecodeNode.putLong(primitives, top -2, c1);
        putSymbolic(symbolic, top-1, unarySymbolicOp(OperatorComparator.D2L, c1, popSymbolic(symbolic, top -1)));
    }

    // case D2F: putFloat(stack, top - 2, (float) popDouble(stack, top - 1)); break;
    public static void d2f(long[] primitives, Object[] symbolic, int top) {
        float c1 = (float) BytecodeNode.popDouble(primitives, top - 1);
        BytecodeNode.putFloat(primitives, top -2, c1);
        putSymbolic(symbolic, top-2, unarySymbolicOp(OperatorComparator.D2F, c1, popSymbolic(symbolic, top -1)));
    }

    // case I2B: putInt(stack, top - 1, (byte) popInt(stack, top - 1)); break;
    public static void i2b(long[] primitives, Object[] symbolic, int top) {
        byte c1 = (byte) BytecodeNode.popInt(primitives, top - 1);
        BytecodeNode.putInt(primitives, top -1, c1);
        putSymbolic(symbolic, top-1, binarySymbolicOp(OperatorComparator.IAND, c1, popSymbolic(symbolic, top -1), Constant.INT_BYTE_MAX));
    }

    // case I2C: putInt(stack, top - 1, (char) popInt(stack, top - 1)); break;
    public static void i2c(long[] primitives, Object[] symbolic, int top) {
        char c1 = (char) BytecodeNode.popInt(primitives, top - 1);
        BytecodeNode.putInt(primitives, top -1, c1);
        putSymbolic(symbolic, top-1, binarySymbolicOp(OperatorComparator.IAND, c1, popSymbolic(symbolic, top -1), Constant.INT_CHAR_MAX));
    }

    // case I2S: putInt(stack, top - 1, (short) popInt(stack, top - 1)); break;
    public static void i2s(long[] primitives, Object[] symbolic, int top) {
        short c1 = (short) BytecodeNode.popInt(primitives, top - 1);
        BytecodeNode.putInt(primitives, top -1, c1);
        putSymbolic(symbolic, top-1, binarySymbolicOp(OperatorComparator.IAND, c1, popSymbolic(symbolic, top -1), Constant.INT_SHORT_MAX));
    }

    // case LCMP : putInt(stack, top - 4, compareLong(popLong(stack, top - 1), popLong(stack, top - 3))); break;
    public static void lcmp(long[] primitives, Object[] symbolic, int top) {
        long c1 = BytecodeNode.popLong(primitives, top - 1);
        long c2 = BytecodeNode.popLong(primitives, top - 3);
        int concResult = Long.compare(c2, c1);
        BytecodeNode.putInt(primitives, top - 4, concResult);
        putSymbolic(symbolic, top-4, binarySymbolicOp(OperatorComparator.LCMP, PrimitiveTypes.LONG,
                c2, c1, concResult, popSymbolic(symbolic, top - 3), popSymbolic(symbolic, top - 1)));
    }

    // case FCMPL: putInt(stack, top - 2, compareFloatLess(popFloat(stack, top - 1), popFloat(stack, top - 2))); break;
    public static void fcmpl(long[] primitives, Object[] symbolic, int top) {
        float c1 = BytecodeNode.popFloat(primitives, top - 1);
        float c2 = BytecodeNode.popFloat(primitives, top - 2);
        int concResult = BytecodeNode.compareFloatLess(c1, c2);
        BytecodeNode.putInt(primitives, top - 2, concResult);
        putSymbolic(symbolic, top-2, binarySymbolicOp(OperatorComparator.FCMPL, PrimitiveTypes.FLOAT,
                c2, c1, concResult, popSymbolic(symbolic, top - 2), popSymbolic(symbolic, top - 1)));
    }

    // case FCMPG: putInt(stack, top - 2, compareFloatGreater(popFloat(stack, top - 1), popFloat(stack, top - 2))); break;
    public static void fcmpg(long[] primitives, Object[] symbolic, int top) {
        float c1 = BytecodeNode.popFloat(primitives, top - 1);
        float c2 = BytecodeNode.popFloat(primitives, top - 2);
        int concResult = BytecodeNode.compareFloatGreater(c1, c2);
        BytecodeNode.putInt(primitives, top - 2, concResult);
        putSymbolic(symbolic, top-2, binarySymbolicOp(OperatorComparator.FCMPG, PrimitiveTypes.FLOAT,
                c2, c1, concResult, popSymbolic(symbolic, top - 2), popSymbolic(symbolic, top - 1)));
    }

    // case DCMPL: putInt(stack, top - 4, compareDoubleLess(popDouble(stack, top - 1), popDouble(stack, top - 3))); break;
    public static void dcmpl(long[] primitives, Object[] symbolic, int top) {
        double c1 = BytecodeNode.popDouble(primitives, top - 1);
        double c2 = BytecodeNode.popDouble(primitives, top - 3);
        int concResult = BytecodeNode.compareDoubleLess(c1, c2);
        BytecodeNode.putInt(primitives, top - 4, concResult);
        putSymbolic(symbolic, top-4, binarySymbolicOp(OperatorComparator.DCMPL, PrimitiveTypes.DOUBLE,
                c2, c1, concResult, popSymbolic(symbolic, top - 3), popSymbolic(symbolic, top - 1)));
    }

    // case DCMPG: putInt(stack, top - 4, compareDoubleGreater(popDouble(stack, top - 1), popDouble(stack, top - 3))); break;
    public static void dcmpg(long[] primitives, Object[] symbolic, int top) {
        double c1 = BytecodeNode.popDouble(primitives, top - 1);
        double c2 = BytecodeNode.popDouble(primitives, top - 3);
        int concResult = BytecodeNode.compareDoubleGreater(c1, c2);
        BytecodeNode.putInt(primitives, top - 4, concResult);
        putSymbolic(symbolic, top-4, binarySymbolicOp(OperatorComparator.DCMPG, PrimitiveTypes.DOUBLE,
                c2, c1, concResult, popSymbolic(symbolic, top - 3), popSymbolic(symbolic, top - 1)));
    }

    // branching helpers ...

    public static boolean takeBranchPrimitive1(long[] primitives, Object[] symbolic, int top, int opcode) {
        assert IFEQ <= opcode && opcode <= IFLE;

        AnnotatedValue s1 = popSymbolic( symbolic,top -1);
        int c1 = BytecodeNode.popInt(primitives, top - 1);

        boolean takeBranch = true;

        // @formatter:off
        switch (opcode) {
            case IFEQ      : takeBranch = (c1 == 0); break;
            case IFNE      : takeBranch = (c1 != 0); break;
            case IFLT      : takeBranch = (c1  < 0); break;
            case IFGE      : takeBranch = (c1 >= 0); break;
            case IFGT      : takeBranch = (c1  > 0); break;
            case IFLE      : takeBranch = (c1 <= 0); break;
            default        :
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("expecting IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE");
        }

        if (s1 != null) {
            Expression expr = null;

            if (Expression.isBoolean(s1.symbolic())) {
                switch (opcode) {
                    case IFEQ: expr = !takeBranch ? s1.symbolic() : new ComplexExpression(OperatorComparator.BNEG, s1.symbolic()); break;
                    case IFNE: expr =  takeBranch ? s1.symbolic() : new ComplexExpression(OperatorComparator.BNEG, s1.symbolic()); break;
                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw EspressoError.shouldNotReachHere("only defined for IFEQ and IFNE so far");
                }
            }
            else if (Expression.isCmpExpression(s1.symbolic())) {
                ComplexExpression ce = (ComplexExpression) s1.symbolic();
                OperatorComparator op = null;
                switch (ce.getOperator()) {
                    case LCMP:
                        // 0 if x == y; less than 0 if x < y; greater than 0 if x > y
                        switch (opcode) {
                            case IFEQ      : op = takeBranch ? OperatorComparator.BVEQ : OperatorComparator.BVNE; break;
                            case IFNE      : op = takeBranch ? OperatorComparator.BVNE : OperatorComparator.BVEQ; break;
                            case IFLT      : op = takeBranch ? OperatorComparator.BVLT : OperatorComparator.BVGE; break;
                            case IFGE      : op = takeBranch ? OperatorComparator.BVGE : OperatorComparator.BVLT; break;
                            case IFGT      : op = takeBranch ? OperatorComparator.BVGT : OperatorComparator.BVLE; break;
                            case IFLE      : op = takeBranch ? OperatorComparator.BVLE : OperatorComparator.BVGT; break;
                        }
                        break;
                    case FCMPL:
                    case FCMPG:
                    case DCMPL:
                    case DCMPG:
                        // 0 if x == y; less than 0 if x < y; greater than 0 if x > y
                        switch (opcode) {
                            case IFEQ      :
                            case IFNE      : op = OperatorComparator.FPEQ; break;
                            case IFLT      : op = takeBranch ? OperatorComparator.FPLT : OperatorComparator.FPGE; break;
                            case IFGE      : op = takeBranch ? OperatorComparator.FPGE : OperatorComparator.FPLT; break;
                            case IFGT      : op = takeBranch ? OperatorComparator.FPGT : OperatorComparator.FPLE; break;
                            case IFLE      : op = takeBranch ? OperatorComparator.FPLE : OperatorComparator.FPGT; break;
                        }
                        break;                
                }
                expr = new ComplexExpression(op, ce.getSubExpressions());
                if (!ce.getOperator().equals(OperatorComparator.LCMP) && (
                        (opcode == IFEQ && !takeBranch) || (opcode == IFNE && takeBranch))) {
                    expr = new ComplexExpression(OperatorComparator.BNEG, expr);
                }
            }
            else {
                switch (opcode) {
                    case IFEQ      : expr = new ComplexExpression(OperatorComparator.BVEQ, s1.symbolic(), Constant.INT_ZERO); break;
                    case IFNE      : expr = new ComplexExpression(OperatorComparator.BVNE, s1.symbolic(), Constant.INT_ZERO); break;
                    case IFLT      : expr = new ComplexExpression(OperatorComparator.BVLT, s1.symbolic(), Constant.INT_ZERO);  break;
                    case IFGE      : expr = new ComplexExpression(OperatorComparator.BVGE, s1.symbolic(), Constant.INT_ZERO);  break;
                    case IFGT      : expr = new ComplexExpression(OperatorComparator.BVGT, s1.symbolic(), Constant.INT_ZERO);  break;
                    case IFLE      : expr = new ComplexExpression(OperatorComparator.BVLE, s1.symbolic(), Constant.INT_ZERO);  break;
                    default        :
                        CompilerDirectives.transferToInterpreter();
                        throw EspressoError.shouldNotReachHere("expecting IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE");
                }
                expr = takeBranch ? expr : new ComplexExpression(OperatorComparator.BNEG, expr);
            }

            addTraceElement(new PathCondition(expr, takeBranch ? 1 : 0, 2));
        }
        return takeBranch;
    }

    public static boolean takeBranch2(long[] primitives, Object[] symbolic, int top, int opcode) {
        assert IF_ICMPEQ <= opcode && opcode <= IF_ICMPLE;
        AnnotatedValue s1 = popSymbolic( symbolic,top -1);
        AnnotatedValue s2 = popSymbolic( symbolic,top -2);
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);

        // concrete
        boolean takeBranch = true;

        switch (opcode) {
            case IF_ICMPEQ : takeBranch = (c1 == c2); break;
            case IF_ICMPNE : takeBranch = (c1 != c2); break;
            case IF_ICMPLT : takeBranch = (c1  > c2); break;
            case IF_ICMPGE : takeBranch = (c1 <= c2); break;
            case IF_ICMPGT : takeBranch = (c1  < c2); break;
            case IF_ICMPLE : takeBranch = (c1 >= c2); break;
            default        :
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("non-branching bytecode");
        }

        // symbolic
        if (s1 != null || s2 != null) {
            if (s1 == null) s1 = AnnotatedValue.fromConstant(PrimitiveTypes.INT, c1);
            if (s2 == null) s2 = AnnotatedValue.fromConstant(PrimitiveTypes.INT, c2);

            Expression expr = null;
            switch (opcode) {
                case IF_ICMPEQ : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.BVEQ :
                        OperatorComparator.BVNE, s1.symbolic(), s2.symbolic()); break;
                case IF_ICMPNE : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.BVNE :
                        OperatorComparator.BVEQ, s1.symbolic(), s2.symbolic()); break;
                case IF_ICMPLT : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.BVGT :
                        OperatorComparator.BVLE, s1.symbolic(), s2.symbolic()); break;
                case IF_ICMPGE : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.BVLE :
                        OperatorComparator.BVGT, s1.symbolic(), s2.symbolic()); break;
                case IF_ICMPGT : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.BVLT :
                        OperatorComparator.BVGE, s1.symbolic(), s2.symbolic()); break;
                case IF_ICMPLE : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.BVGE :
                        OperatorComparator.BVLT, s1.symbolic(), s2.symbolic()); break;
                default        :
                    CompilerDirectives.transferToInterpreter();
                    // FIXME: replace EspressoError.shouldNotReachHere calls with stoprecording(...) to make analysis shut down properly
                    throw EspressoError.shouldNotReachHere("non-branching bytecode");
            }

            PathCondition pc = new PathCondition(expr, takeBranch ? 1 : 0, 2);
            addTraceElement(pc);
        }
        return takeBranch;
    }

    public static Object stringEquals(StaticObject self, StaticObject other, Meta meta) {
        // FIXME: we currently do not track conditions for exceptions inside equals!
        // FIXME: could be better to use method handle from meta?
        //boolean areEqual = (boolean) meta.java_lang_String_equals.invokeDirect(self, other);
        boolean areEqual = meta.toHostString(self).equals(meta.toHostString(other));
        if (self.getConcolicId() < 0 && other.getConcolicId() < 0) {
            return areEqual;
        }

        Expression exprSelf = (self.getConcolicId() < 0) ?
                Constant.fromConcreteValue(meta.toHostString(self)) :
                symbolicObjects.get(self.getConcolicId())[0].symbolic();
        Expression exprOther = (other.getConcolicId() < 0) ?
                Constant.fromConcreteValue(meta.toHostString(other)) :
                symbolicObjects.get(other.getConcolicId())[0].symbolic();

        Expression symbolic = new ComplexExpression(OperatorComparator.STRINGEQ, exprSelf, exprOther);

        return new AnnotatedValue(areEqual, symbolic);
    }

    // --------------------------------------------------------------------------
    //
    // symbolic stack and var functions

    public static AnnotatedValue popSymbolic(Object[] symbolic, int slot) {
        if (symbolic[slot] == null || !(symbolic[slot] instanceof AnnotatedValue)) {
            return null;
        }
        AnnotatedValue result = (AnnotatedValue) symbolic[slot];
        symbolic[slot] = null;
        return result;
    }

    public static AnnotatedValue peekSymbolic(Object[] symbolic, int slot) {
        if (symbolic[slot] == null || !(symbolic[slot] instanceof AnnotatedValue)) {
            return null;
        }
        AnnotatedValue result = (AnnotatedValue) symbolic[slot];
        return result;
    }

    public static void putSymbolic(Object[] symbolic, int slot, AnnotatedValue value) {
        symbolic[slot] = value;
    }

    public static void setLocalSymbolic(Object[] symbolic, int slot, AnnotatedValue value) {
        symbolic[symbolic.length - 1 - slot] = value;
    }

    public static AnnotatedValue getLocalSymbolic(Object[] symbolic, int slot) {
        assert symbolic[symbolic.length - 1 - slot] instanceof AnnotatedValue;
        return (AnnotatedValue) symbolic[symbolic.length - 1 - slot];
    }

}
