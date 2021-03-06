package sg.edu.nus.comp.codis;

import sg.edu.nus.comp.codis.ast.*;
import sg.edu.nus.comp.codis.ast.theory.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class Cardinality {

    public static List<Node> pairwise(List<? extends Variable> bits) {
        ArrayList<Node> clauses = new ArrayList<>();
        for (Variable bit1 : bits) {
            for (Variable bit2 : bits) {
                if (!bit1.equals(bit2)) {
                    clauses.add(new Or(new Not(bit1), new Not(bit2)));
                }
            }
        }
        return clauses;
    }

    public static Node circuit(List<? extends Variable> bits, int max) {
        throw new UnsupportedOperationException();
    }

    public static Node sortingNetwork(List<? extends Variable> bits, int max) {
        throw new UnsupportedOperationException();
    }
}
