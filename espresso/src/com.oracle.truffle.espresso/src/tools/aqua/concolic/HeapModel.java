/*
 * Copyright (c) 2026 Automated Quality Assurance Group, TU Dortmund University.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import tools.aqua.smt.*;
import tools.aqua.spout.Annotations;
import tools.aqua.spout.Config;
import tools.aqua.spout.SPouT;
import tools.aqua.spout.Trace;

import java.util.*;

import static tools.aqua.concolic.PathCondition.*;
import static tools.aqua.smt.OperatorComparator.*;
import static tools.aqua.smt.Types.*;

/**
 * Symbolic heap without assumptions on its shape.
 *
 * <p>Objects are identities (sort {@code Int}), fields and array elements are uninterpreted functions of them:
 * {@code (fld.<class>.<field> o)}, {@code (arr.length a)}, {@code (arr.<kind> a i)}, {@code (obj.cls o)}. A
 * reference carries a term in its slot annotation: {@code __object_k} for a nondet object, {@code (fld.Pair.a
 * __object_0)} for the result of reading {@code p.a}. Aliasing is equality of terms; the solver decides it with the
 * constructor summaries, which describe the heap built by a constructor exactly (see {@link #summarize}).
 *
 * <p>The objects reachable from a nondet object when it is created are <em>initial</em>. A field read through a term
 * yields the function application, unless the program wrote the field before: writes to initial objects are logged
 * per field, and a read through term {@code t} decides for each logged write (newest first) whether {@code t} denotes
 * the written object ({@code (= t t_w)}, a branch of the path). Objects allocated by the program carry no term; the
 * terms of references stored in their fields are kept in the field annotations.
 */
public class HeapModel {

    /** elements of an array that are described by summaries and read through terms (cf. annotateArray) */
    public static final int MAX_ARRAY_ELEMENTS = 64;

    /** objects of the heap of a summary; further objects are not described (the summary is incomplete) */
    public static final int MAX_OBJECTS = 10_000;

    public static final String CLS = "obj.cls";
    public static final String OWNER = "obj.owner";
    public static final String IDX = "obj.idx";
    public static final String LENGTH = "arr.length";
    /** the content of a string (sort String): strings are objects, their content is a function of their identity */
    public static final String STR_VAL = "str.val";
    /** the content of a StringBuilder or StringBuffer (sort String), a ghost field written by their mutations */
    public static final String SB_VAL = "sb.val";

    private final Config config;
    private final Trace trace;
    private final Set<String> declared = new HashSet<>();
    private final Set<StaticObject> initial = Collections.newSetFromMap(new IdentityHashMap<>());

    /** a write to a field (or array element) of an initial object */
    private record Write(StaticObject receiver, Expression receiverTerm, int index, Expression indexExpr,
                         Annotations value) {}

    /** writes per field ({@link Field}) and per array element kind ({@link Character}), oldest first */
    private final Map<Object, List<Write>> writes = new HashMap<>();

    public HeapModel(Config config) {
        this.config = config;
        this.trace = config.getTrace();
    }

    // --------------------------------------------------------------------------
    //
    // names and declarations

    @CompilerDirectives.TruffleBoundary
    private void declare(String name, Types result, Types... arguments) {
        if (declared.isEmpty()) {
            declared.add(CLS);
            trace.addElement(new FunctionDeclaration(CLS, KLASS, OBJECT));
        }
        if (declared.add(name)) {
            trace.addElement(new FunctionDeclaration(name, result, arguments));
        }
    }

    static String fieldFunction(Field f) {
        return "fld." + f.getDeclaringKlass().getNameAsString() + "." + f.getNameAsString();
    }

    private static Types sort(JavaKind kind) {
        return switch (kind) {
            case Boolean -> BOOL;
            case Byte -> BYTE;
            case Short -> SHORT;
            case Char -> CHAR;
            case Int -> INT;
            case Float -> FLOAT;
            case Long -> LONG;
            case Double -> DOUBLE;
            default -> OBJECT;
        };
    }

    private static String elementFunction(JavaKind kind) {
        return "arr." + kind.getTypeChar();
    }

