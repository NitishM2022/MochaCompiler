package ir.optimizations;

import java.util.*;
import java.io.*;
import ir.cfg.CFG;

public class Optimizer {
    private List<String> transformations;
    private String sourceFileName;
    private List<String> optimizationFlags;
    private boolean loopMode;
    private boolean maxMode;

    public Optimizer() {
        this.transformations = new ArrayList<>();
    }

    public void setSourceFileName(String fileName) {
        this.sourceFileName = fileName;
    }

    public void setOptimizationFlags(List<String> flags, boolean loop, boolean max) {
        this.optimizationFlags = flags != null ? new ArrayList<>(flags) : new ArrayList<>();
        this.loopMode = loop;
        this.maxMode = max;
    }

    public String applyOptimizations(List<String> opts, List<CFG> cfgs, boolean loop, boolean max) {
        transformations.clear();

        List<String> optimizationsToApply = parseOptimizationFlags(opts, max);

        // Handle Orphan Function Elimination (requires global CFG list)
        if (opts != null && opts.contains("ofe")) {
            new OrphanFunctionElimination(this).eliminateOrphans(cfgs);
        }

        if (optimizationsToApply.isEmpty()) {
            return generateOutput(cfgs);
        }

        for (CFG cfg : cfgs) {
            if (!transformations.isEmpty()) {
                transformations.add("");
            }
            logTransformation("Function: " + cfg.getFunctionName());

            runOptimizationPasses(cfg, optimizationsToApply);
        }

        return generateOutput(cfgs);
    }

    private List<String> parseOptimizationFlags(List<String> opts, boolean max) {
        List<String> optimizationsToApply = new ArrayList<>();

        if (max) {
            return Arrays.asList("cf", "cp", "cpp", "dce", "cse");
        }

        if (opts != null && !opts.isEmpty()) {
            for (String opt : opts) {
                if (!opt.equalsIgnoreCase("ofe")) {
                    if (!optimizationsToApply.contains(opt)) {
                        optimizationsToApply.add(opt);
                    }
                }
            }
        }
        return optimizationsToApply;
    }

    private void runOptimizationPasses(CFG cfg, List<String> optimizationsToApply) {
        if (!loopMode && !maxMode) {
            for (String opt : optimizationsToApply) {
                optimizeCFG(cfg, opt);
            }
            return;
        }

        int iteration = 0;
        boolean changedInThisFunction;

        do {
            changedInThisFunction = false;

            logTransformation("Iteration #" + (iteration + 1));
            int transformCountBeforeIteration = transformations.size();

            for (String opt : optimizationsToApply) {
                boolean passChanged = optimizeCFG(cfg, opt);

                if (passChanged) {
                    changedInThisFunction = true;
                    break;
                }
            }

            if (!changedInThisFunction && transformations.size() == transformCountBeforeIteration) {
                transformations.remove(transformations.size() - 1);
            }

            iteration++;

        } while (changedInThisFunction);
    }

    private boolean optimizeCFG(CFG cfg, String optName) {
        switch (optName.toLowerCase()) {
            case "cf":
                return new ConstantFolding(this).optimize(cfg);
            case "cp":
                return new ConstantPropagation(this).optimize(cfg);
            case "cpp":
                return new CopyPropagation(this).optimize(cfg);
            case "dce":
                return new DeadCodeElimination(this).optimize(cfg);
            case "cse":
                return new CommonSubexpressionElimination(this).optimize(cfg);
            default:
                System.err.println("Unknown optimization: " + optName);
                return false;
        }
    }

    public void logTransformation(String message) {
        transformations.add(message);
    }

    public List<String> getTransformations() {
        return transformations;
    }

    private String generateOutput(List<CFG> cfgs) {
        if (!transformations.isEmpty()) {
            writeTransformationsToFile();
        }

        StringBuilder output = new StringBuilder();
        for (CFG cfg : cfgs) {
            output.append(cfg.asDotGraph()).append("\n");
        }
        return output.toString();
    }

    private void writeTransformationsToFile() {
        String fileName = generateTransformationFileName();
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            for (String trans : transformations) {
                writer.println(trans);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not write transformations to file: " + fileName);
            e.printStackTrace();
        }
    }

    private String generateTransformationFileName() {
        StringBuilder fileName = new StringBuilder("record");

        if (sourceFileName != null && !sourceFileName.isEmpty()) {
            String baseName = sourceFileName;
            int lastSlash = baseName.lastIndexOf('/');
            if (lastSlash >= 0)
                baseName = baseName.substring(lastSlash + 1);
            int lastDot = baseName.lastIndexOf('.');
            if (lastDot >= 0)
                baseName = baseName.substring(0, lastDot);
            fileName.append("_").append(baseName);
        }

        if (optimizationFlags != null && !optimizationFlags.isEmpty()) {
            for (String flag : optimizationFlags) {
                fileName.append("_").append(flag);
            }
        }

        if (maxMode) {
            fileName.append("_max");
        } else if (loopMode) {
            fileName.append("_loop");
        }

        fileName.append(".txt");
        return fileName.toString();
    }
}