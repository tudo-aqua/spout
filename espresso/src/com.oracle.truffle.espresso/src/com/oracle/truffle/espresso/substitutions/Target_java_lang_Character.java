package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import tools.aqua.spout.AnnotatedValue;
import tools.aqua.spout.SPouT;
import tools.aqua.spout.SPouTNumeric;

@EspressoSubstitutions
public final class Target_java_lang_Character {
    @Substitution(methodName = "toUpperCase", passAnnotations = true)
    public static @JavaType(internalName = "C") Object toUpperCase_char(
            @JavaType(internalName = "C") Object c,
            @Inject Meta meta){
        return SPouT.characterToUpperCase(c, meta);
    }

    @Substitution(methodName = "toLowerCase", passAnnotations = true)
    public static @JavaType(internalName = "C") Object toLowerCase_char(
            @JavaType(internalName = "C") Object c,
            @Inject Meta meta){
        return SPouT.characterToLowerCase(c, meta);
    }

    @Substitution(methodName = "isDefined", passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isDefined_char(@JavaType(internalName = "C") Object c,
                                                              @Inject Meta meta){
        return SPouT.isCharDefined(c, meta);
    }

    @Substitution(passAnnotations = true, hasReceiver = true)
    public static @JavaType(internalName = "Z") Object equals(@JavaType(Character.class) StaticObject self, @JavaType(Object.class) Object obj, @Inject Meta meta) {
        if(obj instanceof StaticObject){
            StaticObject other = (StaticObject) obj;
            return SPouT.characterEquals(self, other, meta);
        }else{
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
                    "I don't now what to do, if not both objects in character equal are static objects, yet");
        }
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isAlphabetic(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsAlphabetic(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isJavaIdentifierStart(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsJavaIdentifierStart(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isJavaIdentifierPart(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsJavaIdentifierPart(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isUnicodeIdentifierStart(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsUnicodeIdentifierStart(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isUnicodeIdentifierPart(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsUnicodeIdentifierPart(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isIdentifierIgnorable(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsIdentiferIgnorable(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isLetterOrDigit(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsLetterOrDigit(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isLetter(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsLetter(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isLowerCase(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsLowerCase(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isMirrored(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsMirrored(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isSpaceChar(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsSpaceChar(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isSpace(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsSpace(codePoint, meta);
    }

    @Substitution(methodName = "isTitleCase", passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isTitleCase(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsTitleCase(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isUpperCase(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsUpperCase(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isWhitespace(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterIsWhitespace(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "B") Object getDirectionality(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterGetDirectionality(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "I") Object digit(@JavaType(internalName = "I") Object codePoint,
                                                             @JavaType(internalName = "I") Object radix,  @Inject Meta meta){
        return SPouT.characterDigit(codePoint, radix, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "I") Object getNumericValue(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterGetNumericValue(codePoint, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "I") Object getType(@JavaType(internalName = "I") Object codePoint, @Inject Meta meta){
        return SPouT.characterGetType(codePoint, meta);
    }

    // TODO: cause boot problems

    /*
    @Substitution(passAnnotations = true)
    public static @JavaType(Character.class) StaticObject valueOf(@JavaType(internalName = "C") Object cIn, @Inject Meta meta){
        return SPouTNumeric.charValueOfChar(cIn, meta);
    }


    @Substitution(hasReceiver = true, passAnnotations = true)
    public static @JavaType(internalName = "C") Object charValue(@JavaType(Character.class) StaticObject cIn, @Inject Meta meta) {
        return SPouTNumeric.charCharValue(cIn, meta);
    }
    */
}