    /** values of sub-word sorts are ints on the operand stack */
    private static Expression widen(JavaKind kind, Expression e) {
        return switch (kind) {
            case Byte -> new ComplexExpression(B2I, e);
            case Short -> new ComplexExpression(S2I, e);
            case Char -> new ComplexExpression(C2I, e);
            default -> e;
        };
    }

    @CompilerDirectives.TruffleBoundary
    public Atom fieldTerm(Field f, Expression receiver) {
        Types s = sort(f.getKind());
        declare(fieldFunction(f), s, OBJECT);
        return new FunctionTerm(fieldFunction(f), s, receiver);
    }

    @CompilerDirectives.TruffleBoundary
    public Atom lengthTerm(Expression array) {
        declare(LENGTH, INT, OBJECT);
        return new FunctionTerm(LENGTH, INT, array);
    }

    @CompilerDirectives.TruffleBoundary
    public Atom elementTerm(JavaKind kind, Expression array, Expression index) {
        Types s = sort(kind);
        declare(elementFunction(kind), s, OBJECT, INT);
        return new FunctionTerm(elementFunction(kind), s, array, index);
    }

    /** the content of the string denoted by {@code string} */
    @CompilerDirectives.TruffleBoundary
    public Atom contentTerm(Expression string) {
        declare(STR_VAL, STRING, OBJECT);
        return new FunctionTerm(STR_VAL, STRING, string);
    }

    /** the initial content of the builder denoted by {@code builder} */
    @CompilerDirectives.TruffleBoundary
    public Atom builderContentTerm(Expression builder) {
        declare(SB_VAL, STRING, OBJECT);
        return new FunctionTerm(SB_VAL, STRING, builder);
    }

    /** the class of an object: {@code __object_k.cls} for a nondet object, {@code (obj.cls t)} otherwise */
    @CompilerDirectives.TruffleBoundary
    public Atom clsTerm(Atom object) {
        if (object instanceof FunctionTerm || object.toString().startsWith("__heap_")
                || object.toString().startsWith("__static_") || object.toString().startsWith("__lit_")) {
            declare(CLS, KLASS, OBJECT);
            return new FunctionTerm(CLS, KLASS, object);
        }
        return new AuxiliaryVariable(object + ".cls", KLASS);
    }

    /** @return whether the expression denotes an object identity (a term of sort Int, not {@code null}) */
    @CompilerDirectives.TruffleBoundary
    public static boolean isObjectTerm(Object e) {
        return e instanceof Atom a && a.getType() == OBJECT && !(a instanceof Constant) && !"null".equals(a.toString());
    }

    // --------------------------------------------------------------------------
    //
    // objects that are part of the heap

    @CompilerDirectives.TruffleBoundary
    static boolean isVmShared(StaticObject o, Meta meta) {
        if (StaticObject.isNull(o)) {
            return false;
        }
        Klass k = o.getKlass();
        return k == meta.java_lang_Class || k == meta.java_lang_Module || meta.java_lang_ClassLoader.isAssignableFrom(k);
    }

    static boolean skipField(Field field, Meta meta) {
        // VM-internal fields (e.g. the module entry of a Class) do not hold guest objects
        return field.isHidden()
                || field.getDeclaringKlass() == meta.java_lang_Throwable
                || field.getDeclaringKlass() == meta.java_lang_Thread
                || field.getDeclaringKlass().getNameAsString().contains("ThreadLocal");
    }

    /**
     * Objects that are not part of the symbolic heap: their references keep the annotations of their slots. Strings
     * are part of it if their contents are symbolic ({@code concolic.symbolic.string.fields}).
     */
    private boolean isOpaque(StaticObject o, Meta meta) {
        return StaticObject.isNull(o) || isVmShared(o, meta) || (isString(o, meta) && !config.isSymbolicStringFields());
    }

    static boolean isString(StaticObject o, Meta meta) {
        return StaticObject.notNull(o) && o.getKlass() == meta.java_lang_String;
    }

    /** StringBuilder and StringBuffer: described by their content (sb.val), not by their internal fields */
    public static boolean isBuilder(StaticObject o) {
        if (StaticObject.isNull(o) || o.isArray()) {
            return false;
        }
        String name = o.getKlass().getNameAsString();
        return name.equals("java/lang/StringBuilder") || name.equals("java/lang/StringBuffer");
    }

