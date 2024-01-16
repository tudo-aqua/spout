package com.oracle.truffle.espresso.nodes.concolic;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeVirtual;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeVirtualNodeGen;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

public abstract class ConcolicInvokeVirtualNode extends QuickNode {

    final Meta meta;
    final Method.MethodVersion method;
    final int resultAt;
    final boolean returnsPrimitiveType;
    @Child InvokeVirtual.WithoutNullCheck invokeVirtual;

    abstract Object concolicAnalysis(Object concreteResult, Object[] args);

    public ConcolicInvokeVirtualNode(Method method, int top, int curBCI, Meta meta) {
        super(top, curBCI);
        assert !method.isStatic();
        this.method = method.getMethodVersion();
        this.resultAt = top - Signatures.slotsForParameters(method.getParsedSignature()) - 1; // -receiver
        this.returnsPrimitiveType = Types.isPrimitive(Signatures.returnType(method.getParsedSignature()));
        this.invokeVirtual = InvokeVirtualNodeGen.WithoutNullCheckNodeGen.create(method);
        this.meta = meta;
    }

    @Override
    public int execute(VirtualFrame frame) {
        Object[] args = BytecodeNode.popArguments(frame, top, true, method.getMethod().getParsedSignature());
        nullCheck((StaticObject) args[0]);
        Object result = invokeVirtual.execute(args);
        result = concolicAnalysis(result, args);
        if (!returnsPrimitiveType) {
            getBytecodeNode().checkNoForeignObjectAssumption((StaticObject) result);
        }
        return (getResultAt() - top) + BytecodeNode.putKind(frame, getResultAt(), result, method.getMethod().getReturnKind());
    }

    @Override
    public boolean removedByRedefintion() {
        if (method.getRedefineAssumption().isValid()) {
            return false;
        } else {
            return method.getMethod().isRemovedByRedefition();
        }
    }

    private int getResultAt() {
        return resultAt;
    }


    public static final class StringEquals extends ConcolicInvokeVirtualNode {

        public StringEquals(int top, int curBCI, Meta meta) {
            super(meta.java_lang_String_equals, top, curBCI, meta);
        }

        @Override
        Object concolicAnalysis(Object concreteResult, Object[] args) {
            SPouT.log("concolic virtual node");
            return SPouT.stringEquals( (StaticObject) args[0], (StaticObject) args[1], (boolean) concreteResult, meta);
        }
    }


}
