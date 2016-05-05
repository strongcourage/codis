package sg.edu.nus.comp.codis.ast;

/**
 * Created by Sergey Mechtaev on 2/5/2016.
 *
 * Used by bounded synthesis. Define physical equality. Test-instantiated
 */
public class BranchOutput extends Variable {

    private Type type;

    public Type getType() {
        return type;
    }

    public BranchOutput(Type type) {
        this.type = type;
        objectCounter = classCounter;
        classCounter++;
    }

    @Override
    public void accept(BottomUpVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void accept(TopDownVisitor visitor) {
        visitor.visit(this);
    }

    private static int classCounter = 0;
    private int objectCounter;

    @Override
    public String toString() {
        return "Branch" + objectCounter;
    }

}