    /**
     * Name of an interned string (e.g., a literal): interned strings are shared by all objects that refer to them,
     * so their name depends only on their content.
     */
    private static String literalName(String content) {
        if (content.length() <= 32) {
            StringBuilder sb = new StringBuilder("__lit_");
            for (char c : content.toCharArray()) {
                sb.append(String.format("%04x", (int) c));
            }
            return sb.toString();
        }
        return "__lit_h" + Integer.toHexString(content.hashCode()) + "_" + content.length();
    }

    private static boolean isStringField(Field f, Meta meta) {
        return f.getType() == meta.java_lang_String.getType();
    }

    /**
     * The fields and array elements by which the heap of a summary is described, as (edge, target) pairs. Strings
     * are described by their content, not by their fields.
     */
    private void edges(StaticObject o, Meta meta, EdgeVisitor visitor) {
        if (isString(o, meta) || isBuilder(o)) {
            return;
        }
        if (o.isArray()) {
            ArrayKlass ak = (ArrayKlass) o.getKlass();
            if (ak.getComponentType().isPrimitive()) {
                return;
            }
            Object[] elements = o.unwrap(meta.getLanguage());
            int n = Math.min(elements.length, MAX_ARRAY_ELEMENTS);
            for (int j = 0; j < n; j++) {
                visitor.visit(null, j, (StaticObject) elements[j]);
            }
            return;
        }
        for (Field f : ((ObjectKlass) o.getKlass()).getFieldTable()) {
            if (f.getKind().isObject() && !skipField(f, meta)
                    && (config.isSymbolicStringFields() || !isStringField(f, meta))) {
                visitor.visit(f, -1, f.getObject(o));
            }
        }
    }

    private interface EdgeVisitor {
        void visit(Field field, int index, StaticObject target);
    }

    /**
     * Main mode: marks the objects reachable from a new nondet object as initial (breadth first by identity, as
     * {@link #summarize}). Reads of their fields through terms yield the function applications of the summaries.
     */
    @CompilerDirectives.TruffleBoundary
    public void markInitial(StaticObject root, Meta meta) {
        if (StaticObject.isNull(root)) {
            return;
        }
        ArrayDeque<StaticObject> queue = new ArrayDeque<>();
        if (initial.add(root)) {
            queue.add(root);
        }
        int count = 0;
        while (!queue.isEmpty() && count++ < MAX_OBJECTS) {
            StaticObject o = queue.poll();
            edges(o, meta, (field, index, target) -> {
                // strings are immutable: their fields are not read through terms, writes need no log
                if (!isOpaque(target, meta) && !isString(target, meta) && initial.add(target)) {
                    queue.add(target);
                }
            });
        }
    }

    // --------------------------------------------------------------------------
    //
    // reads and writes of fields

    private Annotations withTerm(Annotations slot, Expression term) {
        Object[] values = slot != null ? slot.getAnnotations().clone() : Annotations.create().getAnnotations();
        values[config.getConcolicIdx()] = term;
        return Annotations.create(values);
    }

    private Expression term(Annotations a) {
        Object e = Annotations.annotation(a, config.getConcolicIdx());
        return isObjectTerm(e) ? (Expression) e : null;
    }

    /**
     * Annotation of the value of field {@code f} of {@code receiver}, read through a reference with annotation
     * {@code receiverA}; {@code slot} is the annotation stored in the field.
     */
    @CompilerDirectives.TruffleBoundary
    public Annotations readField(StaticObject receiver, Annotations receiverA, Field f, Annotations slot, Meta meta) {
        Expression t = term(receiverA);
        if (t == null || !initial.contains(receiver) || skipField(f, meta) || isBuilder(receiver)) {
            // the internal fields of builders are not described by summaries (their content is, see sb.val)
            return slot;
        }
        boolean string = isStringField(f, meta);
        if (string && !config.isSymbolicStringFields()) {
            return slot;
        }
        StaticObject value = f.getKind().isObject() ? f.getObject(receiver) : null;
        if (value != null && isOpaque(value, meta) && !StaticObject.isNull(value)) {
            return slot;
        }
        List<Write> log = writes.get(f);
        if (log != null) {
            for (int i = log.size() - 1; i >= 0; i--) {
                Write w = log.get(i);
                if (aliases(w, receiver, t, null, -1)) {
                    return w.value();
                }
            }
        }
        Expression read = widen(f.getKind(), fieldTerm(f, t));
        if (value != null) {
            nameObject(value, read, meta);
        }
        return withTerm(slot, read);
    }

