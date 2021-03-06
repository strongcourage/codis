package sg.edu.nus.comp.codis;

import fj.data.Either;
import mathsat.api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sg.edu.nus.comp.codis.ast.*;
import sg.edu.nus.comp.codis.ast.theory.*;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Alberto Griggio on 28/4/2016.
 */
public class MathSAT implements Solver, InterpolatingSolver {

    private Logger logger = LoggerFactory.getLogger(MathSAT.class);

    private long config;
    private long solver = 0;

    private MathSAT(boolean interpolating) {
        this.config = mathsat.api.msat_create_config();
        mathsat.api.msat_set_option(this.config, "model_generation", "true");
        if (interpolating) {
            mathsat.api.msat_set_option(this.config, "interpolation", "true");
        }
        //mathsat.api.msat_set_option(this.config, "debug.api_call_trace", "1");
        //mathsat.api.msat_set_option(this.config, "debug.api_call_trace_filename", "trace.smt2");
    }

    private void reset() {
        this.solver = mathsat.api.msat_create_env(this.config);
    }

    public static Solver buildSolver() {
        return new MathSAT(false);
    }

    public static InterpolatingSolver buildInterpolatingSolver() {
        return new MathSAT(true);
    }

    public void dispose() {
        if (solver != 0) {
            mathsat.api.msat_destroy_env(this.solver);
//            mathsat.api.msat_destroy_config(this.config);
        }
    }

    private RuntimeException msatError() {
        return new RuntimeException("MathSAT ERROR: " + mathsat.api.msat_last_error_message(solver));
    }

    @Override
    public Either<Map<Variable, Constant>, List<Node>> getModelOrCore(List<Node> clauses,
                                                                      List<Node> assumptions) {

        dispose();
        reset();
        VariableMarshaller marshaller = new VariableMarshaller();
        for (Node clause : clauses) {
            NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(marshaller);
            clause.accept(visitor);
            mathsat.api.msat_assert_formula(solver, visitor.getExpr());
        }
        ArrayList<Long> assumptionExprs = new ArrayList<>();
        for (Node assumption : assumptions) {
            NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(marshaller);
            assumption.accept(visitor);
            assumptionExprs.add(visitor.getExpr());
        }

        long[] assumptionArray = new long[assumptionExprs.size()];
        int idx = 0;
        for (long a : assumptionExprs) {
            assumptionArray[idx++] = a;
        }

        int status = mathsat.api.msat_solve_with_assumptions(solver, assumptionArray);
        if (status == mathsat.api.MSAT_SAT) {
            long model = mathsat.api.msat_get_model(solver);
            if (mathsat.api.MSAT_ERROR_MODEL(model)) {
                throw msatError();
            }
            try {
                return Either.left(getAssignment(model, marshaller));
            } finally {
                mathsat.api.msat_destroy_model(model);
            }
        } else if (status == mathsat.api.MSAT_UNKNOWN) {
            throw msatError();
        } else {
            ArrayList<Node> unsatCore = new ArrayList<>();
            long[] unsatCoreArray = mathsat.api.msat_get_unsat_assumptions(solver);
            if (unsatCoreArray == null) {
                throw msatError();
            }
            ArrayList<Long> unsatCoreExprs = new ArrayList<>();
            unsatCoreExprs.ensureCapacity(unsatCoreArray.length);
            for (int i = 0; i < unsatCoreArray.length; ++i) {
                unsatCoreExprs.add(unsatCoreArray[i]);
            }
            for (int i=0; i<assumptionExprs.size(); i++) {
                if (unsatCoreExprs.contains(assumptionExprs.get(i))) {
                    unsatCore.add(assumptions.get(i));
                }
            }
            return Either.right(unsatCore);
        }
    }

    private long getIntVarDecl(String name) {
        long decl = api.msat_declare_function(solver, name, api.msat_get_integer_type(solver));
        return decl;
    }
    private long getIntVar(String name) {
        long d = getIntVarDecl(name);
        return mathsat.api.msat_make_constant(solver, d);
    }

