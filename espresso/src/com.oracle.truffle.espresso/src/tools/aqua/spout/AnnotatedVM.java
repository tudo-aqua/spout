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
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import static com.oracle.truffle.espresso.runtime.dispatch.staticobject.EspressoInterop.getMeta;


public class AnnotatedVM {

    // --------------------------------------------------------------------------
    //
    // stack

    private static final int ANNOTATION_SLOT = 1;
    private static final int VALUES_START = 2;

    private static Annotations[] getAnnotations(Frame frame) {
        return (Annotations[]) frame.getObject(ANNOTATION_SLOT);
    }

    public static Annotations peekAnnotations(Frame frame, int slot){
        Annotations[] annotations = getAnnotations(frame);
        if (annotations != null && (slot-VALUES_START) >= 0 && (slot-VALUES_START) < annotations.length) {
            return annotations[slot - VALUES_START];
        }
        return null;
    }

    public static Annotations popAnnotations(Frame frame, int slot) {
        Annotations[] annotations = getAnnotations(frame);
        Annotations result = annotations[slot - VALUES_START];
        annotations[slot - VALUES_START] = null;
        return result;
    }

    public static void putAnnotations(Frame frame, int slot, Annotations value) {
        Annotations[] annotations = getAnnotations(frame);
        annotations[slot - VALUES_START] = value;
    }

    public static Annotations getLocalAnnotations(Frame frame, int slot) {
        Annotations[] annotations = getAnnotations(frame);
        Annotations result = annotations[slot];
        return result;
    }

    public static void setLocalAnnotations(Frame frame, int slot, Annotations value) {
        Annotations[] annotations = getAnnotations(frame);
        annotations[slot] = value;
    }

    public static void initAnnotations(Frame frame) {
        Annotations[] annotations = new Annotations[ frame.getFrameDescriptor().getNumberOfSlots() - VALUES_START ];
        frame.setObject(ANNOTATION_SLOT, annotations);
    }

    // --------------------------------------------------------------------------
    //
    // fields and arrays

    public static void setFieldAnnotation(StaticObject obj, Field f, Annotations a) {
        if (f.isStatic()) {
            obj = f.getDeclaringKlass().getStatics();
        }

        if (!obj.hasAnnotations() && a == null) {
            return;
        }

        Annotations[] annotations = obj.getAnnotations();
        if (annotations == null) {
           annotations = new Annotations[1 + (f.isStatic()
                            ? f.getDeclaringKlass().getStaticFieldTable().length
                            : ((ObjectKlass) obj.getKlass()).getFieldTable().length)];
           obj.setAnnotations(annotations);
        }

        annotations[f.getSlot()] = a;
    }

    public static Annotations getFieldAnnotation(StaticObject obj, Field f) {
        if (f.isStatic()) {
            obj = f.getDeclaringKlass().getStatics();
        }

        if (!obj.hasAnnotations()) {
            return null;
        }
        Annotations[] annotations = obj.getAnnotations();
        Annotations a = annotations[f.getSlot()];
        return a;
    }

    public static Annotations getArrayAnnotations(StaticObject array, int index) {
        if (!array.hasAnnotations()) {
            return null;
        }
        Annotations[] annotations = array.getAnnotations();
        Annotations a = annotations[index];
        return a;
    }

    public static void setArrayAnnotations(StaticObject array, int index, Annotations a, EspressoLanguage lang) {
        if (a == null) return;

        if (!array.hasAnnotations()) {
            Annotations[] annotations = new Annotations[array.length(lang) + 1];
            array.setAnnotations(annotations);
        }
        Annotations[] annotations = array.getAnnotations();
        annotations[index] = a;
    }

    // --------------------------------------------------------------------------
    //
    // method arguments

    @CompilerDirectives.TruffleBoundary
    public static Object[] deAnnotateArguments(Object[] args, Method method) {
        for (int i=0; i<args.length; i++) {
            String methodName =method.getNameAsString();
            if (args[i] instanceof AnnotatedValue) {
                SPouT.log("Warning: removing annotations before calling substituted/native method " +
                        method.getDeclaringKlass().getNameAsString() + "." + method.getNameAsString() + ": " + args[i]);
                if (methodName.contains("doubleToRawLongBits") || methodName.contains("longBitsToDouble")
                        || methodName.contains("intBitsToFloat") || methodName.contains("floatToRawIntBits")){
                    SPouT.losePrecision("Stripping symbolic values from Float or Double methods that do not support them.", getMeta());
                }

            }
            args[i] = AnnotatedValue.value(args[i]);
        }
        return args;
    }

    // --------------------------------------------------------------------------
    //
    // stack operations

    public static void copy(Frame frame, int from, int to) {
        Annotations[] stack = getAnnotations(frame);
        if (stack[from - VALUES_START] != null) {
            stack[to - VALUES_START] = new Annotations(stack[from - VALUES_START]);
        }
    }

    public static void swap(Frame frame, int from, int to) {
        Annotations[] stack = getAnnotations(frame);
        Annotations tmp = stack[to - VALUES_START];
        stack[to - VALUES_START] = stack[from - VALUES_START];
        stack[from - VALUES_START] = tmp;
    }
}
