package tools.aqua.smt;

public class FieldName implements Expression {

    private final String name;
    private final Variable objectId;

    public FieldName(String name, Variable objectId) {
        this.name = name;
        this.objectId = objectId;
    }

    @Override
    public String toString() {
        return objectId.toString() + "." + name;
    }

}
