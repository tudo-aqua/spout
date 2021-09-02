package tools.aqua.concolic;

public class SymbolDeclaration extends TraceElement {

    private final Variable variable;

    public SymbolDeclaration(Variable var) {
        this.variable = var;
    }

    @Override
    public String toString() {
        return "(define-fun " +
                variable + " () " +
                variable.getType() + ")";
    }
}
