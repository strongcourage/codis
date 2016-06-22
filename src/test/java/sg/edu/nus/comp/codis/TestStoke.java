package sg.edu.nus.comp.codis;

import com.microsoft.z3.*;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.*;
import sg.edu.nus.comp.codis.ast.*;
import sg.edu.nus.comp.codis.ast.theory.*;

import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class TestStoke {

    private static TreeBoundedSynthesis synthesizerUnique;

    private final ProgramVariable rdi_1_INIT = ProgramVariable.mkBV("%rdi_1_INIT", 32);
    private final ProgramVariable rax_2_FINAL = ProgramVariable.mkBV("%rax_2_FINAL", 32);

    @BeforeClass
    public static void initSolver() {
        synthesizerUnique = new TreeBoundedSynthesis(Z3.buildInterpolatingSolver(), 2, true);
    }

    @Test
    public void testStokeSMT2() throws Exception {
        // variables of Codis
        ArrayList<Expr> rdiValues = new ArrayList<>();
        ArrayList<String> listCC = new ArrayList<>();
        ArrayList<String> listNotEq = new ArrayList<>();
        listCC.add ("(assert (= CC false))");
        ArrayList<TestCase> testSuite = new ArrayList<>();

        String smt2File = "/home/dungnguyen/codis/ifelse_stoke.smt2";
        String smt2FileTmp = "/home/dungnguyen/codis/ifelse_stoke_tmp.smt2";

        Map<String, String> mapOperators = new HashMap<>();
        mapOperators.put(">", "bvugt");
        mapOperators.put(">=", "bvuge");
        mapOperators.put("<=", "bvule");
        mapOperators.put("<", "bvult");
        mapOperators.put("==", "=");
        mapOperators.put("mod", "bvsmod");

        String def_in = "%rdi_1_INIT";
        // String def_in = "%rax_2_FINAL";

        // initialize context
        HashMap<String, String> cfg = new HashMap<String, String>();
        cfg.put("model", "true");
        Context ctx = new Context(cfg);
        
        Status res = Status.SATISFIABLE;

        Model model = null;
        int count = 0;
        String newCC = "";
        // while (count < 3) {
        while (res == Status.SATISFIABLE) {
            count++;
            System.out.println("-------------------------------- TIME " + count + " --------------------------------");
            // read smt2 file
            BoolExpr be = ctx.parseSMTLIB2File(smt2File, null, null, null, null);
            // initialize a solver QF_BV
            com.microsoft.z3.Solver solver = ctx.mkSolver("QF_BV");
            solver.add(be);
            res = solver.check();
            System.out.println("solver result: " + res);
            if (res == Status.UNSATISFIABLE)
                break;
            // get a model if SAT
            model = solver.getModel();
            // System.out.println(model);
            // get value of def_in in the model
            int numConsts = model.getNumConsts();
            // System.out.println("numConsts: "+ numConsts);
            FuncDecl[] funcs = model.getConstDecls();
            
            Expr definValue = null;
            for (int i=0; i<numConsts; ++i) {
                if (funcs[i].toString().contains(def_in)) {
                    definValue = model.getConstInterp(funcs[i]);
                    System.out.println("def_in: " + definValue);
                    // add a new value of def_in
                    rdiValues.add(definValue);
                }
            }

            // list of components
            Multiset<Node> components = HashMultiset.create();
            // ArrayList<String> compString = new ArrayList<>();
            components.add(rdi_1_INIT);
            // components.add(rax_2_FINAL); 
            components.add(BVConst.ofLong(0, 32));
            components.add(BVConst.ofLong(1, 32)); 
            // components.add(Components.EQ);
            components.add(Components.BVUGT); 
            components.add(Components.BVUGE); 
            components.add(Components.BVULT); 
            components.add(Components.BVULE); 
            // components.add(Components.BVSMOD);
            
            // add a new test suite
            Map<ProgramVariable, Node> assignment = new HashMap<>();
            // assignment.put(rax_2_FINAL, BVConst.ofLong(Long.parseLong(definValue.toString()), 32));
            assignment.put(rdi_1_INIT, BVConst.ofLong(Long.parseLong(definValue.toString()), 32));
            testSuite.add(TestCase.ofAssignment(assignment, BoolConst.TRUE));
            System.out.println("testSuite size:" + testSuite.size());

            // synthesize a formula
            Optional<Pair<Program, Map<Parameter, Constant>>> result = synthesizerUnique.synthesize(testSuite, components);
            if (!result.isPresent()) {
                System.out.println("Codis cannot synthesize any formula!");
                break;
            }
            Node node = result.get().getLeft().getSemantics(result.get().getRight());
            System.out.println("formula generated by Codis:" + node);

            // Component root = bvSynthesizer.getRootComponent(testSuite, components);
            // System.out.println("Root Component: " + root.toString());
            // System.out.println("Root getInputs: " + root.getInputs());
            // Map<Hole, Program> mapHoleProg = bvSynthesizer.getMap(testSuite, components);
            // // System.out.println("Map: " + mapHoleProg);
            // // Map.Entry<Hole, Program> e = mapHoleProg.entrySet();
            // ArrayList<String> str = new ArrayList<String>();
            // for (Map.Entry<Hole, Program> e : mapHoleProg.entrySet())  {
            //     System.out.println(e.getKey()); 
            //     System.out.println(e.getValue());
            //     if (e.getValue().toString().matches("[-+]?\\d+(\\.\\d+)?")) {
            //         String bvStr = "(_ bv"+e.getValue().toString()+" 64)";
            //         str.add(bvStr);
            //     } else {
            //         str.add(e.getValue().toString());
            //     }
            // }

            String formula = node.toString();
            // find the operator in the formula
            String op = "";
            if (formula.contains(">="))                                 op += ">="; 
            else if (formula.contains(">") && !formula.contains("="))   op += ">"; 
            else if (formula.contains("<="))                            op += "<="; 
            else if (formula.contains("<") && !formula.contains("="))   op += "<"; 
            else if (formula.contains("mod"))                           op += "mod"; 
            else if (formula.contains("=="))                            op += "="; 

            // extract the left component and the right component
            int idOp = 0;
            idOp = formula.indexOf(op);
            // System.out.println("op:" + op);
            // System.out.println("idOp:" + idOp);
            String leftOp = formula.substring(1 , idOp);
            String rightOp = formula.substring(idOp+op.length(), formula.length()-1);
            // System.out.println("leftOp: " + leftOp);
            // System.out.println("rightOp: " + rightOp);
            if (leftOp.matches("[-+]?\\d+(\\.\\d+)?"))
                leftOp = "(_ bv"+leftOp+" 64)";
            if (rightOp.matches("[-+]?\\d+(\\.\\d+)?"))
                rightOp = "(_ bv"+rightOp+" 64)";


            // new CC in SMT2: (assert (= CC (bvugt %rdi_1_INIT (_ bv0 64))))
            newCC = "(assert (= CC (" + mapOperators.get(op) + " " + leftOp + " " + rightOp + ")))";
            System.out.println("newCC: " + newCC); 

            // constraint to find a different value of def_in: (assert (not (= %rdi_1_INIT (_ bv0 64))))
            String notEqConstraint = "\n(assert (not (= " + def_in + " (_ bv" + definValue.toString() + " 64))))";
            listNotEq.add(notEqConstraint);
            System.out.println("notEqConstraint: " + notEqConstraint); 

            // update CC in the smt2 file
            File inputFile = new File(smt2File);
            File tempFile = new File(smt2FileTmp);
            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
            String currentLine;
            String oldCC = listCC.get(listCC.size() - 1);
            System.out.println("oldCC: " + oldCC);

            while((currentLine = reader.readLine()) != null) {
                String trimmedLine = currentLine.trim();
                if(trimmedLine.equals(oldCC)) {
                    if (count == 1) {
                        currentLine = newCC;
                    } else {
                        currentLine = newCC.concat(listNotEq.get(listNotEq.size() - 1));
                    }
                }
                writer.write(currentLine + System.getProperty("line.separator"));
            }
            writer.close(); 
            reader.close(); 
            boolean successful = tempFile.renameTo(inputFile);
            if (successful)
                System.out.println("Update CC successfully");

            listCC.add(newCC);
        } 
        // System.out.println("CC: " + newCC);
        // else {
        //     System.out.println("UNSATISFIABLE!");
        //     return;
        // }
    }
}
