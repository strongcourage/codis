package sg.edu.nus.comp.codis.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class ProgramVariable extends Variable {
    private String name;
    private Type type;

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public ProgramVariable(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProgramVariable))
            return false;
        if (obj == this)
            return true;

        ProgramVariable rhs = (ProgramVariable) obj;
        return new EqualsBuilder().
                append(name, rhs.name).
                append(type, rhs.type).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(name).
                append(type).
                toHashCode();
    }

    @Override
    public String toString() {
        return name;
    }

    public void accept(BottomUpVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void accept(TopDownVisitor visitor) {
        visitor.visit(this);
    }

}