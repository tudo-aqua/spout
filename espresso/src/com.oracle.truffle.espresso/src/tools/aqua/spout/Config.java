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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeSpecial;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeSpecialNodeGen;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import tools.aqua.concolic.ConcolicAnalysis;
import tools.aqua.concolic.ConcolicNumericAnalysis;
import tools.aqua.concolic.ConstructorCondition;
import tools.aqua.concolic.PathCondition;
import tools.aqua.concolic.SymbolDeclaration;
import tools.aqua.smt.AuxiliaryVariable;
import tools.aqua.smt.ComplexExpression;
import tools.aqua.smt.Expression;
import tools.aqua.smt.OperatorComparator;
import tools.aqua.smt.Types;
import tools.aqua.smt.Variable;
import tools.aqua.spout.analyses.NumericAnalysis;
import tools.aqua.taint.NumericTaintAnalysis;
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

    /**
     * I split numeric functions in Wrappper from the bytecode for now.
     * We will have to rethink how to make this smarter in the future.
     */

    private ConcolicNumericAnalysis concolicNumericAnalysis = null;
    private NumericTaintAnalysis numericTaintAnalysis = null;

    private int concolicIdx = 0;

    private int taintIdx = 1;

    private int annotationLength = 2;

    private boolean b64ConstructorConfig = false;

    public Config() {
        this.trace = new Trace();
    }

    private void configureAnalysis() {
        if (hasConcolicAnalysis) {
            this.concolicAnalysis = new ConcolicAnalysis(this);
            this.concolicNumericAnalysis = new ConcolicNumericAnalysis();
        } else {
            this.concolicAnalysis = null;
            this.concolicIdx = -1;
            this.taintIdx = 0;
            this.annotationLength--;
        }

        if (!taintType.equals(TaintType.OFF)) {
            this.taintAnalysis = new TaintAnalysis(this);
            this.numericTaintAnalysis = new NumericTaintAnalysis();
        } else {
            this.taintAnalysis = null;
            this.taintIdx = -1;
            this.annotationLength--;
        }

        Annotations.configure(this.annotationLength);
        // native image precautions ...
        OperatorComparator.initialize();
    }

    void printAnalysisConfig() {
        SPouT.log("Concolic Analysis: " + hasConcolicAnalysis);
        SPouT.log("Constructor Summary: " + constructorSummary);
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
        SPouT.log("Seeded Object Values: " + Arrays.toString(seedObjectValues));
    }

    public void parseAnalysesConfig(String config, Meta meta) {
        if (!config.trim().isEmpty()) {
            String[] paramsGroups = config.trim().split(" "); // not in base64
            for (String paramGroup : paramsGroups) {
                String[] keyValue = paramGroup.split(":"); // not in base64
                String value = keyValue[1].trim();
                switch (keyValue[0]) {
                    case "concolic.execution":
                        parseConcolic(value);
                        break;
                    case "concolic.constructor.summary":
                        parseSummary(value);
                        break;
                    case "taint.flow":
                        parseTaint(value);
                        break;

                }
            }
        }
        configureAnalysis();
    }

    public void parseConcolicValues(String config, Meta meta) {
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
                case "concolic.constructors":
                    seedObjectValues = vals;
                    b64ConstructorConfig = b64;
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
    
    private void parseConcolic(String valsAsStr) {
        hasConcolicAnalysis = Boolean.valueOf(valsAsStr.trim());
    }

    private void parseSummary(String valsAsStr) {
        constructorSummary = Boolean.valueOf(valsAsStr.trim());
    }

    private void parseTaint(String valsAsStr) {
        taintType = TaintType.valueOf(valsAsStr.trim());
    }

    // --------------------------------------------------------------------------
    //
    // Section symbolic values

    private int[] seedsIntValues = new int[] {};
    private int countIntSeeds = 0;

    public AnnotatedValue nextSymbolicBoolean() {
        boolean concrete = false;
        if (countBooleanSeeds < seedsBooleanValues.length) {
            concrete = seedsBooleanValues[countBooleanSeeds];
        }
        Variable symbolic = new Variable(Types.BOOL, countBooleanSeeds++);
        Object[] annotations = new Object[annotationLength];
        annotations[concolicIdx] = symbolic;
        return new AnnotatedValue(concrete, annotations);
    }
    public AnnotatedValue nextSymbolicByte() {
        int concrete = 0;
        if (countByteSeeds < seedsByteValues.length) {
            concrete = seedsByteValues[countByteSeeds];
        }
        Variable symbolic = new Variable(Types.BYTE, countByteSeeds++);
        Object[] annotations = new Object[annotationLength];
        annotations[concolicIdx] = symbolic;
        return new AnnotatedValue(concrete, annotations);
    }
    public AnnotatedValue nextSymbolicChar() {
        int concrete = 0;
        if (countCharSeeds < seedsCharValues.length) {
            concrete = seedsCharValues[countCharSeeds];
        }
        Variable symbolic = new Variable(Types.CHAR, countCharSeeds++);
        Object[] annotations = new Object[annotationLength];
        annotations[concolicIdx] = symbolic;
        return new AnnotatedValue(concrete, annotations);
    }
    public AnnotatedValue nextSymbolicShort() {
        int concrete = 0;
        if (countShortSeeds < seedsShortValues.length) {
            concrete = seedsShortValues[countShortSeeds];
        }
        Variable symbolic = new Variable(Types.SHORT, countShortSeeds++);
        Object[] annotations = new Object[annotationLength];
        annotations[concolicIdx] = symbolic;
        return new AnnotatedValue(concrete, annotations);
    }

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
    public AnnotatedValue nextSymbolicLong() {
        long concrete = 0l;
        if (countLongSeeds < seedsLongValues.length) {
            concrete = seedsLongValues[countLongSeeds];
        }
        Variable symbolic = new Variable(Types.LONG, countLongSeeds++);
        Object[] annotations = new Object[annotationLength];
        annotations[concolicIdx] = symbolic;
        AnnotatedValue a = new AnnotatedValue(concrete, annotations);
        return a;
    }

    public AnnotatedValue nextSymbolicFloat() {
        float concrete = 0.0f;
        if (countFloatSeeds < seedsFloatValues.length) {
            concrete = seedsFloatValues[countFloatSeeds];
        }
        Variable symbolic = new Variable(Types.FLOAT, countFloatSeeds++);
        Object[] annotations = new Object[annotationLength];
        annotations[concolicIdx] = symbolic;
        return new AnnotatedValue(concrete, annotations);
    }

    public AnnotatedValue nextSymbolicDouble() {
        double concrete = 0.0;
        if (countDoubleSeeds < seedsDoubleValues.length) {
            concrete = seedsDoubleValues[countDoubleSeeds];
        }
        Variable symbolic = new Variable(Types.DOUBLE, countDoubleSeeds++);
        Object[] annotations = new Object[annotationLength];
        annotations[concolicIdx] = symbolic;
        return new AnnotatedValue(concrete, annotations);
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

    // todo: update documentation
    /**
     * Takes a String from -Dconcolic.constructors and creates a {{@link StaticObject}}.
     * The Strings of -Dconcolic.constructors are in the format {QUALIFIED_CLASS_NAME}|{CONSTRUCTOR_SIGNATURE}.
     * Both QUALIFIED_CLASS_NAME and CONSTRUCTOR_SIGNATURE follow the <b>class File Format</b><br>
     * (See JVM Specification chapter 2 The Structure of the Java Virtual Machine)
     *
     * <b>example:</b><br>
     * Ljava/lang/StringBuilder;|()V
     * Here the default constructor ()V from the class Ljava/lang/StringBuilder is invoked
     *
     * @return Parsed {{@link StaticObject}}
     */
    @CompilerDirectives.TruffleBoundary
    public StaticObject nextSymbolicObject(Meta meta) {
        String constructorConfig = "<>null|NULL";
        if(countObjectSeeds < seedObjectValues.length) {
            constructorConfig = b64ConstructorConfig ? b64decode(seedObjectValues[countObjectSeeds]) : seedObjectValues[countObjectSeeds];
        }

        int endIdx = findMatchingIndex(constructorConfig, '<', '>');
        String label =  constructorConfig.substring(1, endIdx);
        constructorConfig = constructorConfig.substring(endIdx + 1);

        Variable symbolicObjectId = new Variable(Types.OBJECT, countObjectSeeds);
        AuxiliaryVariable objectCreationError = new AuxiliaryVariable( symbolicObjectId + ".err", Types.STRING);
        countObjectSeeds++;
        Expression errorExpr = new ComplexExpression(OperatorComparator.STRINGEQ,
                objectCreationError, Expression.fromConstant(Types.STRING, label));

        trace.addElement(new SymbolDeclaration(objectCreationError));
        if (constructorSummary) {
            trace.addElement(new ConstructorCondition(errorExpr));
        } else {
            trace.addElement(new PathCondition(errorExpr, 0,2));
        }

        StringBuilder logger = new StringBuilder();
        logger.append("<").append(label).append(">");
        StaticObject obj = parseObjectValue(constructorConfig, meta, b64ConstructorConfig, logger);

        if (constructorSummary) {
            AuxiliaryVariable var = new AuxiliaryVariable(symbolicObjectId + ".init", Types.STRING);
            Expression.fromConstant(Types.STRING, logger.toString());
            trace.addElement(new ConstructorCondition(
                    new ComplexExpression(OperatorComparator.STRINGEQ, var,
                            Expression.fromConstant(Types.STRING, logger.toString()))));
        }

        Annotations objectDescription = Annotations.emptyArray();
        objectDescription.set(getConcolicIdx(), symbolicObjectId);
        Annotations.setObjectAnnotation(obj, objectDescription);
        return obj;
    }

   @CompilerDirectives.TruffleBoundary
    private StaticObject parseObjectValue(String value, Meta meta, boolean b64, StringBuilder call) {
        //Extract className and constructor_signature etc.
        if (value.equals("null|NULL")) {
            call.append(value);
            return StaticObject.createNull(null);
        }

        String[] split = value.split("\\|", 3);
        String className = split[0];
        String constructorSignature = split[1];

        if (className.equals("Ljava/lang/String;")) {
            // TODO: simply call String handling?
            SPouT.stopRecording("Strings are currently not supported as symbolic objects.", meta);
        }

        Klass klass = getKlass(className, meta);
        assert klass != null;
        Method constructor = getConstructor(klass, constructorSignature, meta);
        assert constructor != null;
        call.append(className).append("|").append(constructorSignature).append("|");

        Object[] constructorCallparams = new Object[constructor.getArgumentCount()];
        Klass[] paramTypes = constructor.resolveParameterKlasses();
        String paramListAsString = split[2];
        for (int i = 0; i < paramTypes.length; i++) {
            call.append("{");
            int endIdx = findMatchingIndex(paramListAsString, '{', '}');
            String paramAsString = paramListAsString.substring(1, endIdx);
            paramListAsString = paramListAsString.substring(endIdx);
            if (paramTypes[i].isPrimitive()) {
                constructorCallparams[i + 1] = parsePrimitiveValue(paramAsString, (PrimitiveKlass) paramTypes[i], meta, b64);
                Object a = Annotations.annotation(AnnotatedValue.svalue(constructorCallparams[i + 1]), concolicIdx);
                if (a != null) call.append(a);
            } else {
                constructorCallparams[i + 1] = parseObjectValue(paramAsString, meta, b64, call);
            }
            call.append("}");
        }

        // instantiate object
        StaticObject staticObject = klass.allocateInstance();
        // for some reason we have to do it here explicitly ...
        Annotations.initObjectAnnotations(staticObject);
        constructorCallparams[0] = staticObject;
        //SPouT.log("constructor call: " + Arrays.toString(constructorCallparams));
        InvokeSpecial invokeSpecial = InvokeSpecialNodeGen.create(constructor);
        invokeSpecial.execute(constructorCallparams);
        return staticObject;
    }

    private Object parsePrimitiveValue(String value, PrimitiveKlass klass, Meta meta, boolean b64) {
        if (b64) {
            value = b64decode(value);
        }
        switch (klass.getPrimitiveJavaKind()) {
            case Boolean -> {
                return constructorSummary ? SPouT.nextSymbolicBoolean() : Boolean.parseBoolean(value);
            }
            case Byte -> {
                return constructorSummary ? SPouT.nextSymbolicByte() : Byte.parseByte(value);
            }
            case Short -> {
                return constructorSummary ? SPouT.nextSymbolicShort() : Short.parseShort(value);
            }
            case Char -> {
                return constructorSummary ? SPouT.nextSymbolicChar() : value.charAt(0);
            }
            case Int -> {
                return constructorSummary ? SPouT.nextSymbolicInt() : Integer.parseInt(value);
            }
            case Float -> {
                return constructorSummary ? SPouT.nextSymbolicFloat() : Float.parseFloat(value);
            }
            case Long -> {
                return constructorSummary ? SPouT.nextSymbolicLong() : Long.parseLong(value);
            }
            case Double -> {
                return constructorSummary ? SPouT.nextSymbolicDouble() : Double.parseDouble(value);
            }
            default -> {
                SPouT.stopRecording("unsupported primitive kind.", meta);
            }
        }
        // unreachable code
        return null;
    }

    private static int findMatchingIndex(String str, char open, char close) {
        assert str.length() > 1 && str.charAt(0) == open;
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == open) { count++; } else if (c == close) { count--; }
            if (count == 0) { return i; }
        }
        return -1;
    }

    public static Klass getKlass(String fqn, Meta meta) {
        Symbol<Type> type = meta.getTypes().fromClassGetName(fqn);
        if (type == null) {
            SPouT.stopRecording("loading symbol for classname failed.", meta);
        }

        StaticObject classLoader = (StaticObject) meta.java_lang_ClassLoader_getSystemClassLoader.invokeDirect();
        Klass klass = meta.loadKlassOrNull(type,
                classLoader,  //No classLoader means that the BOOT-Classloader is used
                StaticObject.NULL); //protectionDomain ???

        if (klass == null) {
            SPouT.stopRecording("loading klass failed", meta);
        }
        return klass;
    }

    public static Method getConstructor(Klass klass, String signatureString, Meta meta) {

        Symbol<Signature> signature = klass.getSignatures().lookupValidSignature(signatureString);
        if (signature == null) {
            SPouT.stopRecording("loading symbol for signature failed.", meta);
        }

        Method[] declaredConstructors = klass.getDeclaredConstructors();
        for (Method declaredConstructor : declaredConstructors) {
            if (declaredConstructor.getRawSignature().equals(signature)) {
                return declaredConstructor;
            }
        }
        SPouT.stopRecording("no constructor found for signature.", meta);
        return null; // cannot be reached
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

    private String[] seedObjectValues = new String[] {};
    private int countObjectSeeds = 0;

    public int getCountObjectSeeds() {
        return countObjectSeeds;
    }

    private boolean constructorSummary = false;

    public boolean isConstructorSummary() {
        return constructorSummary;
    }

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

    public NumericAnalysis<?>[] getNumericAnalyses() {
        NumericAnalysis<?>[] analyses = new NumericAnalysis<?>[this.annotationLength];
        if (hasConcolicAnalysis()) analyses[this.concolicIdx] = this.concolicNumericAnalysis;
        if (hasTaintAnalysis()) analyses[this.taintIdx] = this.numericTaintAnalysis;
        return analyses;
    }

    // This should be a record, but SPouT cannot compile records yet.
    public static class SymbolicStringValue {
        public String concrete;
        public Variable symbolic;
        public SymbolicStringValue(String c, Variable s){
            concrete = c;
            symbolic = s;
        }
    }

}
