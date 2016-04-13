package sg.edu.nus.comp.codis.ast;

import sg.edu.nus.comp.codis.ast.theory.*;

import java.util.*;
import java.util.function.Function;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class Traverse {

    public static Node transform(Node node, Function<Node, Node> function) {
        TransformationVisitor visitor = new TransformationVisitor(function);
        node.accept(visitor);
        return visitor.getTransformedNode();
    }

    public static Node substitute(Node node,
                                  Map<? extends Variable, ? extends Node> mapping) {
        return transform(node, n -> {
            if (n instanceof Variable && mapping.containsKey(n)) {
                return mapping.get(n);
            } else {
                return n;
            }
        });
    }

    public static <T> Set<T> collectByType(Node node, Class<T> type) {
        CollectVisitor visitor = new CollectVisitor(type);
        node.accept(visitor);
        return visitor.getCollected();
    }

    private static class CollectVisitor<T> implements BottomUpVisitor {

        public Set<T> getCollected() {
            return collected;
        }

        private Set<T> collected;
        private Class<T> type;

        public CollectVisitor(Class<T> type) {
            this.type = type;
            collected = new HashSet<>();
        }

        private void addIfMatches(Node node) {
            if (type.isInstance(node)) {
                collected.add((T) node);
            }
        }

        @Override
        public void visit(ProgramVariable programVariable) {
            addIfMatches(programVariable);
        }

        @Override
        public void visit(Location location) {
            addIfMatches(location);
        }

        @Override
        public void visit(UIFApplication UIFApplication) {
            addIfMatches(UIFApplication);
        }

        @Override
        public void visit(Equal equal) {
            addIfMatches(equal);
        }

        @Override
        public void visit(Add add) {
            addIfMatches(add);
        }

        @Override
        public void visit(Sub sub) {
            addIfMatches(sub);
        }

        @Override
        public void visit(Mult mult) {
            addIfMatches(mult);
        }

        @Override
        public void visit(Div div) {
            addIfMatches(div);
        }

        @Override
        public void visit(And and) {
            addIfMatches(and);
        }

        @Override
        public void visit(Or or) {
            addIfMatches(or);
        }

        @Override
        public void visit(Iff iff) {
            addIfMatches(iff);
        }

        @Override
        public void visit(Impl impl) {
            addIfMatches(impl);
        }

        @Override
        public void visit(Greater greater) {
            addIfMatches(greater);
        }

        @Override
        public void visit(Less less) {
            addIfMatches(less);
        }

        @Override
        public void visit(GreaterOrEqual greaterOrEqual) {
            addIfMatches(greaterOrEqual);
        }

        @Override
        public void visit(LessOrEqual lessOrEqual) {
            addIfMatches(lessOrEqual);
        }

        @Override
        public void visit(Minus minus) {
            addIfMatches(minus);
        }

        @Override
        public void visit(Not not) {
            addIfMatches(not);
        }

        @Override
        public void visit(IntConst intConst) {
            addIfMatches(intConst);
        }

        @Override
        public void visit(BoolConst boolConst) {
            addIfMatches(boolConst);
        }

        @Override
        public void visit(ComponentInput componentInput) {
            addIfMatches(componentInput);
        }

        @Override
        public void visit(ComponentOutput componentOutput) {
            addIfMatches(componentOutput);
        }

        @Override
        public void visit(TestInstance testInstance) {
            addIfMatches(testInstance);
        }

        @Override
        public void visit(Parameter parameter) {
            addIfMatches(parameter);
        }

        @Override
        public void visit(Hole hole) {
            addIfMatches(hole);
        }
    }

    private static class TransformationVisitor implements BottomUpVisitor {

        private Stack<Node> nodes;
        private Function<Node, Node> function;

        TransformationVisitor(Function<Node, Node> function) {
            this.function = function;
            nodes = new Stack<>();
        }

        Node getTransformedNode() {
            assert nodes.size() != 0;
            return nodes.peek();
        }

        @Override
        public void visit(ProgramVariable programVariable) {
            nodes.push(function.apply(programVariable));
        }

        @Override
        public void visit(Location location) {
            nodes.push(function.apply(location));
        }

        @Override
        public void visit(UIFApplication UIFApplication) {
            int argsNum = UIFApplication.getArgs().size();
            ArrayList<Node> args = new ArrayList<>();
            for (int i=0; i< argsNum; i++) {
                args.add(nodes.pop());
            }
            Collections.reverse(args);
            nodes.push(function.apply(new UIFApplication(UIFApplication.getUIF(), args)));
        }

        @Override
        public void visit(Equal equal) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new Equal(left, right)));
        }

        @Override
        public void visit(Add add) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new Add(left, right)));
        }

        @Override
        public void visit(Sub sub) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new Sub(left, right)));
        }

        @Override
        public void visit(Mult mult) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new Mult(left, right)));
        }

        @Override
        public void visit(Div div) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new Div(left, right)));
        }

        @Override
        public void visit(And and) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new And(left, right)));
        }

        @Override
        public void visit(Or or) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new Or(left, right)));
        }

        @Override
        public void visit(Iff iff) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new Iff(left, right)));
        }

        @Override
        public void visit(Impl impl) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new Impl(left, right)));
        }

        @Override
        public void visit(Greater greater) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new Greater(left, right)));
        }

        @Override
        public void visit(Less less) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new Less(left, right)));
        }

        @Override
        public void visit(GreaterOrEqual greaterOrEqual) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new GreaterOrEqual(left, right)));
        }

        @Override
        public void visit(LessOrEqual lessOrEqual) {
            Node right = nodes.pop();
            Node left = nodes.pop();
            nodes.push(function.apply(new LessOrEqual(left, right)));
        }

        @Override
        public void visit(Minus minus) {
            Node arg = nodes.pop();
            nodes.push(function.apply(new Minus(arg)));
        }

        @Override
        public void visit(Not not) {
            Node arg = nodes.pop();
            nodes.push(function.apply(new Not(arg)));
        }

        @Override
        public void visit(IntConst intConst) {
            nodes.push(function.apply(intConst));
        }

        @Override
        public void visit(BoolConst boolConst) {
            nodes.push(function.apply(boolConst));
        }

        @Override
        public void visit(ComponentInput componentInput) {
            nodes.push(function.apply(componentInput));
        }

        @Override
        public void visit(ComponentOutput componentOutput) {
            nodes.push(function.apply(componentOutput));
        }

        @Override
        public void visit(TestInstance testInstance) {
            nodes.push(function.apply(testInstance));
        }

        @Override
        public void visit(Parameter parameter) {
            nodes.push(function.apply(parameter));
        }

        @Override
        public void visit(Hole hole) {
            nodes.push(function.apply(hole));
        }

    }
}