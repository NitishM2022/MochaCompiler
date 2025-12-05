package mocha;

import java.io.*;
import java.util.*;
import org.apache.commons.cli.*;


public class CompilerTester {

    static final String GRAPH_DIR_NAME = "graphs";
    static final String CFG_DOT_FILE_NAME = "cfg.dot";

    public static void main(String[] args) {
        Options options = new Options();
        options.addRequiredOption("s", "src", true, "Source File");
        options.addOption("i", "in", true, "Data File");
        options.addOption("nr", "reg", true, "Num Regs");
        options.addOption("b", "asm", false, "Print DLX instructions");
        options.addOption("a", "astOut", false, "Print AST");
        // options.addOption("int", "interpret", false, "Interpreter mode");
        
        options.addOption("cfg", "cfg", true, "Print CFG.dot - requires graphs/");

        options.addOption("o", "opt", true, "Order-sensitive optimization -allowed to have multiple");
        options.addOption("loop", "convergence", false, "Run all optimization specified by -o until convergence");
        options.addOption("max", "maxOpt", false, "Run all optimizations till convergence");


        HelpFormatter formatter = new HelpFormatter();
        CommandLineParser cmdParser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = cmdParser.parse(options, args);
        } catch (ParseException e) {
            formatter.printHelp("All Options", options);
            System.exit(-1);
        }

        mocha.Scanner s = null;
        String sourceFile = cmd.getOptionValue("src");
        try {
            s = new mocha.Scanner(sourceFile, new FileReader(sourceFile));
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error accessing the code file: \"" + sourceFile + "\"");
            System.exit(-3);
        }

        InputStream in = System.in;
        if (cmd.hasOption("in")) {
            String inputFilename = cmd.getOptionValue("in");
            try {
                in = new FileInputStream(inputFilename);
            }
            catch (IOException e) {
                System.err.println("Error accessing the data file: \"" + inputFilename + "\"");
                System.exit(-2);
            }
        }

        // Create graph dir if needed
        File dir = new File(GRAPH_DIR_NAME);
            if (!dir.exists()) {
                dir.mkdirs();
            }

        String strNumRegs = cmd.getOptionValue("reg", "24");
        int numRegs = 24;
        try {
            numRegs = Integer.parseInt(strNumRegs);
            if (numRegs > 24) {
                System.err.println("reg num too large - setting to 24");
                numRegs = 24;
            }
            if (numRegs < 2) {
                System.err.println("reg num too small - setting to 2");
                numRegs = 2;
            }
        } catch (NumberFormatException e) {
            System.err.println("Error in option NumRegs -- reseting to 24 (default)");
            numRegs = 24;
        }

        
        mocha.Compiler c = new mocha.Compiler(s, numRegs);
        ast.AST ast = c.genAST();
        if (cmd.hasOption("a")) { // AST to Screen
            String ast_text = ast.printPreOrder();
            System.out.println(ast_text);
        }
        
        if (c.hasError()) {
            System.out.println("Error parsing file.");
            System.out.println(c.errorReport());
            System.exit(-8);
        }

        types.TypeChecker tc = new types.TypeChecker();

        if (!tc.check(ast)) {
            System.out.println("Error type-checking file.");
            System.out.println(tc.errorReport());
            System.exit(-4);
        }

        // if (cmd.hasOption("int")) { // Interpreter mode - at this point the program is well-formed
        //     c.interpret(in);
        // } else {
        // }

        // For IR Visualizer - use SSA-converted IR
        String dotgraph_text = null;
        try {
            // Generate SSA-converted IR for better visualization
            java.util.List<ir.cfg.CFG> ssaCfgs = c.genSSA(ast);
            ir.IROutput ssaOutput = new ir.IROutput(ssaCfgs);
            dotgraph_text = ssaOutput.asDotGraph();
            
            if (cmd.hasOption("cfg")) {
                String[] cfg_output_options = cmd.getOptionValues("cfg");
                
                for (String cfg_output: cfg_output_options) {
                    switch (cfg_output) {
                        case "screen":
                            System.out.println(dotgraph_text);
                            break;
                        case "file":
                            String basename = sourceFile.substring(sourceFile.lastIndexOf(File.separator) + 1);
                            String filename = basename.substring(0, basename.lastIndexOf('.')) + "_"+ CFG_DOT_FILE_NAME;
                            try (PrintStream out = new PrintStream(GRAPH_DIR_NAME+File.separator+filename)) {               
                                out.print(dotgraph_text);
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.err.println("Error accessing the cfg file: " + GRAPH_DIR_NAME + File.separator + filename);
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error caught - see stderr for stack trace " + e.getMessage());
            System.exit(-5);
        }

        // The next 3 lines are for Optimization - reuses already-generated SSA CFGs
        String[] optArgs = cmd.getOptionValues("opt");
        List<String> optArguments = (optArgs!=null && optArgs.length != 0) ? Arrays.asList(optArgs) : new ArrayList<String>();
        c.optimization(optArguments, cmd.hasOption("loop"), cmd.hasOption("max"));  // Uses currentCFGs set by genSSA above
        // we expect after this, there is file recording all transformations your compiler did
        // e.g., if we run -s test000.txt -o cp -o cf -o dce -loop
        // the file will have the name "record_test000_cp_cf_dce_loop.txt"
        
        // Output post-optimization CFG if optimizations were applied
        if (cmd.hasOption("cfg") && !optArguments.isEmpty()) {
            try {
                ir.IROutput postOptOutput = new ir.IROutput(c.getCurrentCFGs());
                String postOptDotGraph = postOptOutput.asDotGraph();
                
                String[] cfg_output_options = cmd.getOptionValues("cfg");
                for (String cfg_output: cfg_output_options) {
                    switch (cfg_output) {
                        case "screen":
                            System.out.println("\n=== POST-OPTIMIZATION CFG ===");
                            System.out.println(postOptDotGraph);
                            break;
                        case "file":
                            String basename2 = sourceFile.substring(sourceFile.lastIndexOf(File.separator) + 1);
                            String optSuffix = String.join("_", optArguments);
                            String postFilename = basename2.substring(0, basename2.lastIndexOf('.')) + "_post_" + optSuffix + "_cfg.dot";
                            try (PrintStream out = new PrintStream(GRAPH_DIR_NAME+File.separator+postFilename)) {               
                                out.print(postOptDotGraph);
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.err.println("Error accessing the post-opt cfg file: " + GRAPH_DIR_NAME + File.separator + postFilename);
                            }
                            break;
                        default:
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error generating post-optimization CFG: " + e.getMessage());
            }
        }

        //Register Allocation
        c.regAlloc(numRegs);

        //Code Gen
        int[] program = c.genCode();
        if (c.hasError()) {
            System.out.println("Error compiling file");
            System.out.println(c.errorReport());
            System.exit(-6);
        }

        if (cmd.hasOption("asm")) {
            String asmFile = sourceFile.substring(0, sourceFile.lastIndexOf('.')) + "_asm.txt";
            try (PrintStream out = new PrintStream(asmFile)) {
                for (int i = 0; i < program.length; i++) {
                    out.print(i + ":\t" + DLX.instrString(program[i])); // \newline included in DLX.instrString()
                }
            } catch (IOException e) {
                System.err.println("Error accessing the asm file: \"" + asmFile + "\"");
                System.exit(-7);
            }
        }

        //Execute!
        DLX.load(program);
        try {
            DLX.execute(in);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("IOException inside DLX");
            System.exit(-8);
        }

    }
}
