package tools.aqua.concolic;

import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.smt.Expression;
import tools.aqua.spout.AnnotatedVM;
import tools.aqua.spout.AnnotatedValue;
import tools.aqua.spout.Annotations;
import tools.aqua.spout.Config;
import tools.aqua.spout.HeapWalkAction;
import tools.aqua.spout.SPouT;

public class ConcolicAnnotationAction implements HeapWalkAction {

    private final ConcolicAnalysis concolicAnalysis;

    private final Config config;

    public ConcolicAnnotationAction(Config config) {
        this.config = config;
        this.concolicAnalysis = config.getConcolicAnalysis();
    }

    @Override
    public void applyToPrimitiveField(StaticObject obj, Field f) {
        Annotations a = AnnotatedVM.getFieldAnnotation(obj, f);
        if (a == null) {
            a = Annotations.create();
        }
        Expression e = Annotations.annotation(a, config.getConcolicIdx());
        if (e != null) {
            SPouT.log("skipping: " + f.getKind().toString() + "-field " +
                    f.getNameAsString() + " of " +
                    obj.getKlass().getNameAsString() + " [" +
                    obj.toString() + "] already concolic");
        }

        SPouT.log("New concolic value: " + f.getKind().toString() + "-field " +
                f.getNameAsString() + " of " +
                obj.getKlass().getNameAsString() + " [" +
                obj.toString() + "]");

        AnnotatedValue av = null;
        switch (f.getKind()) {
            case Boolean:
                av = (AnnotatedValue) concolicAnalysis.nextSymbolicBoolean();
                break;
            case Int:
                av = (AnnotatedValue) concolicAnalysis.nextSymbolicInt();
                break;
            case Byte:
                av = (AnnotatedValue) concolicAnalysis.nextSymbolicByte();
                break;
            case Char:
                av = (AnnotatedValue) concolicAnalysis.nextSymbolicChar();
                break;
            case Long:
                av = (AnnotatedValue) concolicAnalysis.nextSymbolicLong();
                break;
            case Float:
                av = (AnnotatedValue) concolicAnalysis.nextSymbolicFloat();
                break;
            case Short:
                av = (AnnotatedValue) concolicAnalysis.nextSymbolicShort();
                break;
            case Double:
                av = (AnnotatedValue) concolicAnalysis.nextSymbolicDouble();
                break;
        }
        f.set(obj, av.getValue());
        a.set(config.getConcolicIdx(),
                Annotations.annotation(av, config.getConcolicIdx()));
        AnnotatedVM.setFieldAnnotation(obj, f, a);
    }

    @Override
    public void applyToPrimitiveArrayElement(StaticObject obj, int i) {
        SPouT.log("currently no concolic values in arrays on heap");
    }

    @Override
    public void applyToString(StaticObject obj) {
        SPouT.log("currently no concolic values in strings on heap");
    }
}