    /**
     * The content of a builder that is read through a reference with annotation {@code ref}: the content of the
     * newest aliasing write (mutation) of an initial builder, its initial content {@code (sb.val t)} if there is
     * none; {@code null} if the reference has no term (the content is the object annotation).
     */
    @CompilerDirectives.TruffleBoundary
    public Annotations readBuilderContent(StaticObject builder, Annotations ref) {
        Expression t = term(ref);
        if (t == null || !initial.contains(builder)) {
            return null;
        }
        List<Write> log = writes.get(SB_VAL);
        if (log != null) {
            for (int i = log.size() - 1; i >= 0; i--) {
                Write w = log.get(i);
                if (aliases(w, builder, t, null, -1)) {
                    return w.value();
                }
            }
        }
        return withTerm(null, builderContentTerm(t));
    }

    /** a mutation of a builder through a reference with annotation {@code ref}: a write of its content */
    @CompilerDirectives.TruffleBoundary
    public void writeBuilderContent(StaticObject builder, Annotations ref, Annotations content) {
        if (initial.contains(builder)) {
            writes.computeIfAbsent(SB_VAL, k -> new ArrayList<>()).add(new Write(builder, term(ref), -1, null, content));
        }
    }

    @CompilerDirectives.TruffleBoundary
    public void writeField(StaticObject receiver, Annotations receiverA, Field f, Annotations value) {
        if (initial.contains(receiver)) {
            writes.computeIfAbsent(f, k -> new ArrayList<>()).add(new Write(receiver, term(receiverA), -1, null, value));
        }
    }

    /**
     * Whether the logged write {@code w} wrote the element read now (object {@code receiver} through term {@code t},
     * index {@code index}); records the decision unless it is syntactically determined.
     */
    private boolean aliases(Write w, StaticObject receiver, Expression t, Expression indexExpr, int index) {
        boolean same = w.receiver() == receiver && w.index() == index;
        if (w.receiverTerm() == null) {
            // written through a reference without term: concrete
            return same;
        }
        Expression cond = null;
        if (!w.receiverTerm().toString().equals(t.toString())) {
            cond = new ComplexExpression(OBJECT_EQ, t, w.receiverTerm());
        }
        if (indexExpr != null && !w.indexExpr().toString().equals(indexExpr.toString())) {
            if (w.indexExpr() instanceof Constant && indexExpr instanceof Constant) {
                return false;
            }
            Expression eq = new ComplexExpression(BVEQ, indexExpr, w.indexExpr());
            cond = cond == null ? eq : new ComplexExpression(BAND, cond, eq);
        }
        if (cond == null) {
            // the same term denotes the same object (unless a reference fell back to the first term of its object)
            return same;
        }
        trace.addElement(new PathCondition(same ? cond : new ComplexExpression(BNEG, cond),
                same ? FAILURE : SUCCESS, BINARY_SPLIT));
        return same;
    }

    /**
     * References lose their slot annotations in some places (e.g. receivers of substituted methods); such uses fall
     * back to the object annotation. Objects read through a term keep the first term as object annotation.
     */
    private void nameObject(StaticObject value, Expression term, Meta meta) {
        // the object annotation of an array is its length, of a string its content
        if (StaticObject.isNull(value) || value.isArray() || isString(value, meta) || !(term instanceof Atom)) {
            return;
        }
        if (Annotations.annotation(Annotations.objectAnnotation(value), config.getConcolicIdx()) == null) {
            Annotations a = Annotations.objectAnnotation(value);
            Annotations.setObjectAnnotation(value, withTerm(a, term));
        }
    }

    // --------------------------------------------------------------------------
    //
    // arrays

    @CompilerDirectives.TruffleBoundary
    public Expression arrayLength(StaticObject array, Annotations arrayA) {
        Expression t = term(arrayA);
        if (t == null || !initial.contains(array)) {
            return null;
        }
        return lengthTerm(t);
    }

