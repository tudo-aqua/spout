package tools.aqua.concolic;

public abstract class TraceElement {

    private TraceElement next;

    public void setNext(TraceElement next) {
        this.next = next;
    }

    public TraceElement getNext() {
        return next;
    }
}
