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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class AnnotatedValue {

    AnnotatedValue(Object c, Expression s) {
        this.concrete = c;
        this.symbolic = s;
    }

    static AnnotatedValue fromConstant(PrimitiveTypes type, Object value) {
        switch (type) {
            case INT: return new AnnotatedValue(value, Constant.fromConcreteValue( (int) value));
            case LONG: return new AnnotatedValue(value, Constant.fromConcreteValue( (long) value));
            case FLOAT: return new AnnotatedValue(value, Constant.fromConcreteValue( (float) value));
            case DOUBLE: return new AnnotatedValue(value, Constant.fromConcreteValue( (double) value));
            default:
                throw EspressoError.shouldNotReachHere("unsupported constant type");
        }
    }

    private Object concrete;

    private Expression symbolic = null;

    public long asLong() {
        return (long) concrete;
    }

    public Object asType(byte b) {
        switch (b) {
            case 'Z' : return asRaw(); //FIXME: MAGIC???
            case 'B' : return (byte) asInt();
            case 'S' : return (short) asInt();
            case 'C' : return (char) asInt();
            case 'I' : return asInt();
            case 'F' : return asFloat();
            case 'J' : return asLong();
            case 'D' : return asDouble();
            default      :
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("unexpected kind");
        }
    }

    public int asInt() {
        if (concrete instanceof Boolean) {
            // FIXME: should never happen!
            return ((Boolean)concrete) ? 1 : 0;
        }
        if (concrete instanceof Byte) {
            return ((Byte)concrete);
        }
        if (concrete instanceof Character) {
            return ((Character)concrete);
        }
        if (concrete instanceof Short) {
            return ((Short)concrete);
        }
        if (concrete instanceof  Integer) {
            return ((Integer) concrete);
        }
        return error();
    }

    @CompilerDirectives.TruffleBoundary
    public int error() {
        throw EspressoError.shouldNotReachHere("unexpected int type: " +
                concrete.getClass().getSimpleName());
    }
    
    public byte asByte() {
        if (concrete instanceof  Integer){
            return ((Integer) concrete).byteValue();
        }
        return (byte) concrete;
    }

    public char asChar() {
        return (char) concrete;
    }

    public short asShort() {
        return (short) concrete;
    }

    public float asFloat() {
        return (float) concrete;
    }

    public double asDouble() {
        return (double) concrete;
    }

    public StaticObject asRef() {
        return (StaticObject) concrete;
    }

    public boolean isSymbolic() {
        return symbolic == null;
    }

    public Expression symbolic() {
        return symbolic;
    }

    public Object asRaw() {
        return concrete;
    }

    public boolean asBoolean() {
        return (boolean) concrete;
    }
}
