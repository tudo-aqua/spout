package tools.aqua.concolic;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.util.ArrayList;
import java.util.Arrays;

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

    //TODO: add concolic values for all primitive types


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

    public static AnnotatedValue getArray(StaticObject receiver, int index, Object value) {
        //TODO: add array get
        return null;
    }

    public static void setArray(StaticObject receiver, int index, AnnotatedValue a) {
        //TODO: add array set
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
        countIntSeeds = 0;
        countStringSeeds = 0;
        symbolicObjects.clear();
        parseConfig(config);
        System.out.println("Seeded Int Values: " + Arrays.toString(seedsIntValues));
        System.out.println("Seeded String Values: " + Arrays.toString(seedStringValues));
        //TODO: init all values
        System.out.println("======================== START PATH [END].");
    }

    private static void parseConfig(String config) {
        if (config.trim().length() < 1) {
            return;
        }
        String[] paramsGroups = config.trim().split(" "); // not in base64
        for (String paramGroup : paramsGroups) {
            String[] keyValue = paramGroup.split(":"); // not in base64
            switch (keyValue[0]) {
                case "concolic.ints":
                    parseInts(keyValue[1]);
                    break;
                case "concolic.strings":
                    parseStrings(keyValue[1]);
                    break;
                //TODO: add all classes
            }
        }
    }

    private static void parseInts(String config) {
        String[] valsAsStr;
        if (config.trim().length() > 0) {
            valsAsStr = config.split(",");
        }
        else {
            valsAsStr = new String[] {};
        }

        int[] vals = new int[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            vals[i] = Integer.valueOf(valsAsStr[i].trim());
        }
        seedsIntValues = vals;
    }

    private static void parseStrings(String config) {
        String[] valsAsStr;
        if (config.trim().length() > 0) {
            valsAsStr = config.split(",");
        }
        else {
            valsAsStr = new String[] {};
        }

        String[] vals = new String[valsAsStr.length];
        for (int i=0; i<valsAsStr.length; i++) {
            vals[i] = valsAsStr[i].trim();
        }
        seedStringValues = vals;
    }

    @CompilerDirectives.TruffleBoundary
    public static void endPath() {
        System.out.println("======================== END PATH [BEGIN].");
        printTrace();
        System.out.println("======================== END PATH [END].");
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

    private static void symbolicIntOp(OperatorComparator op, Object[] symbolic,
                                       int top, int c1, int c2, int concResult) {
        symbolicIntOp(op, symbolic, top, c1, c2, concResult, false);
    }

    private static void symbolicIntOp(OperatorComparator op, Object[] symbolic,
                                       int top, int c1, int c2, int concResult, boolean reverse) {

        AnnotatedValue s1 = popSymbolic(symbolic, reverse ? top -2 : top -1);
        AnnotatedValue s2 = popSymbolic(symbolic, reverse ? top -1 : top -2);
        if (s1 == null && s2 == null) {
            return;
        }

        if (s1 == null) s1 = AnnotatedValue.fromInt(c1);
        if (s2 == null) s2 = AnnotatedValue.fromInt(c2);

        AnnotatedValue result = new AnnotatedValue( concResult, new ComplexExpression(op, s1.symbolic(), s2.symbolic() ));
        putSymbolic(symbolic,top - 2, result);
    }

    private static void unarySymbolicIntOp(OperatorComparator op, Object[] symbolic,
                                            int top, int c1, int concResult) {

        AnnotatedValue s1 = popSymbolic(symbolic, top -1);
        if (s1 == null) {
            return;
        }
        AnnotatedValue result = new AnnotatedValue( concResult, new ComplexExpression(op, s1.symbolic()));
        putSymbolic(symbolic,top - 1, result);
    }

    // case IADD: putInt(stack, top - 2, popInt(stack, top - 1) + popInt(stack, top - 2)); break;
    public static void iadd(long[] primitives,  Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c1 + c2;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        symbolicIntOp(OperatorComparator.IADD, symbolic, top, c1, c2, concResult);
    }

    // case LADD: putLong(stack, top - 4, popLong(stack, top - 1) + popLong(stack, top - 3)); break;
    // case FADD: putFloat(stack, top - 2, popFloat(stack, top - 1) + popFloat(stack, top - 2)); break;
    // case DADD: putDouble(stack, top - 4, popDouble(stack, top - 1) + popDouble(stack, top - 3)); break;


    //case ISUB: putInt(stack, top - 2, -popInt(stack, top - 1) + popInt(stack, top - 2)); break;
    public static void isub(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c2 - c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        symbolicIntOp(OperatorComparator.ISUB, symbolic, top, c2, c1, concResult, true);
    }

    //case LSUB: putLong(stack, top - 4, -popLong(stack, top - 1) + popLong(stack, top - 3)); break;
    //case FSUB: putFloat(stack, top - 2, -popFloat(stack, top - 1) + popFloat(stack, top - 2)); break;
    //case DSUB: putDouble(stack, top - 4, -popDouble(stack, top - 1) + popDouble(stack, top - 3)); break;

    // case IMUL: putInt(stack, top - 2, popInt(stack, top - 1) * popInt(stack, top - 2)); break;
    public static void imul(long[] primitives,  Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c1 * c2;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        symbolicIntOp(OperatorComparator.IMUL, symbolic, top, c1, c2, concResult);
    }

    // case LMUL: putLong(stack, top - 4, popLong(stack, top - 1) * popLong(stack, top - 3)); break;
    // case FMUL: putFloat(stack, top - 2, popFloat(stack, top - 1) * popFloat(stack, top - 2)); break;
    // case DMUL: putDouble(stack, top - 4, popDouble(stack, top - 1) * popDouble(stack, top - 3)); break;


    private static void checkNonZero(int value, AnnotatedValue a, BytecodeNode bn) {
        if (value != 0) {
            if (a != null) {
                addTraceElement(new PathCondition( new ComplexExpression(
                        OperatorComparator.NE, a.symbolic(), Constant.INT_ZERO),0, 2));
            }
        }
        else {
            if (a != null) {
                addTraceElement(new PathCondition(new ComplexExpression(
                        OperatorComparator.EQ, a.symbolic(), Constant.INT_ZERO), 1, 2));
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
        symbolicIntOp(OperatorComparator.IDIV, symbolic, top, c2, c1, concResult, true);
    }

    // case LDIV: putLong(stack, top - 4, divLong(checkNonZero(popLong(stack, top - 1)), popLong(stack, top - 3))); break;
    // case FDIV: putFloat(stack, top - 2, divFloat(popFloat(stack, top - 1), popFloat(stack, top - 2))); break;
    // case DDIV: putDouble(stack, top - 4, divDouble(popDouble(stack, top - 1), popDouble(stack, top - 3))); break;

    // case IREM: putInt(stack, top - 2, remInt(checkNonZero(popInt(stack, top - 1)), popInt(stack, top - 2))); break;
    public static void irem(long[] primitives, Object[] symbolic, int top, BytecodeNode bn) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        checkNonZero(c1, peekSymbolic(symbolic, top -1), bn);
        int concResult = c2 % c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        symbolicIntOp(OperatorComparator.IREM, symbolic, top, c2, c1, concResult, true);
    }

    // case LREM: putLong(stack, top - 4, remLong(checkNonZero(popLong(stack, top - 1)), popLong(stack, top - 3))); break;
    // case FREM: putFloat(stack, top - 2, remFloat(popFloat(stack, top - 1), popFloat(stack, top - 2))); break;
    // case DREM: putDouble(stack, top - 4, remDouble(popDouble(stack, top - 1), popDouble(stack, top - 3))); break;

    // case INEG: putInt(stack, top - 1, -popInt(stack, top - 1)); break;
    public static void ineg(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int concResult = -c1;
        BytecodeNode.putInt(primitives, top - 1, concResult);
        unarySymbolicIntOp(OperatorComparator.INEG, symbolic, top, c1, concResult);
    }

    // case LNEG: putLong(stack, top - 2, -popLong(stack, top - 1)); break;
    // case FNEG: putFloat(stack, top - 1, -popFloat(stack, top - 1)); break;
    // case DNEG: putDouble(stack, top - 2, -popDouble(stack, top - 1)); break;

    // case ISHL: putInt(stack, top - 2, shiftLeftInt(popInt(stack, top - 1), popInt(stack, top - 2))); break;
    public static void ishl(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c2 << c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        symbolicIntOp(OperatorComparator.ISHL, symbolic, top, c2, c1, concResult, true);
    }

    // case LSHL: putLong(stack, top - 3, shiftLeftLong(popInt(stack, top - 1), popLong(stack, top - 2))); break;

    // case ISHR: putInt(stack, top - 2, shiftRightSignedInt(popInt(stack, top - 1), popInt(stack, top - 2))); break;
    public static void ishr(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c2 >> c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        symbolicIntOp(OperatorComparator.ISHR, symbolic, top, c2, c1, concResult, true);
    }

    // case LSHR: putLong(stack, top - 3, shiftRightSignedLong(popInt(stack, top - 1), popLong(stack, top - 2))); break;

    // case IUSHR: putInt(stack, top - 2, shiftRightUnsignedInt(popInt(stack, top - 1), popInt(stack, top - 2))); break;
    public static void iushr(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c2 >>> c1;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        symbolicIntOp(OperatorComparator.IUSHR, symbolic, top, c2, c1, concResult, true);
    }

    // case LUSHR: putLong(stack, top - 3, shiftRightUnsignedLong(popInt(stack, top - 1), popLong(stack, top - 2))); break;

    // case IAND: putInt(stack, top - 2, popInt(stack, top - 1) & popInt(stack, top - 2)); break;
    public static void iand(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c1 & c2;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        symbolicIntOp(OperatorComparator.IAND, symbolic, top, c1, c2, concResult);
    }

    // case LAND: putLong(stack, top - 4, popLong(stack, top - 1) & popLong(stack, top - 3)); break;

    //  case IOR: putInt(stack, top - 2, popInt(stack, top - 1) | popInt(stack, top - 2)); break;
    public static void ior(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c1 | c2;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        symbolicIntOp(OperatorComparator.IOR, symbolic, top, c1, c2, concResult);
    }

    // case LOR: putLong(stack, top - 4, popLong(stack, top - 1) | popLong(stack, top - 3)); break;

    // case IXOR: putInt(stack, top - 2, popInt(stack, top - 1) ^ popInt(stack, top - 2)); break;
    public static void ixor(long[] primitives, Object[] symbolic, int top) {
        int c1 = BytecodeNode.popInt(primitives, top - 1);
        int c2 = BytecodeNode.popInt(primitives, top - 2);
        int concResult = c1 ^ c2;
        BytecodeNode.putInt(primitives, top - 2, concResult);
        symbolicIntOp(OperatorComparator.IXOR, symbolic, top, c1, c2, concResult);
    }

    // case LXOR: putLong(stack, top - 4, popLong(stack, top - 1) ^ popLong(stack, top - 3)); break;

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
    // case I2F: putFloat(stack, top - 1, popInt(stack, top - 1)); break;
    // case I2D: putDouble(stack, top - 1, popInt(stack, top - 1)); break;

    // case L2I: putInt(stack, top - 2, (int) popLong(stack, top - 1)); break;
    // case L2F: putFloat(stack, top - 2, popLong(stack, top - 1)); break;
    // case L2D: putDouble(stack, top - 2, popLong(stack, top - 1)); break;

    // case F2I: putInt(stack, top - 1, (int) popFloat(stack, top - 1)); break;
    // case F2L: putLong(stack, top - 1, (long) popFloat(stack, top - 1)); break;
    // case F2D: putDouble(stack, top - 1, popFloat(stack, top - 1)); break;

    // case D2I: putInt(stack, top - 2, (int) popDouble(stack, top - 1)); break;
    // case D2L: putLong(stack, top - 2, (long) popDouble(stack, top - 1)); break;
    // case D2F: putFloat(stack, top - 2, (float) popDouble(stack, top - 1)); break;

    // case I2B: putInt(stack, top - 1, (byte) popInt(stack, top - 1)); break;
    // case I2C: putInt(stack, top - 1, (char) popInt(stack, top - 1)); break;
    // case I2S: putInt(stack, top - 1, (short) popInt(stack, top - 1)); break;

    // case LCMP : putInt(stack, top - 4, compareLong(popLong(stack, top - 1), popLong(stack, top - 3))); break;
    // case FCMPL: putInt(stack, top - 2, compareFloatLess(popFloat(stack, top - 1), popFloat(stack, top - 2))); break;
    // case FCMPG: putInt(stack, top - 2, compareFloatGreater(popFloat(stack, top - 1), popFloat(stack, top - 2))); break;
    // case DCMPL: putInt(stack, top - 4, compareDoubleLess(popDouble(stack, top - 1), popDouble(stack, top - 3))); break;
    // case DCMPG: putInt(stack, top - 4, compareDoubleGreater(popDouble(stack, top - 1), popDouble(stack, top - 3))); break;


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
            switch (opcode) {
                case IFEQ: expr = !takeBranch ? s1.symbolic() : new ComplexExpression(OperatorComparator.BNEG, s1.symbolic()); break;
                //TODO: add cases
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("expecting IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE");
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
            if (s1 == null) s1 = AnnotatedValue.fromInt(c1);
            if (s2 == null) s2 = AnnotatedValue.fromInt(c2);

            Expression expr = null;
            switch (opcode) {
                case IF_ICMPEQ : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.EQ :
                        OperatorComparator.NE, s1.symbolic(), s2.symbolic()); break;
                case IF_ICMPNE : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.NE :
                        OperatorComparator.EQ, s1.symbolic(), s2.symbolic()); break;
                case IF_ICMPLT : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.GT :
                        OperatorComparator.LE, s1.symbolic(), s2.symbolic()); break;
                case IF_ICMPGE : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.LE :
                        OperatorComparator.GT, s1.symbolic(), s2.symbolic()); break;
                case IF_ICMPGT : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.LT :
                        OperatorComparator.GE, s1.symbolic(), s2.symbolic()); break;
                case IF_ICMPLE : expr = new ComplexExpression(takeBranch ?
                        OperatorComparator.GE :
                        OperatorComparator.LT, s1.symbolic(), s2.symbolic()); break;
                default        :
                    CompilerDirectives.transferToInterpreter();
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
