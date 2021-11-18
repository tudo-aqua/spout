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

package tools.aqua.concolic;

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
import static tools.aqua.concolic.Constant.INT_ZERO;
import static tools.aqua.concolic.Constant.NAT_ZERO;
import static tools.aqua.concolic.OperatorComparator.BAND;
import static tools.aqua.concolic.OperatorComparator.BNEG;
import static tools.aqua.concolic.OperatorComparator.BOR;
import static tools.aqua.concolic.OperatorComparator.BV2NAT;
import static tools.aqua.concolic.OperatorComparator.BVEQ;
import static tools.aqua.concolic.OperatorComparator.BVGE;
import static tools.aqua.concolic.OperatorComparator.BVGT;
import static tools.aqua.concolic.OperatorComparator.BVLE;
import static tools.aqua.concolic.OperatorComparator.GE;
import static tools.aqua.concolic.OperatorComparator.GT;
import static tools.aqua.concolic.OperatorComparator.IADD;
import static tools.aqua.concolic.OperatorComparator.LE;
import static tools.aqua.concolic.OperatorComparator.LT;
import static tools.aqua.concolic.OperatorComparator.NAT2BV32;
import static tools.aqua.concolic.OperatorComparator.NATADD;
import static tools.aqua.concolic.OperatorComparator.SAT;
import static tools.aqua.concolic.OperatorComparator.SCONCAT;
import static tools.aqua.concolic.OperatorComparator.SCONTAINS;
import static tools.aqua.concolic.OperatorComparator.SLENGTH;
import static tools.aqua.concolic.OperatorComparator.SSUBSTR;
import static tools.aqua.concolic.OperatorComparator.STOCODE;
import static tools.aqua.concolic.OperatorComparator.STOLOWER;
import static tools.aqua.concolic.OperatorComparator.STOUPPER;
import static tools.aqua.concolic.OperatorComparator.STRINGEQ;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

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
    } else {
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
      // FIXME: can be more optimal by making a path element "assumption"
      addTraceElement(new Assumption(a.symbolic(), a.asBoolean()));
    } else {
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

  private static int[] seedsByteValues = new int[] {};
  private static int countByteSeeds = 0;

  public static AnnotatedValue nextSymbolicByte() {
    int concrete = 0;
    if (countByteSeeds < seedsByteValues.length) {
      concrete = seedsByteValues[countByteSeeds];
    }
    Variable symbolic = new Variable(PrimitiveTypes.BYTE, countByteSeeds);
    countByteSeeds++;
    addTraceElement(new SymbolDeclaration(symbolic));
    // upcast byte to int
    return new AnnotatedValue(concrete, new ComplexExpression(OperatorComparator.B2I, symbolic));
  }

  private static int[] seedsCharValues = new int[] {};
  private static int countCharSeeds = 0;

  public static AnnotatedValue nextSymbolicChar() {
    int concrete = 0;
    if (countCharSeeds < seedsCharValues.length) {
      concrete = seedsCharValues[countCharSeeds];
    }
    Variable symbolic = new Variable(PrimitiveTypes.CHAR, countCharSeeds);
    countCharSeeds++;
    addTraceElement(new SymbolDeclaration(symbolic));
    // upcast char to int
    return new AnnotatedValue(concrete, new ComplexExpression(OperatorComparator.C2I, symbolic));
  }

  private static int[] seedsShortValues = new int[] {};
  private static int countShortSeeds = 0;

  public static AnnotatedValue nextSymbolicShort() {
    int concrete = 0;
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
    if (false) {
      stopRecording("Analysis of String constraints is not supported, yet.", meta);
    }

    String concreteHost = "";
    if (countStringSeeds < seedStringValues.length) {
      concreteHost = seedStringValues[countStringSeeds];
    }
    StaticObject concrete = meta.toGuestString(concreteHost);
    Variable symbolic = new Variable(PrimitiveTypes.STRING, countStringSeeds);
    AnnotatedValue a = new AnnotatedValue(concrete, symbolic);
    AnnotatedValue length =
        new AnnotatedValue(
            concreteHost.length(),
            new ComplexExpression(
                OperatorComparator.NAT2BV32, new ComplexExpression(SLENGTH, symbolic)));
    concrete.setConcolicId(symbolicObjects.size());

    int lengthAnnotations = ((ObjectKlass) concrete.getKlass()).getFieldTable().length + 2;
    AnnotatedValue[] annotation = new AnnotatedValue[lengthAnnotations];
    annotation[annotation.length - 2] = a;
    annotation[annotation.length - 1] = length;
    symbolicObjects.add(annotation);
    countStringSeeds++;
    addTraceElement(new SymbolDeclaration(symbolic));
    return concrete;
  }

  // --------------------------------------------------------------------------
  //
  // Section symbolic fields of objects / arrays

  private static ArrayList<AnnotatedValue[]> symbolicObjects = new ArrayList<>();

  public static void setFieldAnnotation(StaticObject obj, Field f, AnnotatedValue a) {
    if (f.isStatic()) {
      obj = f.getDeclaringKlass().getStatics();
    }

    if (obj.getConcolicId() < 0 && a == null) {
      return;
    }
    if (obj.getConcolicId() < 0) {
      obj.setConcolicId(symbolicObjects.size());
      symbolicObjects.add(
          new AnnotatedValue
              [f.isStatic()
                  ? f.getDeclaringKlass().getStaticFieldTable().length
                  : ((ObjectKlass) obj.getKlass()).getFieldTable().length]);
    }
    AnnotatedValue[] annotations = symbolicObjects.get(obj.getConcolicId());
    annotations[f.getSlot()] = a;
  }

  public static void getFieldAnnotation(StaticObject obj, Field f, Object[] symbolic, int at) {
    if (f.isStatic()) {
      obj = f.getDeclaringKlass().getStatics();
    }

    if (obj.getConcolicId() < 0) {
      return;
    }
    AnnotatedValue[] annotations = symbolicObjects.get(obj.getConcolicId());
    AnnotatedValue a = annotations[f.getSlot()];
    putSymbolic(symbolic, at, a);
  }

  public static void getArrayAnnotation(
      StaticObject array, int concIndex, Object[] symbolic, int from, int to) {
    AnnotatedValue symbIndex = popSymbolic(symbolic, from);
    if (!checkArrayAccessPathConstraint(array, concIndex, symbIndex) || array.getConcolicId() < 0) {
      return;
    }
    AnnotatedValue[] annotations = symbolicObjects.get(array.getConcolicId());
    AnnotatedValue a = annotations[concIndex];
    putSymbolic(symbolic, to, a);
  }

  public static void setArrayAnnotation(
      StaticObject array, int concIndex, Object[] symbolic, int fromValue, int fromIndex) {
    AnnotatedValue symbIndex = popSymbolic(symbolic, fromIndex);
    AnnotatedValue symbValue = popSymbolic(symbolic, fromValue);
    if (!checkArrayAccessPathConstraint(array, concIndex, symbIndex) || symbValue == null) {
      return;
    }

    if (array.getConcolicId() < 0) {
      array.setConcolicId(symbolicObjects.size());
      symbolicObjects.add(new AnnotatedValue[array.length() + 1]);
    }
    AnnotatedValue[] annotations = symbolicObjects.get(array.getConcolicId());
    annotations[concIndex] = symbValue;
  }

  private static boolean checkArrayAccessPathConstraint(
      StaticObject array, int concIndex, AnnotatedValue annotatedIndex) {
    assert array.isArray();
    int concLen = array.length();
    boolean safe = 0 <= concIndex && concIndex < concLen;
    if ((array.getConcolicId() >= 0 && symbolicObjects.get(array.getConcolicId())[concLen] != null)
        || annotatedIndex != null) {

      Expression symbIndex =
          (annotatedIndex == null)
              ? Constant.fromConcreteValue(concIndex)
              : annotatedIndex.symbolic();
      Expression symbLen;
      if (array.getConcolicId() < 0) {
        symbLen = Constant.fromConcreteValue(concLen);
      } else {
        AnnotatedValue a = symbolicObjects.get(array.getConcolicId())[concLen];
        symbLen = (a != null) ? a.symbolic() : Constant.fromConcreteValue(concLen);
      }

      Expression arrayBound =
          new ComplexExpression(
              OperatorComparator.BAND,
              new ComplexExpression(BVLE, INT_ZERO, symbIndex),
              new ComplexExpression(OperatorComparator.BVLT, symbIndex, symbLen));

      addTraceElement(
          new PathCondition(
              safe ? arrayBound : new ComplexExpression(BNEG, arrayBound), safe ? 1 : 0, 2));
    }
    return safe;
  }

  public static AnnotatedValue arrayLength(StaticObject array) {
    if (array.getConcolicId() < 0) {
      return null;
    }
    AnnotatedValue[] annotatedValues = symbolicObjects.get(array.getConcolicId());
    return annotatedValues[annotatedValues.length - 1];
  }

  // --------------------------------------------------------------------------
  //
  // symbolic array sizes

  public static void newArray(
      long[] primitives,
      Object[] symbolic,
      byte jvmPrimitiveType,
      int top,
      Meta meta,
      BytecodeNode bytecodeNode) {
    int concLen = BytecodeNode.popInt(primitives, top - 1);
    AnnotatedValue symbLen = popSymbolic(symbolic, top - 1);
    addArrayCreationPathConstraint(concLen, symbLen);
    StaticObject array =
        InterpreterToVM.allocatePrimitiveArray(jvmPrimitiveType, concLen, meta, bytecodeNode);
    if (symbLen != null) {
      array.setConcolicId(symbolicObjects.size());
      symbolicObjects.add(new AnnotatedValue[concLen + 1]);
      symbolicObjects.get(array.getConcolicId())[concLen] = symbLen;
    }
    BytecodeNode.putObject(symbolic, top - 1, array);
  }

  @CompilerDirectives.TruffleBoundary
  public static int newMultiArray(
      long[] primitives,
      Object[] refs,
      int top,
      Klass klass,
      int allocatedDimensions,
      InterpreterToVM ivm) {
    assert klass.isArray();
    CompilerAsserts.partialEvaluationConstant(allocatedDimensions);
    CompilerAsserts.partialEvaluationConstant(klass);
    int[] dimensions = new int[allocatedDimensions];
    AnnotatedValue[] symdim = new AnnotatedValue[allocatedDimensions];
    int zeroDimOrError = allocatedDimensions;
    for (int i = 0; i < allocatedDimensions; ++i) {
      dimensions[i] = BytecodeNode.popInt(primitives, top - allocatedDimensions + i);
      symdim[i] = Concolic.popSymbolic(refs, top - allocatedDimensions + i);
      if (i < zeroDimOrError) {
        Concolic.addArrayCreationPathConstraint(dimensions[i], symdim[i]);
        if (dimensions[i] <= 0) {
          zeroDimOrError = i;
        }
      }
    }
    StaticObject array = ivm.newMultiArray(((ArrayKlass) klass).getComponentType(), dimensions);
    for (int i = 0; i < zeroDimOrError; ++i) {
      if (symdim[i] != null) {
        List<StaticObject> arrays = getAllArraysAtDepth(array, i);
        for (StaticObject arr : arrays) {
          arr.setConcolicId(symbolicObjects.size());
          int dLength = dimensions[i];
          symbolicObjects.add(new AnnotatedValue[dLength + 1]);
          symbolicObjects.get(arr.getConcolicId())[dLength] = symdim[i];
        }
      }
    }
    BytecodeNode.putObject(refs, top - allocatedDimensions, array);
    return -allocatedDimensions; // Does not include the created (pushed) array.
  }

  private static List<StaticObject> getAllArraysAtDepth(StaticObject array, int depth) {
    if (depth == 0) {
      return Collections.singletonList(array);
    }
    List<StaticObject> lower = getAllArraysAtDepth(array, depth - 1);
    List<StaticObject> arrays = new LinkedList<>();
    for (StaticObject a : lower) {
      arrays.addAll(Arrays.asList(a.unwrap()));
    }
    return arrays;
  }

  public static void addArrayCreationPathConstraint(int conclen, AnnotatedValue symbLen) {
    if (symbLen == null) {
      return;
    }
    boolean holds = (0 <= conclen);
    Expression lengthConstraint = new ComplexExpression(BVLE, INT_ZERO, symbLen.symbolic());

    addTraceElement(
        new PathCondition(
            holds ? lengthConstraint : new ComplexExpression(BNEG, lengthConstraint),
            holds ? 1 : 0,
            2));
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

  private static void parseBools(String[] valsAsStr, boolean b64) {
    seedsBooleanValues = new boolean[valsAsStr.length];
    for (int i = 0; i < valsAsStr.length; i++) {
      seedsBooleanValues[i] =
          Boolean.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
    }
  }

  private static void parseBytes(String[] valsAsStr, boolean b64) {
    seedsByteValues = new int[valsAsStr.length];
    for (int i = 0; i < valsAsStr.length; i++) {
      seedsByteValues[i] =
          Integer.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
    }
  }

  private static void parseChars(String[] valsAsStr, boolean b64) {
    seedsCharValues = new int[valsAsStr.length];
    for (int i = 0; i < valsAsStr.length; i++) {
      seedsCharValues[i] =
          Integer.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
    }
  }

  private static void parseShorts(String[] valsAsStr, boolean b64) {
    seedsShortValues = new int[valsAsStr.length];
    for (int i = 0; i < valsAsStr.length; i++) {
      seedsShortValues[i] =
          Integer.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
    }
  }

  private static void parseInts(String[] valsAsStr, boolean b64) {
    seedsIntValues = new int[valsAsStr.length];
    for (int i = 0; i < valsAsStr.length; i++) {
      seedsIntValues[i] =
          Integer.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
    }
  }

  private static void parseLongs(String[] valsAsStr, boolean b64) {
    seedsLongValues = new long[valsAsStr.length];
    for (int i = 0; i < valsAsStr.length; i++) {
      seedsLongValues[i] = Long.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
    }
  }

  private static void parseFloats(String[] valsAsStr, boolean b64) {
    seedsFloatValues = new float[valsAsStr.length];
    for (int i = 0; i < valsAsStr.length; i++) {
      seedsFloatValues[i] =
          Float.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
    }
  }

  private static void parseDoubles(String[] valsAsStr, boolean b64) {
    seedsDoubleValues = new double[valsAsStr.length];
    for (int i = 0; i < valsAsStr.length; i++) {
      seedsDoubleValues[i] =
          Double.valueOf(b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim());
    }
  }

  private static void parseStrings(String[] valsAsStr, boolean b64) {
    seedStringValues = new String[valsAsStr.length];
    for (int i = 0; i < valsAsStr.length; i++) {
      seedStringValues[i] = b64 ? b64decode(valsAsStr[i].trim()) : valsAsStr[i].trim();
    }
  }

  @CompilerDirectives.TruffleBoundary
  public static void endPath() {
    System.out.println("======================== END PATH [BEGIN].");
    printTrace();
    System.out.println("======================== END PATH [END].");
    System.out.println("[ENDOFTRACE]");
    System.out.flush();
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

  private static AnnotatedValue binarySymbolicOp(
      OperatorComparator op,
      PrimitiveTypes typeLeft,
      PrimitiveTypes typeRight,
      Object cLeft,
      Object cRight,
      Object concResult,
      AnnotatedValue sLeft,
      AnnotatedValue sRight) {

    if (sLeft == null && sRight == null) {
      return null;
    }
    if (sLeft == null) sLeft = AnnotatedValue.fromConstant(typeLeft, cLeft);
    if (sRight == null) sRight = AnnotatedValue.fromConstant(typeRight, cRight);
    return new AnnotatedValue(
        concResult, new ComplexExpression(op, sLeft.symbolic(), sRight.symbolic()));
  }

  private static AnnotatedValue binarySymbolicOp(
      OperatorComparator op,
      PrimitiveTypes type,
      Object cLeft,
      Object cRight,
      Object concResult,
      AnnotatedValue sLeft,
      AnnotatedValue sRight) {

    return binarySymbolicOp(op, type, type, cLeft, cRight, concResult, sLeft, sRight);
  }

  private static AnnotatedValue binarySymbolicOp(
      OperatorComparator op, Object concResult, AnnotatedValue s1, Constant c) {
    if (s1 == null) {
      return null;
    }
    return new AnnotatedValue(concResult, new ComplexExpression(op, s1.symbolic(), c));
  }

  // case IADD: putInt(stack, top - 2, popInt(stack, top - 1) + popInt(stack, top - 2)); break;

  public static void iadd(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    int concResult = c1 + c2;
    BytecodeNode.putInt(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.IADD,
            PrimitiveTypes.INT,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 2)));
  }
  // case LADD: putLong(stack, top - 4, popLong(stack, top - 1) + popLong(stack, top - 3)); break;

  public static void ladd(long[] primitives, Object[] symbolic, int top) {
    long c1 = BytecodeNode.popLong(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 3);
    long concResult = c1 + c2;
    BytecodeNode.putLong(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.LADD,
            PrimitiveTypes.LONG,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 3)));
  }
  // case FADD: putFloat(stack, top - 2, popFloat(stack, top - 1) + popFloat(stack, top - 2));
  // break;

  private static AnnotatedValue unarySymbolicOp(
      OperatorComparator op, Object concResult, AnnotatedValue s1) {
    if (s1 == null) {
      return null;
    }
    return new AnnotatedValue(concResult, new ComplexExpression(op, s1.symbolic()));
  }

  public static void fadd(long[] primitives, Object[] symbolic, int top) {
    float c1 = BytecodeNode.popFloat(primitives, top - 1);
    float c2 = BytecodeNode.popFloat(primitives, top - 2);
    float concResult = c1 + c2;
    BytecodeNode.putFloat(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.FADD,
            PrimitiveTypes.FLOAT,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 2)));
  }

  // case DADD: putDouble(stack, top - 4, popDouble(stack, top - 1) + popDouble(stack, top - 3));
  // break;
  public static void dadd(long[] primitives, Object[] symbolic, int top) {
    double c1 = BytecodeNode.popDouble(primitives, top - 1);
    double c2 = BytecodeNode.popDouble(primitives, top - 3);
    double concResult = c1 + c2;
    BytecodeNode.putDouble(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.DADD,
            PrimitiveTypes.DOUBLE,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 3)));
  }

  // case ISUB: putInt(stack, top - 2, -popInt(stack, top - 1) + popInt(stack, top - 2)); break;
  public static void isub(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    int concResult = c2 - c1;
    BytecodeNode.putInt(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.ISUB,
            PrimitiveTypes.INT,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 2),
            popSymbolic(symbolic, top - 1)));
  }

  // case LSUB: putLong(stack, top - 4, -popLong(stack, top - 1) + popLong(stack, top - 3)); break;
  public static void lsub(long[] primitives, Object[] symbolic, int top) {
    long c1 = BytecodeNode.popLong(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 3);
    long concResult = c2 - c1;
    BytecodeNode.putLong(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.LSUB,
            PrimitiveTypes.LONG,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 3),
            popSymbolic(symbolic, top - 1)));
  }

  // case FSUB: putFloat(stack, top - 2, -popFloat(stack, top - 1) + popFloat(stack, top - 2));
  // break;
  public static void fsub(long[] primitives, Object[] symbolic, int top) {
    float c1 = BytecodeNode.popFloat(primitives, top - 1);
    float c2 = BytecodeNode.popFloat(primitives, top - 2);
    float concResult = c2 - c1;
    BytecodeNode.putFloat(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.FSUB,
            PrimitiveTypes.FLOAT,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 2),
            popSymbolic(symbolic, top - 1)));
  }

  // case DSUB: putDouble(stack, top - 4, -popDouble(stack, top - 1) + popDouble(stack, top - 3));
  // break;
  public static void dsub(long[] primitives, Object[] symbolic, int top) {
    double c1 = BytecodeNode.popDouble(primitives, top - 1);
    double c2 = BytecodeNode.popDouble(primitives, top - 3);
    double concResult = c2 - c1;
    BytecodeNode.putDouble(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.DSUB,
            PrimitiveTypes.DOUBLE,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 3),
            popSymbolic(symbolic, top - 1)));
  }

  // case IMUL: putInt(stack, top - 2, popInt(stack, top - 1) * popInt(stack, top - 2)); break;
  public static void imul(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    int concResult = c1 * c2;
    BytecodeNode.putInt(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.IMUL,
            PrimitiveTypes.INT,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 2)));
  }

  // case LMUL: putLong(stack, top - 4, popLong(stack, top - 1) * popLong(stack, top - 3)); break;
  public static void lmul(long[] primitives, Object[] symbolic, int top) {
    long c1 = BytecodeNode.popLong(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 3);
    long concResult = c1 * c2;
    BytecodeNode.putLong(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.LMUL,
            PrimitiveTypes.LONG,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 3)));
  }

  // case FMUL: putFloat(stack, top - 2, popFloat(stack, top - 1) * popFloat(stack, top - 2));
  // break;
  public static void fmul(long[] primitives, Object[] symbolic, int top) {
    float c1 = BytecodeNode.popFloat(primitives, top - 1);
    float c2 = BytecodeNode.popFloat(primitives, top - 2);
    float concResult = c1 * c2;
    BytecodeNode.putFloat(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.FMUL,
            PrimitiveTypes.FLOAT,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 2)));
  }

  // case DMUL: putDouble(stack, top - 4, popDouble(stack, top - 1) * popDouble(stack, top - 3));
  // break;
  public static void dmul(long[] primitives, Object[] symbolic, int top) {
    double c1 = BytecodeNode.popDouble(primitives, top - 1);
    double c2 = BytecodeNode.popDouble(primitives, top - 3);
    double concResult = c1 * c2;
    BytecodeNode.putDouble(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.DMUL,
            PrimitiveTypes.DOUBLE,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 3)));
  }

  private static void checkNonZero(int value, AnnotatedValue a, BytecodeNode bn) {
    if (value != 0) {
      if (a != null) {
        addTraceElement(
            new PathCondition(
                new ComplexExpression(OperatorComparator.BVNE, a.symbolic(), INT_ZERO), 0, 2));
      }
    } else {
      if (a != null) {
        addTraceElement(
            new PathCondition(
                new ComplexExpression(OperatorComparator.BVEQ, a.symbolic(), INT_ZERO), 1, 2));
      }
      bn.enterImplicitExceptionProfile();
      Meta meta = bn.getMeta();
      throw meta.throwExceptionWithMessage(meta.java_lang_ArithmeticException, "/ by zero");
    }
  }

  private static void checkNonZero(long value, AnnotatedValue a, BytecodeNode bn) {
    if (value != 0L) {
      if (a != null) {
        addTraceElement(
            new PathCondition(
                new ComplexExpression(OperatorComparator.BVNE, a.symbolic(), Constant.LONG_ZERO),
                0,
                2));
      }
    } else {
      if (a != null) {
        addTraceElement(
            new PathCondition(
                new ComplexExpression(OperatorComparator.BVEQ, a.symbolic(), Constant.LONG_ZERO),
                1,
                2));
      }
      bn.enterImplicitExceptionProfile();
      Meta meta = bn.getMeta();
      throw meta.throwExceptionWithMessage(meta.java_lang_ArithmeticException, "/ by zero");
    }
  }

  // case IDIV: putInt(stack, top - 2, divInt(checkNonZero(popInt(stack, top - 1)), popInt(stack,
  // top - 2))); break;
  public static void idiv(long[] primitives, Object[] symbolic, int top, BytecodeNode bn) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    checkNonZero(c1, peekSymbolic(symbolic, top - 1), bn);
    int concResult = c2 / c1;
    BytecodeNode.putInt(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.IDIV,
            PrimitiveTypes.INT,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 2),
            popSymbolic(symbolic, top - 1)));
  }

  // case LDIV: putLong(stack, top - 4, divLong(checkNonZero(popLong(stack, top - 1)),
  // popLong(stack, top - 3))); break;
  public static void ldiv(long[] primitives, Object[] symbolic, int top, BytecodeNode bn) {
    long c1 = BytecodeNode.popLong(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 3);
    checkNonZero(c1, peekSymbolic(symbolic, top - 1), bn);
    long concResult = c2 / c1;
    BytecodeNode.putLong(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.LDIV,
            PrimitiveTypes.LONG,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 3),
            popSymbolic(symbolic, top - 1)));
  }

  // case FDIV: putFloat(stack, top - 2, divFloat(popFloat(stack, top - 1), popFloat(stack, top -
  // 2))); break;
  public static void fdiv(long[] primitives, Object[] symbolic, int top) {
    float c1 = BytecodeNode.popFloat(primitives, top - 1);
    float c2 = BytecodeNode.popFloat(primitives, top - 2);
    float concResult = c2 / c1;
    BytecodeNode.putFloat(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.FDIV,
            PrimitiveTypes.FLOAT,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 2),
            popSymbolic(symbolic, top - 1)));
  }

  // case DDIV: putDouble(stack, top - 4, divDouble(popDouble(stack, top - 1), popDouble(stack, top
  // - 3))); break;
  public static void ddiv(long[] primitives, Object[] symbolic, int top) {
    double c1 = BytecodeNode.popDouble(primitives, top - 1);
    double c2 = BytecodeNode.popDouble(primitives, top - 3);
    double concResult = c2 / c1;
    BytecodeNode.putDouble(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.DDIV,
            PrimitiveTypes.DOUBLE,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 3),
            popSymbolic(symbolic, top - 1)));
  }

  // case IREM: putInt(stack, top - 2, remInt(checkNonZero(popInt(stack, top - 1)), popInt(stack,
  // top - 2))); break;
  public static void irem(long[] primitives, Object[] symbolic, int top, BytecodeNode bn) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    checkNonZero(c1, peekSymbolic(symbolic, top - 1), bn);
    int concResult = c2 % c1;
    BytecodeNode.putInt(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.IREM,
            PrimitiveTypes.INT,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 2),
            popSymbolic(symbolic, top - 1)));
  }

  // case LREM: putLong(stack, top - 4, remLong(checkNonZero(popLong(stack, top - 1)),
  // popLong(stack, top - 3))); break;
  public static void lrem(long[] primitives, Object[] symbolic, int top, BytecodeNode bn) {
    long c1 = BytecodeNode.popLong(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 3);
    checkNonZero(c1, peekSymbolic(symbolic, top - 1), bn);
    long concResult = c2 % c1;
    BytecodeNode.putLong(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.LREM,
            PrimitiveTypes.LONG,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 3),
            popSymbolic(symbolic, top - 1)));
  }

  // case FREM: putFloat(stack, top - 2, remFloat(popFloat(stack, top - 1), popFloat(stack, top -
  // 2))); break;
  public static void frem(long[] primitives, Object[] symbolic, int top) {
    float c1 = BytecodeNode.popFloat(primitives, top - 1);
    float c2 = BytecodeNode.popFloat(primitives, top - 2);
    float concResult = c2 % c1;
    BytecodeNode.putFloat(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.FREM,
            PrimitiveTypes.FLOAT,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 2),
            popSymbolic(symbolic, top - 1)));
  }

  // case DREM: putDouble(stack, top - 4, remDouble(popDouble(stack, top - 1), popDouble(stack, top
  // - 3))); break;
  public static void drem(long[] primitives, Object[] symbolic, int top) {
    double c1 = BytecodeNode.popDouble(primitives, top - 1);
    double c2 = BytecodeNode.popDouble(primitives, top - 3);
    double concResult = c2 % c1;
    BytecodeNode.putDouble(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.DREM,
            PrimitiveTypes.DOUBLE,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 3),
            popSymbolic(symbolic, top - 1)));
  }

  // case INEG: putInt(stack, top - 1, -popInt(stack, top - 1)); break;
  public static void ineg(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int concResult = -c1;
    BytecodeNode.putInt(primitives, top - 1, concResult);
    putSymbolic(
        symbolic,
        top - 1,
        unarySymbolicOp(OperatorComparator.INEG, concResult, popSymbolic(symbolic, top - 1)));
  }

  // case LNEG: putLong(stack, top - 2, -popLong(stack, top - 1)); break;
  public static void lneg(long[] primitives, Object[] symbolic, int top) {
    long c1 = BytecodeNode.popLong(primitives, top - 1);
    long concResult = -c1;
    BytecodeNode.putLong(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 1,
        unarySymbolicOp(OperatorComparator.LNEG, concResult, popSymbolic(symbolic, top - 1)));
  }

  // case FNEG: putFloat(stack, top - 1, -popFloat(stack, top - 1)); break;
  public static void fneg(long[] primitives, Object[] symbolic, int top) {
    float c1 = BytecodeNode.popFloat(primitives, top - 1);
    float concResult = -c1;
    BytecodeNode.putFloat(primitives, top - 1, concResult);
    putSymbolic(
        symbolic,
        top - 1,
        unarySymbolicOp(OperatorComparator.FNEG, concResult, popSymbolic(symbolic, top - 1)));
  }

  // case DNEG: putDouble(stack, top - 2, -popDouble(stack, top - 1)); break;
  public static void dneg(long[] primitives, Object[] symbolic, int top) {
    double c1 = BytecodeNode.popDouble(primitives, top - 1);
    double concResult = -c1;
    BytecodeNode.putDouble(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 1,
        unarySymbolicOp(OperatorComparator.DNEG, concResult, popSymbolic(symbolic, top - 1)));
  }

  // case ISHL: putInt(stack, top - 2, shiftLeftInt(popInt(stack, top - 1), popInt(stack, top -
  // 2))); break;
  public static void ishl(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    int concResult = c2 << c1;
    BytecodeNode.putInt(primitives, top - 2, concResult);

    AnnotatedValue sRight = popSymbolic(symbolic, top - 1);
    AnnotatedValue sLeft = popSymbolic(symbolic, top - 2);
    if (sLeft != null || sRight != null) {
      sRight =
          new AnnotatedValue(
              c1,
              new ComplexExpression(
                  OperatorComparator.IAND,
                  sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                  Constant.INT_0x1F));
    }
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.ISHL, PrimitiveTypes.INT, c2, c1, concResult, sLeft, sRight));
  }

  // case LSHL: putLong(stack, top - 3, shiftLeftLong(popInt(stack, top - 1), popLong(stack, top -
  // 2))); break;
  public static void lshl(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 2);
    long concResult = c2 << c1;
    BytecodeNode.putLong(primitives, top - 3, concResult);

    AnnotatedValue sRight = popSymbolic(symbolic, top - 1);
    AnnotatedValue sLeft = popSymbolic(symbolic, top - 2);
    if (sLeft != null || sRight != null) {
      sRight =
          new AnnotatedValue(
              c1,
              new ComplexExpression(
                  OperatorComparator.I2L,
                  new ComplexExpression(
                      OperatorComparator.IAND,
                      sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                      Constant.INT_0x3F)));
    }
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.LSHL,
            PrimitiveTypes.LONG,
            PrimitiveTypes.INT,
            c2,
            c1,
            concResult,
            sLeft,
            sRight));
  }

  // case ISHR: putInt(stack, top - 2, shiftRightSignedInt(popInt(stack, top - 1), popInt(stack, top
  // - 2))); break;
  public static void ishr(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    int concResult = c2 >> c1;
    BytecodeNode.putInt(primitives, top - 2, concResult);

    AnnotatedValue sRight = popSymbolic(symbolic, top - 1);
    AnnotatedValue sLeft = popSymbolic(symbolic, top - 2);
    if (sLeft != null || sRight != null) {
      sRight =
          new AnnotatedValue(
              c1,
              new ComplexExpression(
                  OperatorComparator.IAND,
                  sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                  Constant.INT_0x1F));
    }
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.ISHR, PrimitiveTypes.INT, c2, c1, concResult, sLeft, sRight));
  }

  // case LSHR: putLong(stack, top - 3, shiftRightSignedLong(popInt(stack, top - 1), popLong(stack,
  // top - 2))); break;
  public static void lshr(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 2);
    long concResult = c2 >> c1;
    BytecodeNode.putLong(primitives, top - 3, concResult);

    AnnotatedValue sRight = popSymbolic(symbolic, top - 1);
    AnnotatedValue sLeft = popSymbolic(symbolic, top - 2);
    if (sLeft != null || sRight != null) {
      sRight =
          new AnnotatedValue(
              c1,
              new ComplexExpression(
                  OperatorComparator.I2L,
                  new ComplexExpression(
                      OperatorComparator.IAND,
                      sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                      Constant.INT_0x3F)));
    }
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.LSHR,
            PrimitiveTypes.LONG,
            PrimitiveTypes.INT,
            c2,
            c1,
            concResult,
            sLeft,
            sRight));
  }

  // case IUSHR: putInt(stack, top - 2, shiftRightUnsignedInt(popInt(stack, top - 1), popInt(stack,
  // top - 2))); break;
  public static void iushr(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    int concResult = c2 >>> c1;
    BytecodeNode.putInt(primitives, top - 2, concResult);

    AnnotatedValue sRight = popSymbolic(symbolic, top - 1);
    AnnotatedValue sLeft = popSymbolic(symbolic, top - 2);
    if (sLeft != null || sRight != null) {
      sRight =
          new AnnotatedValue(
              c1,
              new ComplexExpression(
                  OperatorComparator.IAND,
                  sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                  Constant.INT_0x1F));
    }

    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.IUSHR, PrimitiveTypes.INT, c2, c1, concResult, sLeft, sRight));
  }

  // case LUSHR: putLong(stack, top - 3, shiftRightUnsignedLong(popInt(stack, top - 1),
  // popLong(stack, top - 2))); break;
  public static void lushr(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 2);
    long concResult = c2 >>> c1;
    BytecodeNode.putLong(primitives, top - 3, concResult);

    AnnotatedValue sRight = popSymbolic(symbolic, top - 1);
    AnnotatedValue sLeft = popSymbolic(symbolic, top - 2);
    if (sLeft != null || sRight != null) {
      sRight =
          new AnnotatedValue(
              c1,
              new ComplexExpression(
                  OperatorComparator.I2L,
                  new ComplexExpression(
                      OperatorComparator.IAND,
                      sRight != null ? sRight.symbolic() : Constant.fromConcreteValue(c1),
                      Constant.INT_0x3F)));
    }

    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.LUSHR,
            PrimitiveTypes.LONG,
            PrimitiveTypes.INT,
            c2,
            c1,
            concResult,
            sLeft,
            sRight));
  }

  // case IAND: putInt(stack, top - 2, popInt(stack, top - 1) & popInt(stack, top - 2)); break;
  public static void iand(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    int concResult = c1 & c2;
    BytecodeNode.putInt(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.IAND,
            PrimitiveTypes.INT,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 2)));
  }

  // case LAND: putLong(stack, top - 4, popLong(stack, top - 1) & popLong(stack, top - 3)); break;
  public static void land(long[] primitives, Object[] symbolic, int top) {
    long c1 = BytecodeNode.popLong(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 3);
    long concResult = c1 & c2;
    BytecodeNode.putLong(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.LAND,
            PrimitiveTypes.LONG,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 3)));
  }

  //  case IOR: putInt(stack, top - 2, popInt(stack, top - 1) | popInt(stack, top - 2)); break;
  public static void ior(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    int concResult = c1 | c2;
    BytecodeNode.putInt(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.IOR,
            PrimitiveTypes.INT,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 2)));
  }

  // case LOR: putLong(stack, top - 4, popLong(stack, top - 1) | popLong(stack, top - 3)); break;
  public static void lor(long[] primitives, Object[] symbolic, int top) {
    long c1 = BytecodeNode.popLong(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 3);
    long concResult = c1 | c2;
    BytecodeNode.putLong(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.LOR,
            PrimitiveTypes.LONG,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 3)));
  }

  // case IXOR: putInt(stack, top - 2, popInt(stack, top - 1) ^ popInt(stack, top - 2)); break;
  public static void ixor(long[] primitives, Object[] symbolic, int top) {
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);
    int concResult = c1 ^ c2;
    BytecodeNode.putInt(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.IXOR,
            PrimitiveTypes.INT,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 2)));
  }

  // case LXOR: putLong(stack, top - 4, popLong(stack, top - 1) ^ popLong(stack, top - 3)); break;
  public static void lxor(long[] primitives, Object[] symbolic, int top) {
    long c1 = BytecodeNode.popLong(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 3);
    long concResult = c1 ^ c2;
    BytecodeNode.putLong(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 3,
        binarySymbolicOp(
            OperatorComparator.LXOR,
            PrimitiveTypes.LONG,
            c1,
            c2,
            concResult,
            popSymbolic(symbolic, top - 1),
            popSymbolic(symbolic, top - 3)));
  }

  // case IINC: setLocalInt(stack, bs.readLocalIndex(curBCI), getLocalInt(stack,
  // bs.readLocalIndex(curBCI)) + bs.readIncrement(curBCI)); break;
  public static void iinc(long[] primitives, Object[] symbolic, BytecodeStream bs, int curBCI) {
    int index = bs.readLocalIndex(curBCI);
    int incr = bs.readIncrement(curBCI);
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
    AnnotatedValue symbResult =
        new AnnotatedValue(
            concResult, new ComplexExpression(OperatorComparator.IADD, s1.symbolic(), symbIncr));
    setLocalSymbolic(symbolic, index, symbResult);
  }

  // case I2L: putLong(stack, top - 1, popInt(stack, top - 1)); break;
  public static void i2l(long[] primitives, Object[] symbolic, int top) {
    long c1 = BytecodeNode.popInt(primitives, top - 1);
    BytecodeNode.putLong(primitives, top - 1, c1);
    putSymbolic(
        symbolic, top, unarySymbolicOp(OperatorComparator.I2L, c1, popSymbolic(symbolic, top - 1)));
  }

  // case I2F: putFloat(stack, top - 1, popInt(stack, top - 1)); break;
  public static void i2f(long[] primitives, Object[] symbolic, int top) {
    float c1 = BytecodeNode.popInt(primitives, top - 1);
    BytecodeNode.putFloat(primitives, top - 1, c1);
    putSymbolic(
        symbolic,
        top - 1,
        unarySymbolicOp(OperatorComparator.I2F, c1, popSymbolic(symbolic, top - 1)));
  }

  // case I2D: putDouble(stack, top - 1, popInt(stack, top - 1)); break;
  public static void i2d(long[] primitives, Object[] symbolic, int top) {
    double c1 = BytecodeNode.popInt(primitives, top - 1);
    BytecodeNode.putDouble(primitives, top - 1, c1);
    putSymbolic(
        symbolic, top, unarySymbolicOp(OperatorComparator.I2D, c1, popSymbolic(symbolic, top - 1)));
  }

  // case L2I: putInt(stack, top - 2, (int) popLong(stack, top - 1)); break;
  public static void l2i(long[] primitives, Object[] symbolic, int top) {
    int c1 = (int) BytecodeNode.popLong(primitives, top - 1);
    BytecodeNode.putInt(primitives, top - 2, c1);
    putSymbolic(
        symbolic,
        top - 2,
        unarySymbolicOp(OperatorComparator.L2I, c1, popSymbolic(symbolic, top - 1)));
  }

  // case L2F: putFloat(stack, top - 2, popLong(stack, top - 1)); break;
  public static void l2f(long[] primitives, Object[] symbolic, int top) {
    float c1 = (float) BytecodeNode.popLong(primitives, top - 1);
    BytecodeNode.putFloat(primitives, top - 2, c1);
    putSymbolic(
        symbolic,
        top - 2,
        unarySymbolicOp(OperatorComparator.L2F, c1, popSymbolic(symbolic, top - 1)));
  }

  // case L2D: putDouble(stack, top - 2, popLong(stack, top - 1)); break;
  public static void l2d(long[] primitives, Object[] symbolic, int top) {
    double c1 = (double) BytecodeNode.popLong(primitives, top - 1);
    BytecodeNode.putDouble(primitives, top - 2, c1);
    putSymbolic(
        symbolic,
        top - 1,
        unarySymbolicOp(OperatorComparator.L2D, c1, popSymbolic(symbolic, top - 1)));
  }

  // case F2I: putInt(stack, top - 1, (int) popFloat(stack, top - 1)); break;
  // TODO: needs to be tested.
  /*
  If the value' is NaN, the result of the conversion is an int 0.
  Otherwise, if the value' is not an infinity, it is rounded to an
  integer value V, rounding towards zero using IEEE 754 round towards
  zero mode. If this integer value V can be represented as an int, then
  the result is the int value V.
  Otherwise, either the value' must be too small (a negative value of
  large magnitude or negative infinity), and the result is the smallest
  representable value of type int, or the value' must be too large
  (a positive value of large magnitude or positive infinity), and the
  result is the largest representable value of type int.
  */
  public static void f2i(long[] primitives, Object[] symbolic, int top) {
    int c1 = (int) BytecodeNode.popFloat(primitives, top - 1);
    BytecodeNode.putInt(primitives, top - 1, c1);
    AnnotatedValue s1 = popSymbolic(symbolic, top - 1);
    if (s1 == null) {
      return;
    }

    ComplexExpression intMinAsFloat =
        new ComplexExpression(OperatorComparator.I2F_RTZ, Constant.INT_MIN);
    ComplexExpression intMaxAsFloat =
        new ComplexExpression(OperatorComparator.I2F_RTZ, Constant.INT_MAX);
    ComplexExpression rtz = new ComplexExpression(OperatorComparator.F2I_RTZ, s1.symbolic());

    ComplexExpression cast =
        new ComplexExpression(
            OperatorComparator.ITE,
            /* cond */ new ComplexExpression(OperatorComparator.FP_ISNAN, s1.symbolic()),
            /* then */ INT_ZERO,
            /* else */ new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(OperatorComparator.FP_ISNEG, s1.symbolic()),
                /* then */ new ComplexExpression(
                    OperatorComparator.ITE,
                    /* cond */ new ComplexExpression(
                        OperatorComparator.FPLT, s1.symbolic(), intMinAsFloat),
                    /* then */ Constant.INT_MIN,
                    /* else */ rtz),
                /* else */ new ComplexExpression(
                    OperatorComparator.ITE,
                    /* cond */ new ComplexExpression(
                        OperatorComparator.FPGT, s1.symbolic(), intMaxAsFloat),
                    /* then */ Constant.INT_MAX,
                    /* else */ rtz)));

    putSymbolic(symbolic, top - 1, new AnnotatedValue(c1, cast));
  }

  // case F2L: putLong(stack, top - 1, (long) popFloat(stack, top - 1)); break;
  // TODO: needs to be tested.
  /*
  If the value' is NaN, the result of the conversion is a long 0.
  Otherwise, if the value' is not an infinity, it is rounded to an
  integer value V, rounding towards zero using IEEE 754 round towards
  zero mode. If this integer value V can be represented as a long, then
  the result is the long value V.
  Otherwise, either the value' must be too small (a negative value of
  large magnitude or negative infinity), and the result is the smallest
  representable value of type long, or the value' must be too large
  (a positive value of large magnitude or positive infinity), and the
  result is the largest representable value of type long.
  */
  public static void f2l(long[] primitives, Object[] symbolic, int top) {
    long c1 = (long) BytecodeNode.popFloat(primitives, top - 1);
    BytecodeNode.putLong(primitives, top - 1, c1);
    AnnotatedValue s1 = popSymbolic(symbolic, top - 1);
    if (s1 == null) {
      return;
    }

    ComplexExpression longMinAsFloat =
        new ComplexExpression(OperatorComparator.L2F_RTZ, Constant.LONG_MIN);
    ComplexExpression longMaxAsFloat =
        new ComplexExpression(OperatorComparator.L2F_RTZ, Constant.LONG_MAX);
    ComplexExpression rtz = new ComplexExpression(OperatorComparator.F2L_RTZ, s1.symbolic());

    ComplexExpression cast =
        new ComplexExpression(
            OperatorComparator.ITE,
            /* cond */ new ComplexExpression(OperatorComparator.FP_ISNAN, s1.symbolic()),
            /* then */ Constant.LONG_ZERO,
            /* else */ new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(OperatorComparator.FP_ISNEG, s1.symbolic()),
                /* then */ new ComplexExpression(
                    OperatorComparator.ITE,
                    /* cond */ new ComplexExpression(
                        OperatorComparator.FPLT, s1.symbolic(), longMinAsFloat),
                    /* then */ Constant.LONG_MIN,
                    /* else */ rtz),
                /* else */ new ComplexExpression(
                    OperatorComparator.ITE,
                    /* cond */ new ComplexExpression(
                        OperatorComparator.FPGT, s1.symbolic(), longMaxAsFloat),
                    /* then */ Constant.LONG_MAX,
                    /* else */ rtz)));

    putSymbolic(symbolic, top, new AnnotatedValue(c1, cast));
  }

  // case F2D: putDouble(stack, top - 1, popFloat(stack, top - 1)); break;
  public static void f2d(long[] primitives, Object[] symbolic, int top) {
    double c1 = BytecodeNode.popFloat(primitives, top - 1);
    BytecodeNode.putDouble(primitives, top - 1, c1);
    putSymbolic(
        symbolic, top, unarySymbolicOp(OperatorComparator.F2D, c1, popSymbolic(symbolic, top - 1)));
  }

  // case D2I: putInt(stack, top - 2, (int) popDouble(stack, top - 1)); break;
  // TODO: needs to be tested.
  /*
  If the value' is NaN, the result of the conversion is an int 0.
  Otherwise, if the value' is not an infinity, it is rounded to
  an integer value V, rounding towards zero using IEEE 754 round
  towards zero mode. If this integer value V can be represented
  as an int, then the result is the int value V.
  Otherwise, either the value' must be too small (a negative value
  of large magnitude or negative infinity), and the result is the
  smallest representable value of type int, or the value' must be
  too large (a positive value of large magnitude or positive infinity),
  and the result is the largest representable value of type int.
  */
  public static void d2i(long[] primitives, Object[] symbolic, int top) {
    int c1 = (int) BytecodeNode.popDouble(primitives, top - 1);
    BytecodeNode.putInt(primitives, top - 2, c1);
    AnnotatedValue s1 = popSymbolic(symbolic, top - 1);
    if (s1 == null) {
      return;
    }

    ComplexExpression intMinAsFloat =
        new ComplexExpression(OperatorComparator.I2D_RTZ, Constant.INT_MIN);
    ComplexExpression intMaxAsFloat =
        new ComplexExpression(OperatorComparator.I2D_RTZ, Constant.INT_MAX);
    ComplexExpression rtz = new ComplexExpression(OperatorComparator.D2I_RTZ, s1.symbolic());

    ComplexExpression cast =
        new ComplexExpression(
            OperatorComparator.ITE,
            /* cond */ new ComplexExpression(OperatorComparator.FP_ISNAN, s1.symbolic()),
            /* then */ INT_ZERO,
            /* else */ new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(OperatorComparator.FP_ISNEG, s1.symbolic()),
                /* then */ new ComplexExpression(
                    OperatorComparator.ITE,
                    /* cond */ new ComplexExpression(
                        OperatorComparator.FPLT, s1.symbolic(), intMinAsFloat),
                    /* then */ Constant.INT_MIN,
                    /* else */ rtz),
                /* else */ new ComplexExpression(
                    OperatorComparator.ITE,
                    /* cond */ new ComplexExpression(
                        OperatorComparator.FPGT, s1.symbolic(), intMaxAsFloat),
                    /* then */ Constant.INT_MAX,
                    /* else */ rtz)));

    putSymbolic(symbolic, top - 2, new AnnotatedValue(c1, cast));
  }

  // case D2L: putLong(stack, top - 2, (long) popDouble(stack, top - 1)); break;
  // TODO: needs to be tested.
  /*
  If the value' is NaN, the result of the conversion is a long 0.
  Otherwise, if the value' is not an infinity, it is rounded to an
  integer value V, rounding towards zero using IEEE 754 round towards
  zero mode. If this integer value V can be represented as a long, then
  the result is the long value V.
  Otherwise, either the value' must be too small (a negative value of
  large magnitude or negative infinity), and the result is the smallest
  representable value of type long, or the value' must be too large
  (a positive value of large magnitude or positive infinity), and the
  result is the largest representable value of type long.
  */
  public static void d2l(long[] primitives, Object[] symbolic, int top) {
    long c1 = (long) BytecodeNode.popDouble(primitives, top - 1);
    BytecodeNode.putLong(primitives, top - 2, c1);
    AnnotatedValue s1 = popSymbolic(symbolic, top - 1);
    if (s1 == null) {
      return;
    }

    ComplexExpression longMinAsFloat =
        new ComplexExpression(OperatorComparator.L2D_RTZ, Constant.LONG_MIN);
    ComplexExpression longMaxAsFloat =
        new ComplexExpression(OperatorComparator.L2D_RTZ, Constant.LONG_MAX);
    ComplexExpression rtz = new ComplexExpression(OperatorComparator.D2L_RTZ, s1.symbolic());

    ComplexExpression cast =
        new ComplexExpression(
            OperatorComparator.ITE,
            /* cond */ new ComplexExpression(OperatorComparator.FP_ISNAN, s1.symbolic()),
            /* then */ Constant.LONG_ZERO,
            /* else */ new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(OperatorComparator.FP_ISNEG, s1.symbolic()),
                /* then */ new ComplexExpression(
                    OperatorComparator.ITE,
                    /* cond */ new ComplexExpression(
                        OperatorComparator.FPLT, s1.symbolic(), longMinAsFloat),
                    /* then */ Constant.LONG_MIN,
                    /* else */ rtz),
                /* else */ new ComplexExpression(
                    OperatorComparator.ITE,
                    /* cond */ new ComplexExpression(
                        OperatorComparator.FPGT, s1.symbolic(), longMaxAsFloat),
                    /* then */ Constant.LONG_MAX,
                    /* else */ rtz)));

    putSymbolic(symbolic, top - 1, new AnnotatedValue(c1, cast));
  }

  // case D2F: putFloat(stack, top - 2, (float) popDouble(stack, top - 1)); break;
  // TODO: needs to be tested.
  /*
  The value on the top of the operand stack must be of type double.
  It is popped from the operand stack and undergoes value set conversion
  (§2.8.3) resulting in value'. Then value' is converted to a float result
  using IEEE 754 round to nearest mode. The result is pushed onto the
  operand stack.

  Where an d2f instruction is FP-strict (§2.8.2), the result of the conversion
  is always rounded to the nearest representable value in the float value set
  (§2.3.2).

  Where an d2f instruction is not FP-strict, the result of the conversion may
  be taken from the float-extended-exponent value set (§2.3.2); it is not
  necessarily rounded to the nearest representable value in the float value set.

  A finite value' too small to be represented as a float is converted to a zero
  of the same sign; a finite value' too large to be represented as a float is
  converted to an infinity of the same sign. A double NaN is converted to a
  float NaN.
  */
  public static void d2f(long[] primitives, Object[] symbolic, int top) {
    float c1 = (float) BytecodeNode.popDouble(primitives, top - 1);
    BytecodeNode.putFloat(primitives, top - 2, c1);
    putSymbolic(
        symbolic,
        top - 2,
        unarySymbolicOp(OperatorComparator.D2F, c1, popSymbolic(symbolic, top - 1)));
  }

  // case I2B: putInt(stack, top - 1, (byte) popInt(stack, top - 1)); break;
  public static void i2b(long[] primitives, Object[] symbolic, int top) {
    byte c1 = (byte) BytecodeNode.popInt(primitives, top - 1);
    BytecodeNode.putInt(primitives, top - 1, c1);
    putSymbolic(
        symbolic,
        top - 1,
        unarySymbolicOp(
            OperatorComparator.B2I,
            c1,
            unarySymbolicOp(OperatorComparator.I2B, c1, popSymbolic(symbolic, top - 1))));
  }

  // case I2C: putInt(stack, top - 1, (char) popInt(stack, top - 1)); break;
  public static void i2c(long[] primitives, Object[] symbolic, int top) {
    char c1 = (char) BytecodeNode.popInt(primitives, top - 1);
    BytecodeNode.putInt(primitives, top - 1, c1);
    putSymbolic(
        symbolic,
        top - 1,
        unarySymbolicOp(
            OperatorComparator.C2I,
            c1,
            unarySymbolicOp(OperatorComparator.I2C, c1, popSymbolic(symbolic, top - 1))));
  }

  // case I2S: putInt(stack, top - 1, (short) popInt(stack, top - 1)); break;
  public static void i2s(long[] primitives, Object[] symbolic, int top) {
    short c1 = (short) BytecodeNode.popInt(primitives, top - 1);
    BytecodeNode.putInt(primitives, top - 1, c1);
    putSymbolic(
        symbolic,
        top - 1,
        unarySymbolicOp(
            OperatorComparator.S2I,
            c1,
            unarySymbolicOp(OperatorComparator.I2S, c1, popSymbolic(symbolic, top - 1))));
  }

  // case LCMP : putInt(stack, top - 4, compareLong(popLong(stack, top - 1), popLong(stack, top -
  // 3))); break;
  public static void lcmp(long[] primitives, Object[] symbolic, int top) {
    long c1 = BytecodeNode.popLong(primitives, top - 1);
    long c2 = BytecodeNode.popLong(primitives, top - 3);
    int concResult = Long.compare(c2, c1);
    BytecodeNode.putInt(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 4,
        binarySymbolicOp(
            OperatorComparator.LCMP,
            PrimitiveTypes.LONG,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 3),
            popSymbolic(symbolic, top - 1)));
  }

  // case FCMPL: putInt(stack, top - 2, compareFloatLess(popFloat(stack, top - 1), popFloat(stack,
  // top - 2))); break;
  public static void fcmpl(long[] primitives, Object[] symbolic, int top) {
    float c1 = BytecodeNode.popFloat(primitives, top - 1);
    float c2 = BytecodeNode.popFloat(primitives, top - 2);
    int concResult = BytecodeNode.compareFloatLess(c1, c2);
    BytecodeNode.putInt(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.FCMPL,
            PrimitiveTypes.FLOAT,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 2),
            popSymbolic(symbolic, top - 1)));
  }

  // case FCMPG: putInt(stack, top - 2, compareFloatGreater(popFloat(stack, top - 1),
  // popFloat(stack, top - 2))); break;
  public static void fcmpg(long[] primitives, Object[] symbolic, int top) {
    float c1 = BytecodeNode.popFloat(primitives, top - 1);
    float c2 = BytecodeNode.popFloat(primitives, top - 2);
    int concResult = BytecodeNode.compareFloatGreater(c1, c2);
    BytecodeNode.putInt(primitives, top - 2, concResult);
    putSymbolic(
        symbolic,
        top - 2,
        binarySymbolicOp(
            OperatorComparator.FCMPG,
            PrimitiveTypes.FLOAT,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 2),
            popSymbolic(symbolic, top - 1)));
  }

  // case DCMPL: putInt(stack, top - 4, compareDoubleLess(popDouble(stack, top - 1),
  // popDouble(stack, top - 3))); break;
  public static void dcmpl(long[] primitives, Object[] symbolic, int top) {
    double c1 = BytecodeNode.popDouble(primitives, top - 1);
    double c2 = BytecodeNode.popDouble(primitives, top - 3);
    int concResult = BytecodeNode.compareDoubleLess(c1, c2);
    BytecodeNode.putInt(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 4,
        binarySymbolicOp(
            OperatorComparator.DCMPL,
            PrimitiveTypes.DOUBLE,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 3),
            popSymbolic(symbolic, top - 1)));
  }

  // case DCMPG: putInt(stack, top - 4, compareDoubleGreater(popDouble(stack, top - 1),
  // popDouble(stack, top - 3))); break;
  public static void dcmpg(long[] primitives, Object[] symbolic, int top) {
    double c1 = BytecodeNode.popDouble(primitives, top - 1);
    double c2 = BytecodeNode.popDouble(primitives, top - 3);
    int concResult = BytecodeNode.compareDoubleGreater(c1, c2);
    BytecodeNode.putInt(primitives, top - 4, concResult);
    putSymbolic(
        symbolic,
        top - 4,
        binarySymbolicOp(
            OperatorComparator.DCMPG,
            PrimitiveTypes.DOUBLE,
            c2,
            c1,
            concResult,
            popSymbolic(symbolic, top - 3),
            popSymbolic(symbolic, top - 1)));
  }

  // branching helpers ...

  public static boolean takeBranchPrimitive1(
      long[] primitives, Object[] symbolic, int top, int opcode) {
    assert IFEQ <= opcode && opcode <= IFLE;

    AnnotatedValue s1 = popSymbolic(symbolic, top - 1);
    int c1 = BytecodeNode.popInt(primitives, top - 1);

    boolean takeBranch = true;

    // @formatter:off
    switch (opcode) {
      case IFEQ:
        takeBranch = (c1 == 0);
        break;
      case IFNE:
        takeBranch = (c1 != 0);
        break;
      case IFLT:
        takeBranch = (c1 < 0);
        break;
      case IFGE:
        takeBranch = (c1 >= 0);
        break;
      case IFGT:
        takeBranch = (c1 > 0);
        break;
      case IFLE:
        takeBranch = (c1 <= 0);
        break;
      default:
        CompilerDirectives.transferToInterpreter();
        throw EspressoError.shouldNotReachHere("expecting IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE");
    }

    if (s1 != null) {
      Expression expr = null;

      if (Expression.isBoolean(s1.symbolic())) {
        switch (opcode) {
          case IFEQ:
            expr = !takeBranch ? s1.symbolic() : new ComplexExpression(BNEG, s1.symbolic());
            break;
          case IFNE:
            expr = takeBranch ? s1.symbolic() : new ComplexExpression(BNEG, s1.symbolic());
            break;
          default:
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("only defined for IFEQ and IFNE so far");
        }
      } else if (Expression.isCmpExpression(s1.symbolic())) {
        ComplexExpression ce = (ComplexExpression) s1.symbolic();
        OperatorComparator op = null;
        switch (ce.getOperator()) {
          case LCMP:
            // 0 if x == y; less than 0 if x < y; greater than 0 if x > y
            switch (opcode) {
              case IFEQ:
                op = takeBranch ? OperatorComparator.BVEQ : OperatorComparator.BVNE;
                break;
              case IFNE:
                op = takeBranch ? OperatorComparator.BVNE : OperatorComparator.BVEQ;
                break;
              case IFLT:
                op = takeBranch ? OperatorComparator.BVLT : BVGE;
                break;
              case IFGE:
                op = takeBranch ? BVGE : OperatorComparator.BVLT;
                break;
              case IFGT:
                op = takeBranch ? BVGT : BVLE;
                break;
              case IFLE:
                op = takeBranch ? BVLE : BVGT;
                break;
            }
            break;
          case FCMPL:
          case FCMPG:
          case DCMPL:
          case DCMPG:
            // 0 if x == y; less than 0 if x < y; greater than 0 if x > y
            switch (opcode) {
              case IFEQ:
              case IFNE:
                op = OperatorComparator.FPEQ;
                break;
              case IFLT:
                op = takeBranch ? OperatorComparator.FPLT : OperatorComparator.FPGE;
                break;
              case IFGE:
                op = takeBranch ? OperatorComparator.FPGE : OperatorComparator.FPLT;
                break;
              case IFGT:
                op = takeBranch ? OperatorComparator.FPGT : OperatorComparator.FPLE;
                break;
              case IFLE:
                op = takeBranch ? OperatorComparator.FPLE : OperatorComparator.FPGT;
                break;
            }
            break;
        }
        expr = new ComplexExpression(op, ce.getSubExpressions());
        if (!ce.getOperator().equals(OperatorComparator.LCMP)
            && ((opcode == IFEQ && !takeBranch) || (opcode == IFNE && takeBranch))) {
          expr = new ComplexExpression(BNEG, expr);
        }
      } else {
        switch (opcode) {
          case IFEQ:
            expr = new ComplexExpression(OperatorComparator.BVEQ, s1.symbolic(), INT_ZERO);
            break;
          case IFNE:
            expr = new ComplexExpression(OperatorComparator.BVNE, s1.symbolic(), INT_ZERO);
            break;
          case IFLT:
            expr = new ComplexExpression(OperatorComparator.BVLT, s1.symbolic(), INT_ZERO);
            break;
          case IFGE:
            expr = new ComplexExpression(BVGE, s1.symbolic(), INT_ZERO);
            break;
          case IFGT:
            expr = new ComplexExpression(BVGT, s1.symbolic(), INT_ZERO);
            break;
          case IFLE:
            expr = new ComplexExpression(BVLE, s1.symbolic(), INT_ZERO);
            break;
          default:
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("expecting IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE");
        }
        expr = takeBranch ? expr : new ComplexExpression(BNEG, expr);
      }

      addTraceElement(new PathCondition(expr, takeBranch ? 1 : 0, 2));
    }
    return takeBranch;
  }

  public static boolean takeBranch2(long[] primitives, Object[] symbolic, int top, int opcode) {
    assert IF_ICMPEQ <= opcode && opcode <= IF_ICMPLE;
    AnnotatedValue s1 = popSymbolic(symbolic, top - 1);
    AnnotatedValue s2 = popSymbolic(symbolic, top - 2);
    int c1 = BytecodeNode.popInt(primitives, top - 1);
    int c2 = BytecodeNode.popInt(primitives, top - 2);

    // concrete
    boolean takeBranch = true;

    switch (opcode) {
      case IF_ICMPEQ:
        takeBranch = (c1 == c2);
        break;
      case IF_ICMPNE:
        takeBranch = (c1 != c2);
        break;
      case IF_ICMPLT:
        takeBranch = (c1 > c2);
        break;
      case IF_ICMPGE:
        takeBranch = (c1 <= c2);
        break;
      case IF_ICMPGT:
        takeBranch = (c1 < c2);
        break;
      case IF_ICMPLE:
        takeBranch = (c1 >= c2);
        break;
      default:
        CompilerDirectives.transferToInterpreter();
        throw EspressoError.shouldNotReachHere("non-branching bytecode");
    }

    // symbolic
    if (s1 != null || s2 != null) {

      Expression expr = null;

      // boolean
      if ((s1 == null || Expression.isBoolean(s1.symbolic()))
          && (s2 == null || Expression.isBoolean(s2.symbolic()))) {

        // assume that one is a constant.
        if (s1 != null && s2 != null) {
          throw EspressoError.shouldNotReachHere("non-branching bytecode");
        }

        expr = (s1 != null) ? s1.symbolic() : s2.symbolic();
        int c = (s1 != null) ? c2 : c1;

        switch (opcode) {
          case IF_ICMPEQ:
            expr = (c != 0) ? new ComplexExpression(BNEG, expr) : expr;
            break;
          case IF_ICMPNE:
            expr = (c == 0) ? new ComplexExpression(BNEG, expr) : expr;
            break;
          default:
            CompilerDirectives.transferToInterpreter();
            // FIXME: replace EspressoError.shouldNotReachHere calls with stoprecording(...) to make
            // analysis shut down properly
            throw EspressoError.shouldNotReachHere("non-branching bytecode");
        }

        if (takeBranch) {
          expr = new ComplexExpression(BNEG, expr);
        }

      }
      // numeric
      else {

        if (s1 == null) s1 = AnnotatedValue.fromConstant(PrimitiveTypes.INT, c1);
        if (s2 == null) s2 = AnnotatedValue.fromConstant(PrimitiveTypes.INT, c2);

        switch (opcode) {
          case IF_ICMPEQ:
            expr =
                new ComplexExpression(
                    takeBranch ? OperatorComparator.BVEQ : OperatorComparator.BVNE,
                    s1.symbolic(),
                    s2.symbolic());
            break;
          case IF_ICMPNE:
            expr =
                new ComplexExpression(
                    takeBranch ? OperatorComparator.BVNE : OperatorComparator.BVEQ,
                    s1.symbolic(),
                    s2.symbolic());
            break;
          case IF_ICMPLT:
            expr = new ComplexExpression(takeBranch ? BVGT : BVLE, s1.symbolic(), s2.symbolic());
            break;
          case IF_ICMPGE:
            expr = new ComplexExpression(takeBranch ? BVLE : BVGT, s1.symbolic(), s2.symbolic());
            break;
          case IF_ICMPGT:
            expr =
                new ComplexExpression(
                    takeBranch ? OperatorComparator.BVLT : BVGE, s1.symbolic(), s2.symbolic());
            break;
          case IF_ICMPLE:
            expr =
                new ComplexExpression(
                    takeBranch ? BVGE : OperatorComparator.BVLT, s1.symbolic(), s2.symbolic());
            break;
          default:
            CompilerDirectives.transferToInterpreter();
            // FIXME: replace EspressoError.shouldNotReachHere calls with stoprecording(...) to make
            // analysis shut down properly
            throw EspressoError.shouldNotReachHere("non-branching bytecode");
        }
      }

      PathCondition pc = new PathCondition(expr, takeBranch ? 1 : 0, 2);
      addTraceElement(pc);
    }
    return takeBranch;
  }

  public static void tableSwitch(int concIndex, AnnotatedValue symbIndex, int low, int high) {
    if (symbIndex == null) {
      return;
    }
    Expression expr;
    int bId;
    if (low <= concIndex && concIndex <= high) {
      Constant idx = Constant.fromConcreteValue(concIndex);
      expr = new ComplexExpression(OperatorComparator.BVEQ, symbIndex.symbolic(), idx);
      bId = concIndex - low;
    } else {
      Constant sLow = Constant.fromConcreteValue(low);
      Constant sHigh = Constant.fromConcreteValue(high);
      expr =
          new ComplexExpression(
              BOR,
              new ComplexExpression(OperatorComparator.BVLT, symbIndex.symbolic(), sLow),
              new ComplexExpression(BVGT, symbIndex.symbolic(), sHigh));
      bId = high - low + 1;
    }

    PathCondition pc = new PathCondition(expr, bId, high - low + 2);
    addTraceElement(pc);
  }

  public static void lookupSwitch(int key, AnnotatedValue symbKey, int... vals) {
    if (symbKey == null) {
      return;
    }

    for (int i = 0; i < vals.length; i++) {
      if (vals[i] == key) {
        Constant idxVal = Constant.fromConcreteValue(key);
        addTraceElement(
            new PathCondition(
                new ComplexExpression(OperatorComparator.BVEQ, symbKey.symbolic(), idxVal),
                i,
                vals.length + 1));
        return;
      }
    }

    ComplexExpression[] subExpr = new ComplexExpression[vals.length];
    for (int i = 0; i < vals.length; i++) {
      Constant idxVal = Constant.fromConcreteValue(vals[i]);
      subExpr[i] = new ComplexExpression(OperatorComparator.BVNE, symbKey.symbolic(), idxVal);
    }

    addTraceElement(
        new PathCondition(
            new ComplexExpression(OperatorComparator.BAND, subExpr), vals.length, vals.length + 1));
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

  public static AnnotatedValue popSymbolicAsInt(Object[] symbolic, int slot) {
    if (symbolic[slot] == null || !(symbolic[slot] instanceof AnnotatedValue)) {
      return null;
    }
    AnnotatedValue result = (AnnotatedValue) symbolic[slot];
    symbolic[slot] = null;
    return result == null ? result : new AnnotatedValue(result.asInt(), result.symbolic());
  }

  public static AnnotatedValue popSymbolicAsChar(Object[] symbolic, int slot) {
    if (symbolic[slot] == null || !(symbolic[slot] instanceof AnnotatedValue)) {
      return null;
    }
    AnnotatedValue result = (AnnotatedValue) symbolic[slot];
    symbolic[slot] = null;
    return result == null ? result : new AnnotatedValue((char) result.asInt(), result.symbolic());
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

  public static AnnotatedValue getLocalSymbolicAsInt(Object[] symbolic, int slot) {
    assert symbolic[symbolic.length - 1 - slot] instanceof AnnotatedValue;
    AnnotatedValue av = (AnnotatedValue) symbolic[symbolic.length - 1 - slot];
    return av == null ? av : new AnnotatedValue(av.asInt(), av.symbolic());
  }

  // --------------------------------------------------------------------------
  //
  // propagation of concolic values

  public static void doArrayCopy(
      StaticObject src, int srcPos, StaticObject dest, int destPos, Object oLength) {

    int length = 0;
    Expression symbolicLength;
    if (oLength instanceof AnnotatedValue) {
      // For the concrete Execution, we do not need a symbolic array??
      length = ((AnnotatedValue) oLength).asInt();
      symbolicLength = ((AnnotatedValue) oLength).symbolic();
    } else {
      length = (int) oLength;
      symbolicLength = Constant.fromConcreteValue(length);
    }

    if (src.getConcolicId() < 0 && dest.getConcolicId() < 0) {
      return;
    }

    AnnotatedValue[] destAnnotations;
    if (dest.getConcolicId() >= 0) {
      destAnnotations = symbolicObjects.get(dest.getConcolicId());
    } else {
      // TODO: organize code and wrap annotation creation in a method?
      destAnnotations = new AnnotatedValue[dest.length() + 1]; // + 1 for length
      destAnnotations[dest.length()] = new AnnotatedValue(length, symbolicLength);
      dest.setConcolicId(symbolicObjects.size());
      symbolicObjects.add(destAnnotations);
    }

    if (src.getConcolicId() < 0) {
      for (int i = 0; i < length; i++) {
        destAnnotations[destPos + i] = null;
      }
    } else {
      AnnotatedValue[] srcAnnotations = symbolicObjects.get(src.getConcolicId());
      for (int i = 0; i < length; i++) {
        destAnnotations[destPos + i] = srcAnnotations[srcPos + i];
      }
    }
  }

  // ---------------------- Symbolic Strings
  // ----------------------------------------------------------
  public static Object stringEquals(StaticObject self, StaticObject other, Meta meta) {
    // FIXME: we currently do not track conditions for exceptions inside equals!
    // FIXME: could be better to use method handle from meta?
    boolean areEqual = meta.toHostString(self).equals(meta.toHostString(other));
    if (self.getConcolicId() < 0 && other.getConcolicId() < 0) {
      return areEqual;
    }

    Expression exprSelf =
        (self.getConcolicId() < 0)
            ? Constant.fromConcreteValue(meta.toHostString(self))
            : symbolicObjects
                .get(self.getConcolicId())[symbolicObjects.get(self.getConcolicId()).length - 2]
                .symbolic();
    Expression exprOther =
        (other.getConcolicId() < 0)
            ? Constant.fromConcreteValue(meta.toHostString(other))
            : symbolicObjects
                .get(other.getConcolicId())[symbolicObjects.get(other.getConcolicId()).length - 2]
                .symbolic();

    Expression symbolic = new ComplexExpression(OperatorComparator.STRINGEQ, exprSelf, exprOther);

    return new AnnotatedValue(areEqual, symbolic);
  }

  public static Object stringCharAt(StaticObject self, Object index, Meta meta) {
    String concreteString = meta.toHostString(self);
    int concreteIndex = 0;
    Expression indexExpr;
    boolean concolicIndex = false;
    if (index instanceof AnnotatedValue) {
      AnnotatedValue a = (AnnotatedValue) index;
      concreteIndex = a.asInt();
      indexExpr = a.symbolic();
      concolicIndex = true;
    } else {
      concreteIndex = (int) index;
      indexExpr = Constant.fromConcreteValue(concreteIndex);
    }
    if (!self.isConcolic()) { // && !index.isConcolic()) {
      return concreteString.charAt(concreteIndex);
    }
    Expression symbolicString = makeStringToExpr(self, meta);

    Expression intIndexExpr = new ComplexExpression(BV2NAT, indexExpr);
    Expression symbolicStrLen = new ComplexExpression(SLENGTH, symbolicString);
    Expression bvSymbolicStrLen = new ComplexExpression(NAT2BV32, symbolicStrLen);

    boolean sat1 = (0 <= concreteIndex);
    boolean sat2 = (concreteIndex < concreteString.length());
    if (!sat1 && !concolicIndex) {
      // index is negative
      meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
      return null;
    } else if(!sat1 && concolicIndex) {
      Expression indexGreaterEqualsZero =
          new ComplexExpression(GT, INT_ZERO, indexExpr);
      PathCondition pc = new PathCondition(indexGreaterEqualsZero, 1, 2);
      addTraceElement(pc);
    } else if (sat1 && concolicIndex) {
      Expression indexGreaterEqualsZero =
          new ComplexExpression(LE, INT_ZERO, indexExpr);
      PathCondition pc = new PathCondition(indexGreaterEqualsZero, 0, 2);
      addTraceElement(pc);
    }

    if (!sat2) {
      Expression indexLTSymbolicStringLength = new ComplexExpression(GE, indexExpr, bvSymbolicStrLen);
      PathCondition pc2 = new PathCondition(indexLTSymbolicStringLength,  1, 2);
      addTraceElement(pc2);
      // index is greater than string length
      meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
      return null;
    }else{
      Expression indexLTSymbolicStringLength = new ComplexExpression(LT, indexExpr, bvSymbolicStrLen);
      PathCondition pc2 = new PathCondition(indexLTSymbolicStringLength,  0, 2);
      addTraceElement(pc2);
    }
    Expression charAtExpr = new ComplexExpression(SAT, symbolicString, intIndexExpr);
    return new AnnotatedValue(concreteString.charAt(concreteIndex), charAtExpr);
  }

  public static Object stringLength(StaticObject self, Meta meta) {
    String concreteString = meta.toHostString(self);
    int cLength = concreteString.length();
    if (!self.isConcolic()) {
      return cLength;
    }
    AnnotatedValue[] a = symbolicObjects.get(self.getConcolicId());
    return new AnnotatedValue(cLength, a[a.length - 1].symbolic());
  }

  public static Object characterToUpperCase(Object c, Meta meta) {
    char convC;
    if (c instanceof AnnotatedValue) {
      convC = (char) ((AnnotatedValue) c).asRaw();
      Expression symbolic = ((AnnotatedValue) c).symbolic();
      return new AnnotatedValue(
          Character.toUpperCase(convC), new ComplexExpression(STOUPPER, symbolic));
    } else {
      convC = (char) c;
      return Character.toUpperCase(convC);
    }
  }

  public static Object characterToLowerCase(Object c, Meta meta) {
    char convC;
    if (c instanceof AnnotatedValue) {
      convC = (char) ((AnnotatedValue) c).asRaw();
      Expression symbolic = ((AnnotatedValue) c).symbolic();
      return new AnnotatedValue(
          Character.toLowerCase(convC), new ComplexExpression(STOLOWER, symbolic));
    } else {
      convC = (char) c;
      return Character.toLowerCase(convC);
    }
  }

  public static void stringBuilderAppendString(
      StaticObject self, StaticObject string, DirectCallNode originalToString, Meta meta) {
    if (!self.isConcolic() && !string.isConcolic()) {
      // Nothing to do for pure concrete execution
      return;
    }
    String concreteOther = meta.toHostString(string);
    Expression strAddition = makeStringToExpr(string, meta);
    Expression strBuilder;
    String content = meta.toHostString((StaticObject) originalToString.call(self));
    String hostResult = content + concreteOther;
    StaticObject result = meta.toGuestString(hostResult);
    if (self.isConcolic()) {
      AnnotatedValue[] a = symbolicObjects.get(self.getConcolicId());
      strBuilder = a[a.length - 2].symbolic();
      Expression valueExpr = new ComplexExpression(SCONCAT, strBuilder, strAddition);
      a[a.length - 2] = new AnnotatedValue(result, valueExpr);
      a[a.length - 1] =
          new AnnotatedValue(
              hostResult.length(),
              new ComplexExpression(NAT2BV32, new ComplexExpression(SLENGTH, valueExpr)));
      symbolicObjects.set(self.getConcolicId(), a);
    } else {
      strBuilder = Constant.fromConcreteValue(content);
      int lengthAnnotations = ((ObjectKlass) self.getKlass()).getFieldTable().length + 2;
      AnnotatedValue[] annotation = new AnnotatedValue[lengthAnnotations];
      Expression valueExpr = new ComplexExpression(SCONCAT, strBuilder, strAddition);
      annotation[annotation.length - 2] = new AnnotatedValue(result, valueExpr);
      annotation[annotation.length - 1] =
          new AnnotatedValue(
              hostResult.length(),
              new ComplexExpression(NAT2BV32, new ComplexExpression(SLENGTH, valueExpr)));
      self.setConcolicId(symbolicObjects.size());
      symbolicObjects.add(annotation);
    }
    return;
  }

  public static Object stringBuilderToString(StaticObject self, String concrete, Meta meta) {
    StaticObject gConcrete = meta.toGuestString(concrete);
    if (self.isConcolic()) {
      Expression symbolicString = extractStringVarFromArray(self.getConcolicId());
      Expression symbolicStringLength = extractStringLengthFromArray(self.getConcolicId());
      updateStringAnnotations(
          gConcrete,
          new AnnotatedValue(gConcrete, symbolicString),
          new AnnotatedValue(concrete.length(), symbolicStringLength));
    }
    return gConcrete;
  }

  private static Field integer_value = null;

  public static StaticObject boxInteger(Object unboxed, Meta meta) {
    if (integer_value == null) {
      integer_value = meta.java_lang_Integer.lookupField(Symbol.Name.value, Symbol.Type._int);
    }
    StaticObject boxed = meta.java_lang_Integer.allocateInstance();

    // concolic part
    if (unboxed instanceof AnnotatedValue) {
      AnnotatedValue a = (AnnotatedValue) unboxed;
      setFieldAnnotation(boxed, integer_value, a);
      unboxed = a.asInt();
    }

    // concrete part
    integer_value.set(boxed, unboxed);

    return boxed;
  }

  private static Field byte_value = null;

  public static StaticObject boxByte(Object unboxed, Meta meta) {
    if (byte_value == null) {
      byte_value = meta.java_lang_Byte.lookupField(Symbol.Name.value, Symbol.Type._byte);
    }
    StaticObject boxed = meta.java_lang_Byte.allocateInstance();

    // concolic part
    if (unboxed instanceof AnnotatedValue) {
      AnnotatedValue a = (AnnotatedValue) unboxed;
      setFieldAnnotation(boxed, byte_value, a);
      unboxed = (byte) a.asInt();
    }

    // concrete part
    byte_value.set(boxed, unboxed);
    return boxed;
  }

  private static Field char_value = null;

  public static StaticObject boxChar(Object unboxed, Meta meta) {
    if (char_value == null) {
      char_value = meta.java_lang_Character.lookupField(Symbol.Name.value, Symbol.Type._char);
    }
    StaticObject boxed = meta.java_lang_Character.allocateInstance();

    // concolic part
    if (unboxed instanceof AnnotatedValue) {
      AnnotatedValue a = (AnnotatedValue) unboxed;
      setFieldAnnotation(boxed, char_value, a);
      unboxed = (char) a.asInt();
    }

    // concrete part
    char_value.set(boxed, unboxed);

    return boxed;
  }

  private static Field short_value = null;

  public static StaticObject boxShort(Object unboxed, Meta meta) {
    if (short_value == null) {
      short_value = meta.java_lang_Short.lookupField(Symbol.Name.value, Symbol.Type._short);
    }
    StaticObject boxed = meta.java_lang_Short.allocateInstance();

    // concolic part
    if (unboxed instanceof AnnotatedValue) {
      AnnotatedValue a = (AnnotatedValue) unboxed;
      setFieldAnnotation(boxed, short_value, a);
      unboxed = (short) a.asInt();
    }

    // concrete part
    short_value.set(boxed, unboxed);

    return boxed;
  }

  private static Field long_value = null;

  public static StaticObject boxLong(Object unboxed, Meta meta) {
    if (long_value == null) {
      long_value = meta.java_lang_Long.lookupField(Symbol.Name.value, Symbol.Type._long);
    }
    StaticObject boxed = meta.java_lang_Long.allocateInstance();

    // concolic part
    if (unboxed instanceof AnnotatedValue) {
      AnnotatedValue a = (AnnotatedValue) unboxed;
      setFieldAnnotation(boxed, long_value, a);
      unboxed = a.asLong();
    }

    // concrete part
    long_value.set(boxed, unboxed);

    return boxed;
  }

  private static Expression makeStringToExpr(StaticObject string, Meta meta) {
    assert string.isString();
    return string.isConcolic()
        ? extractStringVarFromArray(string.getConcolicId())
        : Constant.fromConcreteValue(meta.toHostString(string));
  }

  private static Expression makeStringLengthToExpr(StaticObject string, Meta meta) {
    return makeStringLengthToExpr(string, meta, true);
  }

  private static Expression makeStringLengthToExpr(StaticObject string, Meta meta, boolean bv) {
    assert string.isString();
    return string.isConcolic()
        ? extractStringLengthFromArray(string.getConcolicId())
        : bv
            ? Constant.fromConcreteValue(meta.toHostString(string).length())
            : Constant.createNatConstant(meta.toHostString(string).length());
  }

  private static Expression extractStringVarFromArray(int concolicId) {
    AnnotatedValue[] vals = symbolicObjects.get(concolicId);
    return vals[vals.length - 2].symbolic();
  }

  private static Expression extractStringLengthFromArray(int concolicId) {
    AnnotatedValue[] vals = symbolicObjects.get(concolicId);
    return vals[vals.length - 1].symbolic();
  }

  public static Object isDefinded(Object c, Meta meta) {
    int CODEPOINT_BOUND = 1000;
    char value;
    if (c instanceof AnnotatedValue) {
      value = (char) ((AnnotatedValue) c).asInt();
      Expression symbolic = ((AnnotatedValue) c).symbolic();
      Expression cAsInt = new ComplexExpression(NAT2BV32, new ComplexExpression(STOCODE, symbolic));
      Expression codepointLT5000 =
          new ComplexExpression(BVLE, cAsInt, Constant.fromConcreteValue(CODEPOINT_BOUND));
      boolean isDef = Character.isDefined(value);

      if (value > CODEPOINT_BOUND) {
        PathCondition pc =
            new PathCondition(
                new ComplexExpression(BVGT, cAsInt, Constant.fromConcreteValue(CODEPOINT_BOUND)),
                1,
                2);
        addTraceElement(pc);
        stopRecording("Analysis of defined code points over 1000 is not supported.", meta);
      }
      PathCondition pc = new PathCondition(codepointLT5000, 0, 2);
      addTraceElement(pc);
      Expression undefined = Constant.fromConcreteValue(false);
      for (int i = 0; i <= CODEPOINT_BOUND; i++) {
        if (!Character.isDefined(i)) {
          undefined =
              new ComplexExpression(
                  BOR,
                  undefined,
                  new ComplexExpression(BVEQ, cAsInt, Constant.fromConcreteValue(i)));
        }
      }
      if (isDef) {
        undefined = new ComplexExpression(BNEG, undefined);
      }
      return new AnnotatedValue(isDef, undefined);
    } else {
      value = (char) c;
    }

    return Character.isDefined(value);
  }

  @TruffleBoundary
  public static Object stringContains(StaticObject self, StaticObject s, Meta meta) {
    String concreteSelf = meta.toHostString(self);
    String other = meta.toHostString(s);
    boolean concreteRes = concreteSelf.contains(other);
    if (!self.isConcolic() && !s.isConcolic()) {
      return concreteRes;
    }
    Expression symbolicSelf = makeStringToExpr(self, meta);
    Expression symbolicOther = makeStringToExpr(s, meta);
    return new AnnotatedValue(
        concreteRes, new ComplexExpression(SCONTAINS, symbolicSelf, symbolicOther));
  }

  @TruffleBoundary
  public static Object stringConcat(StaticObject self, StaticObject s, Meta meta) {
    String concreteSelf = meta.toHostString(self);
    String concreteOther = meta.toHostString(s);
    String concatenated = concreteSelf.concat(concreteOther);
    StaticObject result = meta.toGuestString(concatenated);
    if (self.isConcolic() || s.isConcolic()) {
      Expression symbolicSelf = makeStringToExpr(self, meta);
      Expression symbolicOther = makeStringToExpr(s, meta);
      Expression newConcatExpr = new ComplexExpression(SCONCAT, symbolicSelf, symbolicOther);

      result.setConcolicId(symbolicObjects.size());

      int lengthAnnotations = ((ObjectKlass) self.getKlass()).getFieldTable().length + 2;
      AnnotatedValue[] annotations = new AnnotatedValue[lengthAnnotations];
      annotations[annotations.length - 2] = new AnnotatedValue(result, newConcatExpr);
      annotations[annotations.length - 1] =
          new AnnotatedValue(
              concatenated.length(),
              new ComplexExpression(
                  IADD, makeStringLengthToExpr(self, meta), makeStringLengthToExpr(s, meta)));
      symbolicObjects.add(annotations);
    }
    return result;
  }

  @TruffleBoundary
  public static Object stringToString(StaticObject self, Meta meta) {
    return self;
  }

  public static void stringBuilderInsert(
      StaticObject self,
      Object offset,
      Object toInsert,
      DirectCallNode insert,
      DirectCallNode originalToString,
      Meta meta) {
    if (toInsert instanceof AnnotatedValue) {
      Concolic.stopRecording("Cannot insert symbolic chars to StringBuffer", meta);
    }
    StaticObject toInsertCasted = meta.toGuestString(String.valueOf((char) toInsert));
    stringBuilderInsert(self, offset, toInsertCasted, insert, originalToString, meta);
  }

  public static void stringBuilderInsert(
      StaticObject self,
      Object offset,
      StaticObject toInsert,
      DirectCallNode insert,
      DirectCallNode originalToString,
      Meta meta) {
    if (offset instanceof AnnotatedValue) {
      Concolic.stopRecording("Cannot handle symbolic insert into string buffer yet.", meta);
    }
    int concreteOffset = (int) offset;
    Expression symbolicOffset = Constant.createNatConstant(concreteOffset);
    if (self.isConcolic() || toInsert.isConcolic()) {
      String concreteSelf = meta.toHostString((StaticObject) originalToString.call(self));

      Expression symbolicSelf = makeStringToExpr(self, meta);
      Expression symbolicToInsert = makeStringToExpr(toInsert, meta);

      Expression outerSelf = makeStringLengthToExpr(self, meta);
      Expression symbolicSelfLength = outerSelf;
      if(outerSelf instanceof ComplexExpression) {
        symbolicSelfLength = ((ComplexExpression) outerSelf).getSubExpressions()[0];
      }
      Expression symbolicToInsertLength = makeStringLengthToExpr(toInsert, meta, false);

      boolean validOffset = 0 <= concreteOffset && concreteOffset <= concreteSelf.length();
      if (!validOffset) {
        Expression lengthCheck =
            new ComplexExpression(
                BNEG,
                new ComplexExpression(
                    BAND,
                    new ComplexExpression(GE, NAT_ZERO, symbolicOffset),
                    new ComplexExpression(LE, symbolicOffset, symbolicSelfLength)));
        PathCondition pc = new PathCondition(lengthCheck, 1, 2);
        addTraceElement(pc);
        meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
      } else {
        Expression lengthCheck =
            new ComplexExpression(
                BAND,
                new ComplexExpression(GE, NAT_ZERO, symbolicOffset),
                new ComplexExpression(LE, symbolicOffset, symbolicSelfLength));
        PathCondition pc = new PathCondition(lengthCheck, 0, 2);
        addTraceElement(pc);
      }
      Expression resultingSymbolicValue;
      if(concreteSelf.isEmpty()){
        resultingSymbolicValue = symbolicToInsert;
      } else {
        if (concreteOffset != 0) {
          Expression symbolicLeft =
              new ComplexExpression(SSUBSTR, symbolicSelf, NAT_ZERO, symbolicOffset);
          Expression symbolicRight =
              new ComplexExpression(
                  SSUBSTR,
                  symbolicSelf,
                  new ComplexExpression(NATADD, symbolicOffset, symbolicToInsertLength),
                  symbolicSelfLength);
          resultingSymbolicValue =
              new ComplexExpression(
                  SCONCAT,
                  symbolicLeft,
                  new ComplexExpression(SCONCAT, symbolicToInsert, symbolicRight));
        }else{
          resultingSymbolicValue =
              new ComplexExpression(
                  SCONCAT,
                  symbolicToInsert,
                  symbolicSelf);
        }
      }
      Expression resultingSymbolicLength =
          new ComplexExpression(NAT2BV32, new ComplexExpression(SLENGTH, resultingSymbolicValue));
      int concoliID = self.getConcolicId();
      insert.call(self, concreteOffset, toInsert);
      self.setConcolicId(concoliID);
      StaticObject concretRes = (StaticObject) originalToString.call(self);
      AnnotatedValue newContent = new AnnotatedValue(concretRes, resultingSymbolicValue);
      AnnotatedValue newLength =
          new AnnotatedValue(meta.toHostString(concretRes).length(), resultingSymbolicLength);
      updateStringAnnotations(self, newContent, newLength);
    }else{
      insert.call(self, concreteOffset, toInsert);
    }
  }

  private static void updateStringAnnotations(
      StaticObject string, AnnotatedValue content, AnnotatedValue length) {
    if (string.isConcolic()) {
      AnnotatedValue[] annotations = symbolicObjects.get(string.getConcolicId());
      annotations[annotations.length - 2] = content;
      annotations[annotations.length - 1] = length;
    } else {
      int lengthAnnotations = ((ObjectKlass) string.getKlass()).getFieldTable().length + 2;
      AnnotatedValue[] annotationsString = new AnnotatedValue[lengthAnnotations];
      annotationsString[lengthAnnotations - 2] = content;
      annotationsString[lengthAnnotations - 1] = length;
      string.setConcolicId(symbolicObjects.size());
      symbolicObjects.add(annotationsString);
    }
  }

  public static Object stringToLowercase(StaticObject self, Meta meta) {
    String concreteHost = meta.toHostString(self).toLowerCase();
    StaticObject result = meta.toGuestString(concreteHost);
    if (self.isConcolic()) {
      result.setConcolicId(symbolicObjects.size());
      AnnotatedValue[] annotations = symbolicObjects.get(self.getConcolicId());
      annotations[annotations.length - 2] =
          new AnnotatedValue(
              result,
              new ComplexExpression(STOLOWER, annotations[annotations.length - 2].symbolic()));
      symbolicObjects.add(result.getConcolicId(), annotations);
    }
    return result;
  }

  public static void stringConstructor(StaticObject self, StaticObject other, Meta meta) {
    String val = meta.toHostString(self);
    Expression str = makeStringToExpr(other, meta);
    Expression strLen = makeStringLengthToExpr(other, meta);
    updateStringAnnotations(
        self,
        new AnnotatedValue(meta.toGuestString(val), str),
        new AnnotatedValue(val.length(), strLen));
  }

  public static Object regionMatches(
      StaticObject self,
      StaticObject other,
      boolean ignore,
      int ctoffset,
      int cooffset,
      int clen,
      Meta meta) {
    String cSelf = meta.toHostString(self);
    String cOther = meta.toHostString(self);
    boolean boundsCheck =
        evaluateBoundRegionMatches(
            cooffset,
            ctoffset,
            clen,
            cOther.length(),
            cSelf.length(),
            makeStringLengthToExpr(other, meta),
            makeStringLengthToExpr(self, meta));
    if (!boundsCheck) {
      return false;
    }
    boolean cRes = cSelf.regionMatches(ignore, ctoffset, cOther, cooffset, clen);
    if (self.isConcolic() && !other.isConcolic()) {
      return regionMatchesSymbolic(
          makeStringToExpr(self, meta),
          Constant.fromConcreteValue(cOther),
          ctoffset,
          cooffset,
          clen,
          ignore,
          cRes);
    } else if (!self.isConcolic() && other.isConcolic()) {
      return regionMatchesSymbolic(
          makeStringToExpr(other, meta),
          Constant.fromConcreteValue(cSelf),
          cooffset,
          ctoffset,
          clen,
          ignore,
          cRes);
    } else {
      return regionMatchesSymbolic(
          makeStringToExpr(self, meta),
          makeStringToExpr(other, meta),
          ctoffset,
          cooffset,
          clen,
          ignore,
          cRes);
    }
  }

  private static Object regionMatchesSymbolic(
      Expression symbolicSelf,
      Expression symbolicOther,
      int ctoffset,
      int cooffset,
      int clen,
      boolean ignore,
      boolean cRes) {
    Expression symbolicSubSelf =
        new ComplexExpression(
            SSUBSTR,
            symbolicSelf,
            Constant.createNatConstant(ctoffset),
            Constant.createNatConstant(clen));
    Expression symbolicSubOther =
        new ComplexExpression(
            SSUBSTR,
            symbolicOther,
            Constant.createNatConstant(cooffset),
            Constant.createNatConstant(clen));
    if (ignore) {
      symbolicSubSelf = new ComplexExpression(STOLOWER, symbolicSubSelf);
      symbolicSubOther = new ComplexExpression(STOLOWER, symbolicSubOther);
    }
    PathCondition pc;
    if (cRes) {
      pc =
          new PathCondition(
              new ComplexExpression(STRINGEQ, symbolicSubSelf, symbolicSubOther), 0, 2);
    } else {
      pc =
          new PathCondition(
              new ComplexExpression(
                  BNEG, new ComplexExpression(STRINGEQ, symbolicSubSelf, symbolicSubOther)),
              1,
              2);
    }
    addTraceElement(pc);
    return cRes;
  }

  private static boolean evaluateBoundRegionMatches(
      int ooffset,
      int toffset,
      int len,
      int olen,
      int tlen,
      Expression otherSymLen,
      Expression tSymLen) {
    boolean upperOBound = (ooffset + len) > olen;
    Expression upperOBoundE =
        new ComplexExpression(LT, otherSymLen, Constant.fromConcreteValue(ooffset + len));

    boolean lowerOBound = (ooffset + len) < 0;

    boolean lowerTBound = (toffset + len) < 0;

    boolean upperTBound = (toffset + len) > tlen;
    Expression upperTBoundE =
        new ComplexExpression(LT, tSymLen, Constant.fromConcreteValue(toffset + len));
    Expression check0 = new ComplexExpression(BAND, upperOBoundE, upperTBoundE);
    Expression check1 =
        new ComplexExpression(BAND, upperOBoundE, new ComplexExpression(BNEG, upperTBoundE));
    Expression check2 =
        new ComplexExpression(BAND, new ComplexExpression(BNEG, upperOBoundE), upperTBoundE);
    Expression check3 =
        new ComplexExpression(
            BAND,
            new ComplexExpression(BNEG, upperOBoundE),
            new ComplexExpression(BNEG, upperTBoundE));
    Expression effective = null;
    int branchIdx = -1;
    if (upperOBound) {
      if (upperTBound) {
        effective = check0;
        branchIdx = 0;

      } else {
        effective = check1;
        branchIdx = 1;
      }
    } else {
      if (upperTBound) {
        effective = check2;
        branchIdx = 2;
      } else {
        effective = check3;
        branchIdx = 3;
      }
    }
    PathCondition pc = new PathCondition(effective, branchIdx, 4);
    addTraceElement(pc);
    return !(upperOBound || lowerOBound || lowerTBound || upperTBound);
  }
}
