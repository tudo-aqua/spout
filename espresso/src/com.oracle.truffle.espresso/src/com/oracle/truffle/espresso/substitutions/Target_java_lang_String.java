/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications to original file are Copyright (c) 2021 Automated Quality
 * Assurance Group, TU Dortmund University. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import java.util.Arrays;
import tools.aqua.concolic.AnnotatedValue;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_java_lang_String {

    @Substitution(hasReceiver = true, methodName = "<init>")
    public static void init(@Host(String.class) StaticObject self, @Host(String.class) StaticObject other, @InjectMeta Meta meta){
        Field[] fields = other.getKlass().getDeclaredFields();
        Field value = null, coder = null, hash = null;
        for(Field f: fields){
            if(f.getNameAsString().equals("value")){
                value = f;
            }else if(f.getNameAsString().equals("coder")){
                coder = f;
            }else if(f.getNameAsString().equals("hash")){
                hash = f;
            }
        }
        for(Field f: self.getKlass().getDeclaredFields()){
            if(f.getNameAsString().equals("value")){
                f.setObject(self, value.getObject(other).copy());
            }else if(f.getNameAsString().equals("coder")){
                f.setByte(self, coder.getByte(other));
            }else if(f.getNameAsString().equals("hash")){
                f.setInt(self, hash.getInt(other));
            }
        }
        if(other.isConcolic()){
            Concolic.stringConstructor(self, other, meta);
        }
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @Host(typeName = "Z") Object equals(
        @Host(String.class) StaticObject self,
        @Host(Object.class) StaticObject other,
        @InjectMeta Meta meta) {

        // TODO: not sure if this check is necessary?
        // if (StaticObject.isNull(self)) {
        //     meta.throwNullPointerException();
        // }
        // TODO: afaik, nothing we can do here symbolically?
        if (StaticObject.isNull(other) || !self.getKlass().equals(other.getKlass())) {
            return false;
        }

        return Concolic.stringEquals(self, other, meta);
    }

    @Substitution(methodName = "valueOf")
    @CompilerDirectives.TruffleBoundary
    public static @Host(String.class) StaticObject valueOf_bool(@Host(typeName = "Z") Object v, @InjectMeta Meta meta) {
        if (v instanceof AnnotatedValue) {
            Concolic.stopRecording("concolic type conversion to string not supported, yet.", meta);
        }
        String ret = "" + (boolean) v;
        return meta.toGuestString(ret);
    }

    @Substitution(methodName = "valueOf")
    @CompilerDirectives.TruffleBoundary
    public static @Host(String.class) StaticObject valueOf_byte(@Host(typeName = "B") Object v, @InjectMeta Meta meta) {
        if (v instanceof AnnotatedValue) {
            Concolic.stopRecording("concolic type conversion to string not supported, yet.", meta);
        }
        String ret = "" + (byte) v;
        return meta.toGuestString(ret);
    }

    @Substitution(methodName = "valueOf")
    @CompilerDirectives.TruffleBoundary
    public static @Host(String.class) StaticObject valueOf_char(@Host(typeName = "C") Object v, @InjectMeta Meta meta) {
        if (v instanceof AnnotatedValue) {
            Concolic.stopRecording("concolic type char conversion to string not supported, yet.", meta);
        }
        String ret = "" + (char) v;
        return meta.toGuestString(ret);
    }

  @Substitution(methodName = "valueOf")
  @CompilerDirectives.TruffleBoundary
  public static @Host(String.class) StaticObject valueOf_char_array(
      @Host(typeName = "[C") StaticObject v, @InjectMeta Meta meta) {
    if (v.isConcolic()) {
      Concolic.stopRecording("concolic type char array conversion to string not supported, yet.", meta);
    }
    char[] value = v.unwrap();
    return meta.toGuestString(new String(value));
  }

  @Substitution(methodName = "valueOf")
  @CompilerDirectives.TruffleBoundary
  public static @Host(String.class) StaticObject valueOf_char_array(
      @Host(typeName = "[C") StaticObject v,
      @Host(typeName = "I") Object offset,
      @Host(typeName = "I") Object count,
      @InjectMeta Meta meta) {
    if (v.isConcolic() || offset instanceof AnnotatedValue || count instanceof AnnotatedValue) {
      Concolic.stopRecording("concolic type char array conversion to string not supported, yet.", meta);
    }
    char[] value = Arrays.copyOfRange((char[]) v.unwrap(), (int) offset, (int) count);
    return meta.toGuestString(new String(value));
  }

    @Substitution(methodName = "valueOf")
    @CompilerDirectives.TruffleBoundary
    public static @Host(String.class) StaticObject valueOf_short(@Host(typeName = "S") Object v, @InjectMeta Meta meta) {
        if (v instanceof AnnotatedValue) {
            Concolic.stopRecording("concolic type conversion to string not supported, yet.", meta);
        }
        String ret = "" + (short) v;
        return meta.toGuestString(ret);
    }

    @Substitution(methodName = "valueOf")
    @CompilerDirectives.TruffleBoundary
    public static @Host(String.class) StaticObject valueOf_int(@Host(typeName = "I") Object v, @InjectMeta Meta meta) {
        if (v instanceof AnnotatedValue) {
            Concolic.stopRecording("concolic type conversion to string not supported, yet.", meta);
        }
        String ret = "" + (int) v;
        return meta.toGuestString(ret);
    }

    @Substitution(methodName = "valueOf")
    @CompilerDirectives.TruffleBoundary
    public static @Host(String.class) StaticObject valueOf_long(@Host(typeName = "J") Object v, @InjectMeta Meta meta) {
        if (v instanceof AnnotatedValue) {
            Concolic.stopRecording("concolic type conversion to string not supported, yet.", meta);
        }
        String ret = "" + (long) v;
        return meta.toGuestString(ret);
    }

    @Substitution(methodName = "valueOf")
    @CompilerDirectives.TruffleBoundary
    public static @Host(String.class) StaticObject valueOf_float(@Host(typeName = "F") Object v, @InjectMeta Meta meta) {
        if (v instanceof AnnotatedValue) {
            Concolic.stopRecording("concolic type conversion to string not supported, yet.", meta);
        }
        String ret = "" + (float) v;
        return meta.toGuestString(ret);
    }

    @Substitution(methodName = "valueOf")
    @CompilerDirectives.TruffleBoundary
    public static @Host(String.class) StaticObject valueOf_double(@Host(typeName = "D") Object v, @InjectMeta Meta meta) {
        if (v instanceof AnnotatedValue) {
            Concolic.stopRecording("concolic type conversion to string not supported, yet.", meta);
        }
        String ret = "" + (double) v;
        return meta.toGuestString(ret);
    }

    @Substitution(hasReceiver = true)
    public static @Host(typeName = "C") Object charAt(
        @Host(String.class) StaticObject self,
        @Host(typeName = "I") Object index,
        @InjectMeta Meta meta) {
        return Concolic.stringCharAt(self, index, meta);
    }

    @Substitution(hasReceiver = true)
    public static @Host(typeName = "I") Object length(
        @Host(String.class) StaticObject self,
        @InjectMeta Meta meta) {
        return Concolic.stringLength(self, meta);
    }

    @Substitution(hasReceiver = true)
    public static @Host(typeName = "Z") Object contains(@Host(String.class) StaticObject self, @Host(CharSequence.class) StaticObject s, @InjectMeta Meta meta){
        if(StaticObject.isNull(self) || StaticObject.isNull(s)){
            return false;
        }
        return Concolic.stringContains(self, s, meta);
    }

    @Substitution(hasReceiver = true)
    public static @Host(String.class) Object concat(@Host(String.class) StaticObject self, @Host(String.class) StaticObject s, @InjectMeta Meta meta){
        return Concolic.stringConcat(self, s, meta);
    }

    @Substitution(hasReceiver = true)
    public static @Host(String.class) Object toString(@Host(String.class) StaticObject self, @InjectMeta Meta meta){
        return Concolic.stringToString(self, meta);
    }

    @Substitution(hasReceiver = true, methodName = "toLowerCase")
    @TruffleBoundary
    public static @Host(String.class) Object toLowerCase(@Host(String.class) StaticObject self, @InjectMeta Meta meta){
        StaticObject res = (StaticObject) Concolic.stringToLowercase(self, meta);
        return res;
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @Host(String[].class) Object split(@Host(String.class) StaticObject self, @Host(String.class) StaticObject regex, @InjectMeta Meta meta){
        if(self.isConcolic() || regex.isConcolic()){
            Concolic.stopRecording("Cannot split symbolic strings yet", meta);
        }
        String s = meta.toHostString(self);
        String r = meta.toHostString(regex);
        String[] res = s.split(r);
        StaticObject[] resSO = new StaticObject[res.length];
        for(int i = 0; i < res.length; i++){
            resSO[i] = meta.toGuestString(res[i]);
        }
        return StaticObject.createArray(self.getKlass().getArrayClass(), resSO);
    }

    @Substitution(hasReceiver = true, methodName = "regionMatches")
    public static @Host(typeName = "Z") Object regionMatches_ignoreCase(@Host(String.class) StaticObject self, @Host(typeName = "Z") Object ignoreCase, @Host(typeName = "I") Object toffset, @Host(String.class) StaticObject other,
        @Host(typeName = "I") Object ooffset, @Host(typeName = "I") Object len,@InjectMeta Meta meta){
        boolean ignore = false;
        if(ignoreCase instanceof AnnotatedValue){
            Concolic.stopRecording("Cannot deal with symbolic ignore case for regionMatches yet", meta);
        }else{
            ignore =(boolean) ignoreCase;
        }
        int ctoffset= -1, cooffset=-1, clen=-1;
        if(toffset instanceof AnnotatedValue){
            Concolic.stopRecording("Cannot deal with symbolic toffset for regionMatches yet", meta);
        }
        else{
            ctoffset = (int) toffset;
        }
        if(ooffset instanceof AnnotatedValue){
            Concolic.stopRecording("Cannot deal with symbolic ooffset for regionMatches yet", meta);
        }
        else{
            cooffset = (int) ooffset;
        }
        if(len instanceof AnnotatedValue){
            Concolic.stopRecording("Cannot deal with symbolic len for regionMatches yet", meta);
        }
        else{
            clen = (int) len;
        }
        if(self.isConcolic() || other.isConcolic()){
            return  Concolic.regionMatches(self, other, ignore, ctoffset, cooffset, clen, meta);
        }else{
            return meta.toHostString(self).regionMatches(ignore, ctoffset, meta.toHostString(other), cooffset, clen);
        }
    }

}
