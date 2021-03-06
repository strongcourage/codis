package sg.edu.nus.comp.codis;

import sg.edu.nus.comp.codis.ast.*;
import sg.edu.nus.comp.codis.ast.theory.*;

import java.util.*;

/**
 * Created by Sergey Mechtaev on 8/4/2016.
 */
public class Unifier {

    public static Optional<Map<Hole, Node>> unify(Node pattern, Node node) {
        UnifyVisitor visitor = new UnifyVisitor(node);
        pattern.accept(visitor);
        return visitor.getUnifier();
    }

    private static class UnifyVisitor implements TopDownVisitor {

        private Map<Hole, Node> unifier;

        private Stack<Node> nodeStack;

        private boolean failed;

        private boolean updateUnifier(Hole hole, Node node) {
            if (unifier.containsKey(hole)) {
                return unifier.get(hole).equals(node);
            } else {
                unifier.put(hole, node);
                return true;
            }
        }

        private void processLeaf(Node leaf) {
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right.equals(leaf))
                return;
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            failed = true;
        }

        private void processBinaryOp(BinaryOp node) {
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right.getClass().equals(node.getClass())) {
                nodeStack.push(((BinaryOp)right).getRight());
                nodeStack.push(((BinaryOp)right).getLeft());
                return;
            }
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            failed = true;
        }

        private void processUnaryOp(UnaryOp node) {
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right.getClass().equals(node.getClass())) {
                nodeStack.push(((UnaryOp)right).getArg());
                return;
            }
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            failed = true;
        }


        public UnifyVisitor(Node right) {
            this.nodeStack = new Stack<>();
            this.nodeStack.push(right);
            failed = false;
            unifier = new HashMap<>();
        }

        public Optional<Map<Hole, Node>> getUnifier() {
            if (failed) {
                return Optional.empty();
            } else {
                return Optional.of(unifier);
            }
        }

        @Override
        public void visit(ProgramVariable programVariable) {
            if (failed) return;
            processLeaf(programVariable);
        }

        @Override
        public void visit(Location location) {
            if (failed) return;
            processLeaf(location);
        }

        @Override
        public void visit(UIFApplication UIFApplication) {
            if (failed) return;
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right instanceof UIFApplication &&
                    ((UIFApplication)right).getUIF().equals(UIFApplication.getUIF())) {
                ArrayList<Node> args = ((UIFApplication)right).getArgs();
                Collections.reverse(args);
                for (Node arg : args) {
                    nodeStack.push(arg);
                }
                return;
            }
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            failed = true;
        }

        @Override
        public void visit(Equal equal) {
            if (failed) return;
            processBinaryOp(equal);
        }

        @Override
        public void visit(Add add) {
            if (failed) return;
            processBinaryOp(add);
        }

        @Override
        public void visit(Sub sub) {
            if (failed) return;
            processBinaryOp(sub);
        }

        @Override
        public void visit(Mult mult) {
            if (failed) return;
            processBinaryOp(mult);
        }

        @Override
        public void visit(Div div) {
            if (failed) return;
            processBinaryOp(div);
        }

        @Override
        public void visit(And and) {
            if (failed) return;
            processBinaryOp(and);
        }

        @Override
        public void visit(Or or) {
            if (failed) return;
            processBinaryOp(or);
        }

        @Override
        public void visit(Iff iff) {
            if (failed) return;
            processBinaryOp(iff);
        }

        @Override
        public void visit(Impl impl) {
            if (failed) return;
            processBinaryOp(impl);
        }

        @Override
        public void visit(Greater greater) {
            if (failed) return;
            processBinaryOp(greater);
        }

        @Override
        public void visit(Less less) {
            if (failed) return;
            processBinaryOp(less);
        }

        @Override
        public void visit(GreaterOrEqual greaterOrEqual) {
            if (failed) return;
            processBinaryOp(greaterOrEqual);
        }

        @Override
        public void visit(LessOrEqual lessOrEqual) {
            if (failed) return;
            processBinaryOp(lessOrEqual);
        }

        @Override
        public void visit(Minus minus) {
            if (failed) return;
            processUnaryOp(minus);
        }

        @Override
        public void visit(Not not) {
            if (failed) return;
            processUnaryOp(not);
        }

        @Override
        public void visit(IntConst intConst) {
            if (failed) return;
            processLeaf(intConst);
        }

        @Override
        public void visit(BoolConst boolConst) {
            if (failed) return;
            processLeaf(boolConst);
        }

        @Override
        public void visit(ComponentInput componentInput) {
            if (failed) return;
            processLeaf(componentInput);
        }

        @Override
        public void visit(ComponentOutput componentOutput) {
            if (failed) return;
            processLeaf(componentOutput);
        }

        @Override
        public void visit(TestInstance testInstance) {
            if (failed) return;
            processLeaf(testInstance);
        }

        @Override
        public void visit(Parameter parameter) {
            if (failed) return;
            processLeaf(parameter);
        }

        @Override
        public void visit(Hole hole) {
            if (failed) return;
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            if (hole.getType().equals(TypeInference.typeOf(right)) &&
                    hole.getSuperclass().isInstance(right) &&
                    updateUnifier(hole, right)) {
                return;
            }
            failed = true;

        }

        @Override
        public void visit(ITE ite) {
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right instanceof ITE) {
                nodeStack.push(((ITE)right).getElseBranch());
                nodeStack.push(((ITE)right).getThenBranch());
                nodeStack.push(((ITE)right).getCondition());
                return;
            }
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            failed = true;
        }

        @Override
        public void visit(Selector selector) {
            if (failed) return;
            processLeaf(selector);
        }

        @Override
        public void visit(BVConst bvConst) {
            if (failed) return;
            processLeaf(bvConst);
        }

        @Override
        public void visit(BVAdd bvAdd) {
            if (failed) return;
            processBinaryOp(bvAdd);
        }

        @Override
        public void visit(BVAnd bvAnd) {
            if (failed) return;
            processBinaryOp(bvAnd);
        }

        @Override
        public void visit(BVMult bvMult) {
            if (failed) return;
            processBinaryOp(bvMult);
        }

        @Override
        public void visit(BVNeg bvNeg) {
            if (failed) return;
            processUnaryOp(bvNeg);
        }

        @Override
        public void visit(BVNot bvNot) {
            if (failed) return;
            processUnaryOp(bvNot);
        }

        @Override
        public void visit(BVOr bvOr) {
            if (failed) return;
            processBinaryOp(bvOr);
        }

        @Override
        public void visit(BVShiftLeft bvShiftLeft) {
            if (failed) return;
            processBinaryOp(bvShiftLeft);
        }

        @Override
        public void visit(BVSignedDiv bvSignedDiv) {
            if (failed) return;
            processBinaryOp(bvSignedDiv);
        }

        @Override
        public void visit(BVSignedGreater bvSignedGreater) {
            if (failed) return;
            processBinaryOp(bvSignedGreater);
        }

        @Override
        public void visit(BVSignedGreaterOrEqual bvSignedGreaterOrEqual) {
            if (failed) return;
            processBinaryOp(bvSignedGreaterOrEqual);
        }

        @Override
        public void visit(BVSignedLess bvSignedLess) {
            if (failed) return;
            processBinaryOp(bvSignedLess);
        }

        @Override
        public void visit(BVSignedLessOrEqual bvSignedLessOrEqual) {
            if (failed) return;
            processBinaryOp(bvSignedLessOrEqual);
        }

        @Override
        public void visit(BVSignedModulo bvSignedModulo) {
            if (failed) return;
            processBinaryOp(bvSignedModulo);
        }

        @Override
        public void visit(BVSignedRemainder bvSignedRemainder) {
            if (failed) return;
            processBinaryOp(bvSignedRemainder);
        }

        @Override
        public void visit(BVSignedShiftRight bvSignedShiftRight) {
            if (failed) return;
            processBinaryOp(bvSignedShiftRight);
        }

        @Override
        public void visit(BVSub bvSub) {
            if (failed) return;
            processBinaryOp(bvSub);
        }

        @Override
        public void visit(BVUnsignedDiv bvUnsignedDiv) {
            if (failed) return;
            processBinaryOp(bvUnsignedDiv);
        }

        @Override
        public void visit(BVUnsignedGreater bvUnsignedGreater) {
            if (failed) return;
            processBinaryOp(bvUnsignedGreater);
        }

        @Override
        public void visit(BVUnsignedGreaterOrEqual bvUnsignedGreaterOrEqual) {
            if (failed) return;
            processBinaryOp(bvUnsignedGreaterOrEqual);
        }

        @Override
        public void visit(BVUnsignedLess bvUnsignedLess) {
            if (failed) return;
            processBinaryOp(bvUnsignedLess);
        }

        @Override
        public void visit(BVUnsignedLessOrEqual bvUnsignedLessOrEqual) {
            if (failed) return;
            processBinaryOp(bvUnsignedLessOrEqual);
        }

        @Override
        public void visit(BVUnsignedRemainder bvUnsignedRemainder) {
            if (failed) return;
            processBinaryOp(bvUnsignedRemainder);
        }

        @Override
        public void visit(BVUnsignedShiftRight bvUnsignedShiftRight) {
            if (failed) return;
            processBinaryOp(bvUnsignedShiftRight);
        }

        @Override
        public void visit(BranchOutput branchOutput) {
            if (failed) return;
            processLeaf(branchOutput);
        }

        @Override
        public void visit(BVNand bvNand) {
            if (failed) return;
            processBinaryOp(bvNand);
        }

        @Override
        public void visit(BVXor bvXor) {
            if (failed) return;
            processBinaryOp(bvXor);
        }

        @Override
        public void visit(BVNor bvNor) {
            if (failed) return;
            processBinaryOp(bvNor);
        }

        @Override
        public void visit(BVXnor bvXnor) {
            if (failed) return;
            processBinaryOp(bvXnor);
        }

        @Override
        public void visit(ProgramOutput programOutput) {
            if (failed) return;
            processLeaf(programOutput);
        }

        @Override
        public void visit(Dummy dummy) {
            if (failed) return;
            processLeaf(dummy);
        }

        @Override
        public void visit(Indexed indexed) {
            if (failed) return;
            processLeaf(indexed);
        }

    }



}