    @CompilerDirectives.TruffleBoundary
    public Annotations readElement(StaticObject array, Annotations arrayA, int index, Expression indexExpr,
                                   Annotations slot, Meta meta) {
        Expression t = term(arrayA);
        if (t == null || !initial.contains(array) || index < 0 || index >= array.length(meta.getLanguage())) {
            return slot;
        }
        JavaKind kind = ((ArrayKlass) array.getKlass()).getComponentType().getJavaKind();
        StaticObject value = null;
        if (kind.isObject()) {
            value = (StaticObject) ((Object[]) array.unwrap(meta.getLanguage()))[index];
            if (isOpaque(value, meta) && !StaticObject.isNull(value)) {
                return slot;
            }
        }
        Expression idx = indexExpr != null ? indexExpr : Constant.fromConcreteValue(index);
        List<Write> log = writes.get(kind.getTypeChar());
        if (log != null) {
            for (int i = log.size() - 1; i >= 0; i--) {
                Write w = log.get(i);
                if (aliases(w, array, t, idx, index)) {
                    return w.value();
                }
            }
        }
        if (index >= MAX_ARRAY_ELEMENTS) {
            // not described by summaries
            return slot;
        }
        Expression read = widen(kind, elementTerm(kind, t, idx));
        if (value != null) {
            nameObject(value, read, meta);
        }
        return withTerm(slot, read);
    }

    @CompilerDirectives.TruffleBoundary
    public void writeElement(StaticObject array, Annotations arrayA, int index, Expression indexExpr,
                             Annotations value) {
        if (initial.contains(array)) {
            JavaKind kind = ((ArrayKlass) array.getKlass()).getComponentType().getJavaKind();
            Expression idx = indexExpr != null ? indexExpr : Constant.fromConcreteValue(index);
            writes.computeIfAbsent(kind.getTypeChar(), k -> new ArrayList<>())
                    .add(new Write(array, term(arrayA), index, idx, value));
        }
    }

    // --------------------------------------------------------------------------
    //
    // constructor summaries

