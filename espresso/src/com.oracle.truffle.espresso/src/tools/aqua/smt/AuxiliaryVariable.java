package tools.aqua.smt;

public class AuxiliaryVariable extends Atom {

    private final String name;

    public AuxiliaryVariable(String name, Types type) {
        super(type);
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

}
