package tools.aqua.concolic;

import tools.aqua.spout.TraceElement;

public class ObjectIdentityDeclaration extends TraceElement {
    private final int id;

    public ObjectIdentityDeclaration(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "[DECLARE] (declare-fun " +
                "__object_id_"+id+" () String)" ;
    }
}