    /**
     * Summary mode: describes the heap reachable from the constructed object {@code root} (named
     * {@code __object_0}) exactly, by identity: every reachable object gets a name ({@code __heap_0_j}, or
     * {@code __static_<class>_<field>} for an object held by a static field), its class, its fields and array
     * elements (values, names of objects, or {@code null}), and its owner: objects allocated by the construction
     * belong to {@code __object_0} and are pairwise distinct ({@code obj.idx}); objects held by static fields
     * existed before and belong to none ({@code obj.owner} = -1). Strings are described by their content
     * ({@code str.val}); interned strings (e.g. literals) are named by their content ({@code __lit_...}) and belong to
     * none, as they are shared by all objects that refer to them.
     */
    @CompilerDirectives.TruffleBoundary
    public void summarize(StaticObject root, Atom rootName, Meta meta) {
        int cIdx = config.getConcolicIdx();
        Expression rootNull = new ComplexExpression(OBJECT_IS_NULL, rootName, Constant.NULL);
        if (StaticObject.isNull(root)) {
            trace.addElement(new ConstructorCondition(rootNull));
            return;
        }
        trace.addElement(new ConstructorCondition(new ComplexExpression(BNEG, rootNull)));
        declare(OWNER, INT, OBJECT);
        declare(IDX, INT, OBJECT);

        Map<StaticObject, Atom> names = new IdentityHashMap<>();
        Map<StaticObject, String> statics = new IdentityHashMap<>();
        Set<Klass> scanned = new HashSet<>();
        scanStatics(root.getKlass(), statics, scanned, meta);
        names.put(root, rootName);
        ArrayDeque<StaticObject> queue = new ArrayDeque<>();
        queue.add(root);
        int[] next = {1};
        boolean[] truncated = {false};
        while (!queue.isEmpty()) {
            StaticObject o = queue.poll();
            Atom name = names.get(o);
            if (o.isArray()) {
                summarizeArray(o, name, cIdx, meta);
            } else if (isString(o, meta)) {
                Expression e = Annotations.annotation(Annotations.objectAnnotation(o), cIdx);
                trace.addElement(new ConstructorCondition(new ComplexExpression(STRINGEQ, contentTerm(name),
                        e != null ? e : Constant.fromConcreteValue(meta.toHostString(o)))));
            } else if (isBuilder(o)) {
                Expression e = Annotations.annotation(Annotations.objectAnnotation(o), cIdx);
                trace.addElement(new ConstructorCondition(new ComplexExpression(STRINGEQ, builderContentTerm(name),
                        e != null ? e : Constant.fromConcreteValue(SPouT.builderToHostString(o, meta)))));
            } else {
                summarizePrimitiveFields(o, name, cIdx, meta);
            }
            edges(o, meta, (field, index, target) -> {
                Atom slot = field != null ? fieldTerm(field, name)
                        : elementTerm(JavaKind.Object, name, Constant.fromConcreteValue(index));
                Expression value;
                if (StaticObject.isNull(target)) {
                    value = Constant.NULL;
                } else if (isOpaque(target, meta)) {
                    return;
                } else if (names.containsKey(target)) {
                    value = names.get(target);
                } else {
                    scanStatics(target.getKlass(), statics, scanned, meta);
                    if (field != null) {
                        scanStatics(field.getDeclaringKlass(), statics, scanned, meta);
                    }
                    Atom n;
                    boolean preexisting = true;
                    if (isString(target, meta) && meta.getContext().getStrings().isInterned(target)) {
                        n = new AuxiliaryVariable(literalName(meta.toHostString(target)), OBJECT);
                    } else if (statics.containsKey(target)) {
                        n = new AuxiliaryVariable(statics.get(target), OBJECT);
                    } else if (next[0] < MAX_OBJECTS) {
                        n = new AuxiliaryVariable("__heap_0_" + next[0], OBJECT);
                        preexisting = false;
                    } else {
                        truncated[0] = true;
                        return;
                    }
                    names.put(target, n);
                    queue.add(target);
                    trace.addElement(new SymbolDeclaration(n));
                    trace.addElement(new ConstructorCondition(new ComplexExpression(BNEG,
                            new ComplexExpression(OBJECT_IS_NULL, n, Constant.NULL))));
                    trace.addElement(new ConstructorCondition(new ComplexExpression(STRINGEQ, clsTerm(n),
                            Expression.fromConstant(KLASS, target.getKlass()))));
                    if (preexisting) {
                        trace.addElement(new ConstructorCondition(new ComplexExpression(BVEQ,
                                new FunctionTerm(OWNER, INT, n), Constant.fromConcreteValue(-1))));
                    } else {
                        trace.addElement(new ConstructorCondition(new ComplexExpression(BVEQ,
                                new FunctionTerm(OWNER, INT, n), new FunctionTerm(OWNER, INT, rootName))));
                        trace.addElement(new ConstructorCondition(new ComplexExpression(BVEQ,
                                new FunctionTerm(IDX, INT, n), Constant.fromConcreteValue(next[0]))));
                        next[0]++;
                    }
                    value = n;
                }
                trace.addElement(new ConstructorCondition(new ComplexExpression(OBJECT_EQ, slot, value)));
            });
        }
        if (truncated[0]) {
            SPouT.losePrecision("Only " + MAX_OBJECTS + " objects of the heap of a constructed object are described "
                    + "by its summary. May lose precision.", meta);
        }
    }

    /** objects held by static reference fields of {@code k} and its super classes, named by the field */
    private void scanStatics(Klass k, Map<StaticObject, String> statics, Set<Klass> scanned, Meta meta) {
        for (; k instanceof ObjectKlass ok && scanned.add(ok); k = ok.getSuperKlass()) {
            if (!ok.isInitialized()) {
                continue;
            }
            for (Field f : ok.getStaticFieldTable()) {
                if (!f.getKind().isObject()) {
                    continue;
                }
                StaticObject v = f.getObject(ok.getStatics());
                if (!isOpaque(v, meta)) {
                    statics.putIfAbsent(v, "__static_" + ok.getNameAsString() + "_" + f.getNameAsString());
                }
            }
        }
    }

