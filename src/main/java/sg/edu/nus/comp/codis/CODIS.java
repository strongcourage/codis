package sg.edu.nus.comp.codis;

import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sg.edu.nus.comp.codis.ast.Component;
import sg.edu.nus.comp.codis.ast.Node;

import java.util.*;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class CODIS implements Synthesis {

    private Logger logger = LoggerFactory.getLogger(CEGIS.class);

    // list of (program, last fixed, change)
    private ArrayList<Triple<Node, TestCase, Map<Integer, Node>>> path;

    @Override
    public Optional<Node> synthesize(ArrayList<TestCase> testSuite, Map<Node, Integer> componentMultiset) {
        throw new UnsupportedOperationException();
    }


}
