/*
 * Copyright (c) 2023 Automated Quality Assurance Group, TU Dortmund University.
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
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * BFS on heap reachable from origin. Executes action on this region.
 */
public class HeapWalker {

    private final StaticObject origin;

    private final HeapWalkAction action;

    private final int maxDepth;

    public HeapWalker(StaticObject origin, HeapWalkAction action, int maxDepth) {
        this.origin = origin;
        this.action = action;
        this.maxDepth = maxDepth;
    }

    public HeapWalker(StaticObject origin, HeapWalkAction action) {
        this(origin, action, -1);
    }

    @CompilerDirectives.TruffleBoundary
    public void walk(EspressoLanguage lang) {
        int depth = 1;
        Set<StaticObject> visited = new HashSet<>();
        LinkedList<StaticObject> queue = new LinkedList<>();
        LinkedList<StaticObject> nextQueue = new LinkedList<>();
        if (origin.isArray()) {
            processArray(origin, lang, nextQueue, visited);
            return;
        }
        queue.offer(origin);
        visited.add(origin);
        while (!queue.isEmpty()) {
            StaticObject current = queue.poll();
            action.applyToObject(current);
            //SPouT.log("Analyzing " + current.getKlass().getNameAsString() + " . " + current.toString());
            ObjectKlass oClass = (ObjectKlass) current.getKlass();
            for (Field f : oClass.getFieldTable()) {
                if (!analyze(f)) continue;
                if (f.getKind().isPrimitive()) {
                    //SPouT.log("Primitive");
                    action.applyToPrimitiveField(current, f);
                } else {
                    StaticObject obj = (StaticObject) f.get(current);
                    if (StaticObject.isNull(obj) || visited.contains(obj)) continue;
                    if (f.getType() == Symbol.Type.java_lang_String) {
                        //SPouT.log("String");
                        action.applyToString(obj);
                    } else if (obj.isArray()) {
                        //SPouT.log("Array");
                        visited.add(obj);
                        processArray(obj, lang, nextQueue, visited);

                    } else {
                        //SPouT.log("Adding " + obj.getKlass().getNameAsString() + " to queue");
                        visited.add(obj);
                        nextQueue.offer(obj);
                    }
                }
            }

            if (queue.isEmpty() && (maxDepth < 0 || depth < maxDepth)) {
                SPouT.log("Increase Depth.");
                depth ++;
                queue = nextQueue;
                nextQueue = new LinkedList<>();
            }
        }
    }

    private void processArray(StaticObject arr, EspressoLanguage lang,
                      LinkedList<StaticObject> nextQueue, Set<StaticObject> visited) {
        if (StaticObject.isNull(arr)) return;
        assert arr.isArray();
        action.applyToArray(arr);
        Klass eClass = arr.getKlass().getElementalType();
        Object[] data = arr.unwrap(lang);
        if (eClass.isPrimitive()) {
            for (int i=0; i<data.length; i++) {
                action.applyToPrimitiveArrayElement(arr, i);
            }
        }
        else if (eClass.getType() == Symbol.Type.java_lang_String) {
            for (Object e : data) {
                StaticObject str = (StaticObject) e;
                action.applyToString(str);
            }
        } else if (eClass.isArray()) {
            for (Object e : data) {
                StaticObject arr2 = (StaticObject) e;
                if (visited.contains(arr2)) continue;
                visited.add(arr2);
                processArray( arr2, lang, nextQueue, visited);
            }
        } else {
            for (Object e : data) {
                StaticObject obj2 = (StaticObject) e;
                if (visited.contains(obj2)) continue;
                visited.add(obj2);
                nextQueue.offer(obj2);
            }
        }
    }

    private boolean analyze(Field f) {
        return true;
    }

}