    private long getBoolVarDecl(String name) {
        long decl = api.msat_declare_function(solver, name, api.msat_get_bool_type(solver));
        return decl;
    }

    private long getBoolVar(String name) {
        long d = getBoolVarDecl(name);
        return mathsat.api.msat_make_constant(solver, d);
    }

    private long getBVVarDecl(String name, int size) {
        long decl = api.msat_declare_function(solver, name, api.msat_get_bv_type(solver, size));
        return decl;
    }

    private long getBVVar(String name, int size) {
        long d = getBVVarDecl(name, size);
        return mathsat.api.msat_make_constant(solver, d);
    }

    private Map<Variable, Constant> getAssignment(long model, VariableMarshaller marshaller) {
        HashMap<Variable, Constant> assingment = new HashMap<>();
        for (Variable variable: marshaller.getVariables()) {
            if (TypeInference.typeOf(variable).equals(IntType.TYPE)) {
                long result = mathsat.api.msat_model_eval(model, getIntVar(marshaller.toString(variable)));
                if (mathsat.api.MSAT_ERROR_TERM(result)) {
                    throw msatError();
                }
                if (mathsat.api.msat_term_is_number(solver, result) != 0) {
                    int value = Integer.parseInt(mathsat.api.msat_term_repr(result));
                    assingment.put(variable, IntConst.of(value));
                } else {
                    throw new RuntimeException("unsupported MathSAT expression type");
                }
            } else if (TypeInference.typeOf(variable).equals(BoolType.TYPE)) {
                long result = mathsat.api.msat_model_eval(model, getBoolVar(marshaller.toString(variable)));
                if (mathsat.api.MSAT_ERROR_TERM(result)) {
                    throw msatError();
                }
                if (mathsat.api.msat_term_is_true(solver, result) != 0) {
                    assingment.put(variable, BoolConst.of(true));
                } else if (mathsat.api.msat_term_is_false(solver, result) != 0) {
                    assingment.put(variable, BoolConst.of(false));
                } else {
                    throw new RuntimeException("wrong variable type");
                }
            } else if (TypeInference.typeOf(variable) instanceof BVType) {
                int size = ((BVType) TypeInference.typeOf(variable)).getSize();
                long result = mathsat.api.msat_model_eval(model, getBVVar(marshaller.toString(variable), size));
                if (mathsat.api.msat_term_is_number(solver, result) != 0) {
                    assingment.put(variable, BVConst.ofLong(convertMathSATNumeral(solver, result), size));
                } else {
                    throw new RuntimeException("unsupported MathSAT expression type");
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }
        return assingment;
    }

    private Long convertMathSATNumeral(long solver, long result) {
        String repr = mathsat.api.msat_term_to_number(solver, result);
        int index = repr.indexOf('_');
        if (index >= 0) {
            repr = repr.substring(0, index); //NOTE: this is a workaround for bv
        }
        return Long.parseLong(repr);
    }

    @Override
    public Optional<Map<Variable, Constant>> getModel(List<Node> clauses) {
        dispose();
        reset();
        VariableMarshaller marshaller = new VariableMarshaller();
        for (Node clause : clauses) {
            NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(marshaller);
            clause.accept(visitor);
            mathsat.api.msat_assert_formula(solver, visitor.getExpr());
        }

//        if (logger.isTraceEnabled()) {
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
//            Date now = new Date();
//            Path logsmt = Paths.get("cbs" + sdf.format(now) + ".smt2");
//            try {
//                List<String> entries = new ArrayList<>();
//                entries.add("(set-option :produce-models true)");
//                Set<Long> seenDecls = new HashSet<>();
//                for (long d : decls) {
//                    if (seenDecls.add(d)) {
//                        String n = mathsat.api.msat_decl_get_name(d);
//                        String tp = "Bool";
//                        if (mathsat.api.msat_is_integer_type(solver, mathsat.api.msat_decl_get_return_type(d)) != 0) {
//                            tp = "Int";
//                        }
//                        entries.add("(declare-fun |" + n.replace("|", "\\|") + "| () " + tp + ")");
//                    }
//                }
//                long[] assertions = mathsat.api.msat_get_asserted_formulas(solver);
//                for (int i = 0; i < assertions.length; ++i) {
//                    String c = mathsat.api.msat_to_smtlib2_term(solver, assertions[i]);
//                    entries.add("(assert " + c + ")");
//                }
//                entries.add("(check-sat)");
//                entries.add("(get-model)");
//                Files.write(logsmt, entries, Charset.forName("UTF-8"));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

        int status = mathsat.api.msat_solve(solver);
        if (status == mathsat.api.MSAT_SAT) {
            long model = mathsat.api.msat_get_model(solver);
            if (mathsat.api.MSAT_ERROR_MODEL(model)) {
                throw msatError();
            }
            try {
                return Optional.of(getAssignment(model, marshaller));
            } finally {
                mathsat.api.msat_destroy_model(model);
            }
        } else if (status == mathsat.api.MSAT_UNKNOWN) {
            throw msatError();
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Either<Map<Variable, Constant>, Node> getModelOrInterpolant(List<Node> leftClauses, List<Node> rightClauses) {
        dispose();
        reset();

        VariableMarshaller marshaller = new VariableMarshaller();

        int groupA = mathsat.api.msat_create_itp_group(solver);
        int groupB = mathsat.api.msat_create_itp_group(solver);

        mathsat.api.msat_set_itp_group(solver, groupA);
        for (Node leftClause : leftClauses) {
            NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(marshaller);
            leftClause.accept(visitor);
            int error = mathsat.api.msat_assert_formula(solver, visitor.getExpr());
            assert (error == 0);
        }

        mathsat.api.msat_set_itp_group(solver, groupB);

        for (Node rightClause : rightClauses) {
            NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(marshaller);
            rightClause.accept(visitor);
            int error = mathsat.api.msat_assert_formula(solver, visitor.getExpr());
            assert (error == 0);
        }

        int status = mathsat.api.msat_solve(solver);
        if (status == mathsat.api.MSAT_SAT) {
            long model = mathsat.api.msat_get_model(solver);
            if (mathsat.api.MSAT_ERROR_MODEL(model)) {
                throw msatError();
            }
            try {
                return Either.left(getAssignment(model, marshaller));
            } finally {
                mathsat.api.msat_destroy_model(model);
            }
        } else if (status == mathsat.api.MSAT_UNKNOWN) {
            throw msatError();
        } else {
            int[] groupsOfA = {groupA};
            long interpolant = mathsat.api.msat_get_interpolant(solver, groupsOfA, 1);
            assert (!mathsat.api.MSAT_ERROR_TERM(interpolant));
//            String s = mathsat.api.msat_to_smtlib2_term(solver, interpolant);
//            System.out.println("\nOK, the interpolant is: " + s);
            //TODO: convert to Node
            //return Either.right(convertMathSATToNode(solver, interpolant, marshaller));
            return Either.right(ProgramVariable.mkBool("<unknown>"));
        }

    }

    private long[] getArgs(long expr) {
        int arity = mathsat.api.msat_term_arity(expr);
        long[] args = new long[arity];
        for (int i=0; i<arity; i++) {
            args[i] = mathsat.api.msat_term_get_arg(expr, i);
        }
        return args;
    }

    private Node convertMathSATToNode(long solver, long expr, VariableMarshaller marshaller) {
        int[] sizeAux = new int[1];
        if (mathsat.api.msat_term_is_plus(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new Add(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_times(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new Mult(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_and(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new And(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_or(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new Or(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_iff(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new Iff(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_leq(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new LessOrEqual(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_equal(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new Equal(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_not(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new Not(convertMathSATToNode(solver, args[0], marshaller));
        } else if (mathsat.api.msat_term_is_number(solver, expr) != 0
                && mathsat.api.msat_is_integer_type(solver, mathsat.api.msat_term_get_type(expr)) != 0) {
            return IntConst.of(Math.toIntExact(convertMathSATNumeral(solver, expr)));
        } else if (mathsat.api.msat_term_is_constant(solver, expr) != 0) {
            return marshaller.toVariable(mathsat.api.msat_decl_get_name(mathsat.api.msat_term_get_decl(expr)));
        } else if (mathsat.api.msat_term_is_true(solver, expr) != 0) {
            return BoolConst.of(true);
        } else if (mathsat.api.msat_term_is_false(solver, expr) != 0) {
            return BoolConst.of(false);
        } else if (mathsat.api.msat_term_is_term_ite(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new ITE(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller), convertMathSATToNode(solver, args[2], marshaller));
        } else if (mathsat.api.msat_term_is_number(solver, expr) != 0
                && mathsat.api.msat_is_bv_type(solver, mathsat.api.msat_term_get_type(expr), null) != 0) {
            return BVConst.ofLong(convertMathSATNumeral(solver, expr), sizeAux[0]);
        } else if (mathsat.api.msat_term_is_bv_plus(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVAdd(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_sdiv(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVSignedDiv(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_udiv(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVUnsignedDiv(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_times(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVMult(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_minus(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVSub(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_neg(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVNeg(convertMathSATToNode(solver, args[0], marshaller));
        } else if (mathsat.api.msat_term_is_bv_and(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVAnd(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_or(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVOr(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_xor(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVXor(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_not(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVNot(convertMathSATToNode(solver, args[0], marshaller));

        } else if (mathsat.api.msat_term_is_bv_lshl(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVShiftLeft(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_lshr(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVUnsignedShiftRight(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_ashr(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVSignedShiftRight(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_sdiv(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVSignedDiv(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_udiv(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVUnsignedDiv(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_srem(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVSignedRemainder(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_urem(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVUnsignedRemainder(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));

        } else if (mathsat.api.msat_term_is_bv_slt(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVSignedLess(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_ult(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVUnsignedLess(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_sleq(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVSignedLessOrEqual(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        } else if (mathsat.api.msat_term_is_bv_uleq(solver, expr) != 0) {
            long[] args = getArgs(expr);
            return new BVUnsignedLessOrEqual(convertMathSATToNode(solver, args[0], marshaller), convertMathSATToNode(solver, args[1], marshaller));
        }

        throw new UnsupportedOperationException("failed to convert MathSAT formula: " + mathsat.api.msat_to_smtlib2_term(solver, expr));
    }


    private class NodeTranslatorVisitor implements BottomUpVisitor {

        private Stack<Long> exprs;

        private VariableMarshaller marshaller;

        NodeTranslatorVisitor(VariableMarshaller marshaller) {
            this.marshaller = marshaller;
            this.exprs = new Stack<>();
        }

        long getExpr() {
            assert exprs.size() == 1;
            return exprs.peek();
        }

        private void processVariable(Variable variable) {
            if (TypeInference.typeOf(variable).equals(IntType.TYPE)) {
                pushExpr(getIntVar(marshaller.toString(variable)));
            } else if (TypeInference.typeOf(variable).equals(BoolType.TYPE)) {
                pushExpr(getBoolVar(marshaller.toString(variable)));
            }else if (TypeInference.typeOf(variable) instanceof BVType) {
                int size = ((BVType) TypeInference.typeOf(variable)).getSize();
                pushExpr(getBVVar(marshaller.toString(variable), size));
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private void pushExpr(long e) {
            if (mathsat.api.MSAT_ERROR_TERM(e)) {
                throw msatError();
            }
            exprs.push(e);
        }

        @Override
        public void visit(ProgramVariable programVariable) {
            processVariable(programVariable);
        }

        @Override
        public void visit(Location location) {
            processVariable(location);
        }

        @Override
        public void visit(UIFApplication UIFApplication) {
            //TODO
            throw new NotImplementedException();
        }

        @Override
        public void visit(Equal equal) {
            long right = exprs.pop();
            long left = exprs.pop();
            if (mathsat.api.msat_is_bool_type(solver, mathsat.api.msat_term_get_type(left)) != 0) {
                pushExpr(mathsat.api.msat_make_iff(solver, left, right));
            } else {
                pushExpr(mathsat.api.msat_make_equal(solver, left, right));
            }
        }

        @Override
        public void visit(Add add) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_plus(solver, left, right));
        }

        @Override
        public void visit(Sub sub) {
            long right = exprs.pop();
            long left = exprs.pop();
            long neg = mathsat.api.msat_make_number(solver, "-1");
            pushExpr(mathsat.api.msat_make_plus(solver, left, mathsat.api.msat_make_times(solver, neg, right)));
        }

        @Override
        public void visit(Mult mult) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_times(solver, left, right));
        }

        @Override
        public void visit(Div div) {
            //TODO
            throw new NotImplementedException();
        }

        @Override
        public void visit(And and) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_and(solver, left, right));
        }

        @Override
        public void visit(Or or) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_or(solver, left, right));
        }

        @Override
        public void visit(Iff iff) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_iff(solver, left, right));
        }

        @Override
        public void visit(Impl impl) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_or(solver, mathsat.api.msat_make_not(solver, left), right));
        }

        @Override
        public void visit(Greater greater) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_not(solver, mathsat.api.msat_make_leq(solver, left, right)));
        }

        @Override
        public void visit(Less less) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_not(solver, mathsat.api.msat_make_leq(solver, right, left)));
        }

        @Override
        public void visit(GreaterOrEqual greaterOrEqual) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_leq(solver, right, left));
        }

        @Override
        public void visit(LessOrEqual lessOrEqual) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_leq(solver, left, right));
        }

        @Override
        public void visit(Minus minus) {
            long neg = mathsat.api.msat_make_number(solver, "-1");
            pushExpr(mathsat.api.msat_make_times(solver, neg, exprs.pop()));
        }

        @Override
        public void visit(Not not) {
            pushExpr(mathsat.api.msat_make_not(solver, exprs.pop()));
        }

        @Override
        public void visit(IntConst intConst) {
            pushExpr(mathsat.api.msat_make_number(solver, Integer.toString(intConst.getValue())));
        }

        @Override
        public void visit(BoolConst boolConst) {
            if (boolConst.getValue()) {
                pushExpr(mathsat.api.msat_make_true(solver));
            } else {
                pushExpr(mathsat.api.msat_make_false(solver));
            }
        }

        @Override
        public void visit(ComponentInput componentInput) {
            processVariable(componentInput);
        }

        @Override
        public void visit(ComponentOutput componentOutput) {
            processVariable(componentOutput);
        }

        @Override
        public void visit(TestInstance testInstance) {
            processVariable(testInstance);
        }

        @Override
        public void visit(Parameter parameter) {
            processVariable(parameter);
        }

        @Override
        public void visit(Hole hole) {
            processVariable(hole);
        }

        @Override
        public void visit(BranchOutput branchOutput) {
            processVariable(branchOutput);
        }

        @Override
        public void visit(ITE ite) {
            long elseBranch = exprs.pop();
            long thenBranch = exprs.pop();
            long condition = exprs.pop();
            pushExpr(mathsat.api.msat_make_term_ite(solver, condition, thenBranch, elseBranch));
        }

        @Override
        public void visit(Selector selector) {
            processVariable(selector);
        }

        @Override
        public void visit(BVConst bvConst) {
            long value = bvConst.getLong();
            if (value < 0) {
                pushExpr(mathsat.api.msat_make_bv_neg(solver,
                        mathsat.api.msat_make_bv_number(solver, Long.toString(-value), bvConst.getType().getSize(), 10)));
            } else {
                pushExpr(mathsat.api.msat_make_bv_number(solver, Long.toString(value), bvConst.getType().getSize(), 10));
            }
        }

        @Override
        public void visit(BVAdd bvAdd) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_plus(solver, left, right));
        }

        @Override
        public void visit(BVAnd bvAnd) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_and(solver, left, right));
        }

        @Override
        public void visit(BVMult bvMult) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_times(solver, left, right));
        }

        @Override
        public void visit(BVNeg bvNeg) {
            long arg = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_neg(solver, arg));
        }

        @Override
        public void visit(BVNot bvNot) {
            long arg = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_not(solver, arg));
        }

        @Override
        public void visit(BVOr bvOr) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_or(solver, left, right));
        }

        @Override
        public void visit(BVShiftLeft bvShiftLeft) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_lshl(solver, left, right));
        }

        @Override
        public void visit(BVSignedDiv bvSignedDiv) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_sdiv(solver, left, right));
        }

        @Override
        public void visit(BVSignedGreater bvSignedGreater) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_slt(solver, right, left)); //NOTE: change order
        }

        @Override
        public void visit(BVSignedGreaterOrEqual bvSignedGreaterOrEqual) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_sleq(solver, right, left)); //NOTE: change order
        }

        @Override
        public void visit(BVSignedLess bvSignedLess) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_slt(solver, left, right));
        }

        @Override
        public void visit(BVSignedLessOrEqual bvSignedLessOrEqual) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_sleq(solver, left, right));
        }

        @Override
        public void visit(BVSignedModulo bvSignedModulo) {
            throw new UnsupportedOperationException(); // FIXME: not available in MathSAT API
        }

        @Override
        public void visit(BVSignedRemainder bvSignedRemainder) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_srem(solver, left, right));
        }

        @Override
        public void visit(BVSignedShiftRight bvSignedShiftRight) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_ashr(solver, left, right));
        }

        @Override
        public void visit(BVSub bvSub) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_minus(solver, left, right));
        }

        @Override
        public void visit(BVUnsignedDiv bvUnsignedDiv) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_udiv(solver, left, right));
        }

        @Override
        public void visit(BVUnsignedGreater bvUnsignedGreater) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_ult(solver, right, left)); //NOTE: change order
        }

        @Override
        public void visit(BVUnsignedGreaterOrEqual bvUnsignedGreaterOrEqual) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_uleq(solver, right, left)); //NOTE: change order
        }

        @Override
        public void visit(BVUnsignedLess bvUnsignedLess) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_ult(solver, left, right));
        }

        @Override
        public void visit(BVUnsignedLessOrEqual bvUnsignedLessOrEqual) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_uleq(solver, left, right));
        }

        @Override
        public void visit(BVUnsignedRemainder bvUnsignedRemainder) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_urem(solver, left, right));
        }

        @Override
        public void visit(BVUnsignedShiftRight bvUnsignedShiftRight) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_lshr(solver, left, right));
        }

        @Override
        public void visit(BVNand bvNand) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_not(solver, mathsat.api.msat_make_bv_and(solver, left, right)));
        }

        @Override
        public void visit(BVXor bvXor) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_xor(solver, left, right));
        }

        @Override
        public void visit(BVNor bvNor) {
            long right = exprs.pop();
            long left = exprs.pop();
            pushExpr(mathsat.api.msat_make_bv_not(solver, mathsat.api.msat_make_bv_or(solver, left, right)));
        }

        @Override
        public void visit(BVXnor bvXnor) {
            //TODO: (bvxnor s t) abbreviates (bvor (bvand s t) (bvand (bvnot s) (bvnot t)))
            throw new UnsupportedOperationException();
        }

        @Override
        public void visit(ProgramOutput programOutput) {
            processVariable(programOutput);
        }

        @Override
        public void visit(Dummy dummy) {
            processVariable(dummy);
        }

        @Override
        public void visit(Indexed indexed) {
            processVariable(indexed);
        }

    }

}
