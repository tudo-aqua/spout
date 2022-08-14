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

package tools.aqua.taint;

import java.util.ArrayList;
import java.util.Collection;

public class ColorUtil {

    private static long[] resize(long[] arr, int size) {
        long[] newArr = new long[size];
        System.arraycopy(arr, 0, newArr, 0, arr.length);
        return newArr;
    }

    public static long[] setColor(long[] colors, int color) {
        int segment = color / 64;
        int offset = color % 64;

        long[] ret = null;
        if (colors != null) {
            ret = resize(colors, Math.max(segment +1, colors.length));
        }
        else {
            ret = new long[segment+1];
        }

        ret[segment] |= (1L << offset);
        return ret;
    }

    public static long[] removeColor(long[] colors, int color) {
        if (colors == null) {
            return null;
        }

        int segment = color / 64;
        int offset = color % 64;

        long[] ret = resize(colors, colors.length);

        if (segment >= colors.length) {
            return ret;
        }

        ret[segment] &= ~(1L << offset);
        return ret;
    }

    public static Taint joinColors(Taint... colorings) {
        int size = -1;
        for (int i=0; i< colorings.length; i++) {
            if (colorings[i] != null) {
                size = (size < colorings[i].getColors().length) ? colorings[i].getColors().length : size;
            }
        }

        if (size < 1) {
            return null;
        }

        long[] colors = new long[size];
        for (int j=0; j< colorings.length; j++) {
            if (colorings[j] != null) {
                for (int i = 0; i < colorings[j].getColors().length; i++) {
                    colors[i] |= colorings[j].getColors()[i];
                }
            }
        }
        return new Taint(colors);
    }

    public static long[] joinColors(long[] ... colorings) {
        int size = -1;
        for (int i=0; i< colorings.length; i++) {
            if (colorings[i] != null) {
                size = (size < colorings[i].length) ? colorings[i].length : size;
            }
        }
        if (size < 0) {
            return null;
        }
        long[] colors = new long[size];
        for (int j=0; j< size; j++) {
            for (int i = 0; i < colorings.length; i++) {
                if (colorings[i] != null && colorings[i].length > j) {
                    colors[j] |= colorings[i][j];
                }
            }
        }
        return colors;
    }

    public static boolean hasColor(long[] colors, int color) {
        int segment = color / 64;
        int offset = color % 64;

        if (segment >= colors.length) {
            return false;
        }

        return (colors[segment] & (1L << offset)) != 0L;
    }

    public static long[] fromColor(int color) {
        return setColor(null, color);
    }

    public static Collection<Integer> colorsIn(long[] taint) {
        ArrayList<Integer> colors = new ArrayList<>();
        if (taint == null) {
            return colors;
        }
        for (int i=0; i< taint.length*64; i++) {
            if (hasColor(taint, i)) {
                colors.add(i);
            }
        }
        return colors;
    }
}