    private void summarizePrimitiveFields(StaticObject o, Atom name, int cIdx, Meta meta) {
        Annotations[] objAnnotations = o.getAnnotations();
        for (Field f : ((ObjectKlass) o.getKlass()).getFieldTable()) {
            if (skipField(f, meta) || !f.getKind().isPrimitive()) {
                continue;
            }
            Annotations a = objAnnotations != null ? objAnnotations[f.getSlot()] : null;
            trace.addElement(new ConstructorCondition(valueEquation(f.getKind(), fieldTerm(f, name), f.getValue(o),
                    Annotations.annotation(a, cIdx))));
        }
    }

    private void summarizeArray(StaticObject array, Atom name, int cIdx, Meta meta) {
        ArrayKlass ak = (ArrayKlass) array.getKlass();
        int length = array.length(meta.getLanguage());
        Annotations[] aa = array.getAnnotations();
        Expression lengthExpr = aa != null ? Annotations.annotation(aa[length], cIdx) : null;
        trace.addElement(new ConstructorCondition(valueEquation(JavaKind.Int, lengthTerm(name), length, lengthExpr)));
        if (!ak.getComponentType().isPrimitive()) {
            return;
        }
        JavaKind kind = ak.getComponentType().getJavaKind();
        int n = Math.min(length, MAX_ARRAY_ELEMENTS);
        if (n < length) {
            SPouT.losePrecision("Only the first " + n + " of " + length
                    + " array elements are described by the summary. May lose precision.", meta);
        }
        for (int j = 0; j < n; j++) {
            Expression e = aa != null ? Annotations.annotation(aa[j], cIdx) : null;
            trace.addElement(new ConstructorCondition(valueEquation(kind,
                    elementTerm(kind, name, Constant.fromConcreteValue(j)), elementValue(array, j, kind, meta), e)));
        }
    }

    private static Object elementValue(StaticObject array, int j, JavaKind kind, Meta meta) {
        return switch (kind) {
            case Boolean -> ((boolean[]) array.unwrap(meta.getLanguage()))[j];
            case Byte -> ((byte[]) array.unwrap(meta.getLanguage()))[j];
            case Char -> ((char[]) array.unwrap(meta.getLanguage()))[j];
            case Short -> ((short[]) array.unwrap(meta.getLanguage()))[j];
            case Int -> ((int[]) array.unwrap(meta.getLanguage()))[j];
            case Long -> ((long[]) array.unwrap(meta.getLanguage()))[j];
            case Float -> ((float[]) array.unwrap(meta.getLanguage()))[j];
            case Double -> ((double[]) array.unwrap(meta.getLanguage()))[j];
            default -> throw new IllegalArgumentException("not a primitive kind: " + kind);
        };
    }

    /**
     * {@code (= slot value)} for a primitive slot: the symbolic value if there is one (narrowed to the sort of the
     * slot: values on the operand stack are ints), the concrete value otherwise.
     */
    static Expression valueEquation(JavaKind kind, Atom slot, Object value, Expression symbolic) {
        return switch (kind) {
            case Boolean -> new ComplexExpression(BEQUIV, slot,
                    symbolic != null ? symbolic : Constant.fromConcreteValue((boolean) value));
            case Byte -> new ComplexExpression(BVEQ, slot,
                    symbolic != null ? new ComplexExpression(I2B, symbolic) : Constant.fromConcreteValue((byte) value));
            case Short -> new ComplexExpression(BVEQ, slot,
                    symbolic != null ? new ComplexExpression(I2S, symbolic) : Constant.fromConcreteValue((short) value));
            case Char -> new ComplexExpression(BVEQ, slot,
                    symbolic != null ? new ComplexExpression(I2C, symbolic) : Constant.fromConcreteValue((char) value));
            case Int -> new ComplexExpression(BVEQ, slot,
                    symbolic != null ? symbolic : Constant.fromConcreteValue((int) value));
            case Long -> new ComplexExpression(BVEQ, slot,
                    symbolic != null ? symbolic : Constant.fromConcreteValue((long) value));
            case Float -> new ComplexExpression(FPEQ, slot,
                    symbolic != null ? symbolic : Constant.fromConcreteValue((float) value));
            case Double -> new ComplexExpression(FPEQ, slot,
                    symbolic != null ? symbolic : Constant.fromConcreteValue((double) value));
            default -> throw new IllegalArgumentException("not a primitive kind: " + kind);
        };
    }
}
