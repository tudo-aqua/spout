package tools.aqua.concolic;

import tools.aqua.spout.TraceElement;

public class ConstructorDeclaration extends TraceElement {
    private final int id;

    public ConstructorDeclaration(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "[DECLARE] (declare-fun " +
                "__object_constructor_"+id+" () String)" ;
    }
}
