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

import java.util.Arrays;

public class Annotations {

    private static int annotationLength = 0;

    private final Object[] annotations;

    Annotations() {
        this(new Object[annotationLength]);
    }

    Annotations(Annotations other) {
        this.annotations = new Object[other.annotations.length];
        System.arraycopy(other.annotations, 0, this.annotations, 0, this.annotations.length);
    }

    Annotations(Object[] annotations) {
        assert annotations.length == annotationLength;
        this.annotations = annotations;
    }

    public Object[] getAnnotations() {
        return annotations;
    }

    @SuppressWarnings("unchecked")
    public static <T> T annotation(Annotations a, int i) {
        return a != null ? (T) a.annotations[i] : null;
    }

    public <T> void set(int i, T annotation) {
        annotations[i] = annotation;
    }

    static void configure(int length) {
        annotationLength = length;
    }

    @Override
    public String toString() {
        return "Annotations{" +
                "annotations=" + Arrays.toString(annotations) +
                '}';
    }
}
