package edu.hust.xzf.mutator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import edu.hust.xzf.AST.ASTGenerator;
import edu.hust.xzf.entity.Pair;
import edu.hust.xzf.jdt.tree.ITree;
import edu.hust.xzf.mutator.config.Configuration;
import edu.hust.xzf.mutator.config.JDK;
import edu.hust.xzf.mutator.context.ContextReader;
import edu.hust.xzf.mutator.context.Dictionary;
import edu.hust.xzf.mutator.deoptpatterns.Inline;
import edu.hust.xzf.mutator.deoptpatterns.Mutators;
import edu.hust.xzf.mutator.info.Patch;
import edu.hust.xzf.mutator.mutatetemplate.MutateTemplate;
import edu.hust.xzf.mutator.utils.*;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.hust.xzf.mutator.config.Configuration.usejTreg;
import static edu.hust.xzf.mutator.utils.CodeUtils.countChar;

public class Scheduler {
    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);
    private static final Pattern exceptionPattern = Pattern.compile("java\\.(?:lang|x?).*?(?:Exception|Error)");
    public String packageName = "";
    public boolean isExecutable = false;
    public int curIter = 0;
    public String targetCaseName;
    public String rootDir;
    public String trueTestCase;
    public String javaTemp;
    public CodeNode mutationPoint = null;
    protected Dictionary dic = null;
    Configuration config;
    String iter0CodeStr;

    String iter0Path;

    int[] parentVector;

    String firstSelectClassName;
    String firstSelectMethodName;


    Map<Mutators, Integer> hitTimes = new HashMap<>();
    private final File differentDir = new File("toreport/");
    private String compileOnly = "";

    public Scheduler(Configuration config) {
        this.config = config;
    }

    public static Process runCmd(String cmd) throws IOException {
        Process p;
        if (Configuration.isWin) p = Runtime.getRuntime().exec("cmd.exe /c " + cmd);
        else {
            p = new ProcessBuilder().command("bash", "-c", "timeout " + Configuration.LinuxTimeout + "s " + cmd).start();
        }
        return p;
    }

    private static <T> T getRandomElementByWeights(Set<T> set, double[] weights) {
        int setSize = set.size();
        if (setSize == 0) {
            return null;
        }

        List<Mutators> mutators = (List<Mutators>) new ArrayList<>(set);
        List<Double> weightList = new ArrayList<>();
        double totalWeight = 0.0;
        for (int i = 0; i < setSize; i++) {
            totalWeight += weights[mutators.get(i).ordinal()];
            //            weightList.add(weights[Mutators.values()[i].ordinal()]);
        }

        double random = Math.random() * totalWeight;
        double weightSum = 0.0;

        for (int i = 0; i < setSize; i++) {
            weightSum += weights[mutators.get(i).ordinal()];
            if (random < weightSum) {
                return (T) mutators.get(i);
            }
        }

        return null;
    }

    private static <T> T getRandomElement(Set<T> set) {
        int setSize = set.size();
        if (setSize == 0) {
            return null;
        }

        int randomIndex = new Random().nextInt(setSize);
        int currentIndex = 0;
        for (T element : set) {
            if (currentIndex == randomIndex) {
                return element;
            }
            currentIndex++;
        }

        return null;
    }

    private static void configureLogging(String logFilePath) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        // 创建 PatternLayoutEncoder
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%-12date{YYYY-MM-dd HH:mm:ss.SSS} %-5level - %msg%n");
        encoder.start();

        // 创建 FileAppender
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(loggerContext);
        fileAppender.setFile(logFilePath);
        fileAppender.setName("FILE_APPENDER");  // 为 FileAppender 设置名称

        fileAppender.setEncoder(encoder);
        fileAppender.start();

        // 获取 root logger
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

        // 移除可能存在的 ConsoleAppender
        // if (!Configuration.isWin)
        //     rootLogger.detachAppender("console");

        // 更新 root logger 的 appender 和日志级别
        rootLogger.addAppender(fileAppender);
        rootLogger.setLevel(Level.ALL);

        // 重置 logger context
        //        if (!Configuration.isWin)
        //        loggerContext.reset();
    }

    // 查找以 xcomp 开头的文件
    private static List<File> findXcompFiles(File folder) {
        return Arrays.stream(Objects.requireNonNull(folder.listFiles()))
                .filter(file -> file.isFile() && file.getName().startsWith("xcomp"))
                .collect(Collectors.toList());
    }

    // 比较两个文件内容是否存在差异
    private static boolean hasDiff(List<File> xcompFiles) throws IOException {
        for (int i = 0; i < xcompFiles.size() - 1; i++) {
            for (int j = i + 1; j < xcompFiles.size(); j++) {
                List<String> lines1 = Files.readAllLines(xcompFiles.get(i).toPath());
                List<String> lines2 = Files.readAllLines(xcompFiles.get(j).toPath());

                String exception1 = containsException(lines1);
                String exception2 = containsException(lines2);
                if (exception1 != null && exception1.equals("java.lang.OutOfMemoryError")
                        || (exception2 != null && exception2.equals("java.lang.OutOfMemoryError"))) {
                    continue;
                }

                if (exception1 == null && exception2 != null || exception1 != null && !exception1.equals(exception2)) {
                    return true;
                }
                if (exception1 == null) {
                    int minlen = Math.min(lines1.size(), lines2.size());
                    if (minlen >= 1000)
                        minlen = 1000;
                    for (int k = 0; k < minlen; k++) {
                        if (!lines1.get(k).equals(lines2.get(k))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static String containsException(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = exceptionPattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(0);
            }
        }
        // 如果没有找到匹配项，返回 null
        return null;
    }

    public static boolean compareFilesIgnoringLines(File file1, File file2) throws IOException {
        if (file1.equals(file2))
            return true;
        String content1 = processFile(file1);
        String content2 = processFile(file2);
        if (countChar(content1, '\n') != countChar(content2, '\n'))
            return true;

        return content1.equals(content2);
    }

    private static String processFile(File file) throws IOException {
        StringBuilder processedContent = new StringBuilder();

        try (Stream<String> stream = Files.lines(Paths.get(file.getAbsolutePath()))) {
            stream.forEach(line -> {
                if (!line.startsWith("For random generator") && !line.startsWith("To re-run test with same seed")) {
                    processedContent.append(line).append("\n");
                }
            });
        }

        return processedContent.toString();
    }

    public static void main(String[] args) throws IOException {
        List<File> files = findXcompFiles(new File("D:\\repository\\JVM-Tesing-by-Anti-Optimization\\debug\\Test7159_10_14_19_50_53\\20"));
        boolean check = hasDiff(files);
        System.out.printf(String.valueOf(check));
    }

    private String outputHitTimes() {
        StringBuilder sb = new StringBuilder();
        for (var entry : hitTimes.entrySet()) {
            sb.append(entry.getKey().name()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    private void getClassAndMethodName(ITree tree) {
        String className = null;
        String methodName = "";
        ITree parent = tree.getParent();
        while (parent != null) {
            if (Checker.isMethodDeclaration(parent.getType())) {
                String mLabel = parent.getLabel();
                // private, @@int, MethodName:test, @@Argus:int+a+int[]+b+
                String[] split = mLabel.split(", ");
                for (String s : split) {
                    if (s.startsWith("MethodName:")) {
                        methodName = s.substring(11);
                        break;
                    }
                }
            }
            if (Checker.isTypeDeclaration(parent.getType())) {
                if (className == null)
                    className = ContextReader.readClassName(parent);
                else
                    className = ContextReader.readClassName(parent) + "." + className;
            }
            parent = parent.getParent();
        }
        firstSelectClassName = className;
        firstSelectMethodName = methodName;

        // for constructer
        if (className.equals(methodName) || className.endsWith("." + methodName))
            methodName = "<init>";
        String concat = className + "::" + methodName;
        //        String concat = className + "::*";

        String compileFormat = this.packageName.isEmpty() ? concat : this.packageName + "." + concat;
        compileOnly = String.format(" -XX:CompileCommand=\"compileonly,%s\" ", compileFormat);
    }

    private Patch generateAndValidatePatches(CodeNode cn, Mutators mutator) throws IOException, InterruptedException {
        MutateTemplate mt = mutator.createInstance();
        log.debug("hit mutator: " + mt.getClass().getName() + " and line=" + Configuration.lineNumber + " and iter=" + this.curIter);
        mt.setSuspiciousCodeStr(cn.codeStr);
        mt.setSuspiciousCodeTree(cn.codeAstNode);
        mt.setPackageName(this.packageName);
        String lastTmp = rootDir + (this.curIter - 1) + "/";
        mt.setSourceCodePath(lastTmp, new File(this.javaTemp));

        //        mt.setDictionary(dic);
        mt.generatePatches();
        Patch patch = mt.getPatch();
        if (patch == null) return null;
        testGeneratedPatches(cn, patch);
        mt.updateMutatorPoint();
        return patch;
    }

    public boolean run() {
        //        if (!this.isExecutable) {
        //            log.error("Not executable === " + config.javaFilePath + " and select point:" + this.mutationPoint.codeStr);
        //            return;
        //        }
        prepare(config);
        Set<Mutators> satisfiableMutators = new HashSet<>();
        Map<String, String> varTypesMap = new HashMap<>();
        List<String> allVarNamesList = new ArrayList<>();
        Map<String, List<String>> allVarNamesMap = new HashMap<>();

        double[] weights = new double[Mutators.values().length];
        Arrays.fill(weights, 1);

        try {
            int infiniteLoop = 0;
            // Match fix templates for this suspicious code with its context information.
            while (curIter <= Configuration.maxIter) {
                satisfiableMutators.clear();

                findSatisfiableMutator(mutationPoint.codeAstNode, satisfiableMutators, varTypesMap);
                if (satisfiableMutators.isEmpty()) {
                    throw new Exception("no more mutators!!\n" + this.mutationPoint.codeStr + "\nline: " + Configuration.lineNumber);
                }


                Patch patch = generateAndValidatePatches(mutationPoint, Mutators.synthesizer);

                if (patch == null) {
                    infiniteLoop++;
                    if (infiniteLoop > 25) {
                        runtestCase(rootDir + (this.curIter - 1) + "/");
                        throw new Exception("no more mutators!!\n" + outputHitTimes() + "\nOrigin Str: "
                                + iter0CodeStr + "\nCurrent Str: " + mutationPoint.codeStr + "\nline: "
                                + Configuration.lineNumber + "\nCurrent Iter: " + (curIter - 1));
                    }
                    continue;
                } else infiniteLoop = 0;
                String tmpDir = rootDir + this.curIter + "/";

                if (curIter == Configuration.maxIter) {
                    log.debug("perform differential fuzzing!");
                    runtestCase(tmpDir);
                    return true;
                }

                Configuration.lineNumber = -1;
                this.javaTemp = tmpDir + config.targetCase.replace(".", "/") + ".java";

                parseSuspiciousCode(config, firstSelectClassName, null);

                allVarNamesMap.clear();
                varTypesMap.clear();
                allVarNamesList.clear();
                ContextReader.readAllVariablesAndFields(this.mutationPoint.codeAstNode, allVarNamesMap, varTypesMap, allVarNamesList, this.javaTemp, null);

                curIter++;
            }
            return true;
        } catch (Exception e) {
            //            e.printStackTrace();
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
            return false;
        }
    }

    protected void checkDiff() throws IOException {
        String mutant = rootDir.substring(Configuration.TEMP_FILES_PATH.length());

        // if there are inconsistant, move the dir to the diff dir.
        Files.walk(Paths.get(rootDir))
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    // skip copy if already moved
                    try {
                        // check r8 log file and whether a Error or Exception in the log file
                        File r8Log = new File(dir.toFile(), "r8.log");
                        if (r8Log.exists()) {
                            List<String> lines = Files.readAllLines(r8Log.toPath());
                            boolean shouldCopy = false;
                            for (String line : lines) {
                                if (line.contains("Error") || line.contains("Exception")) {
                                    shouldCopy = true;
                                }
                                if (line.contains("Missing")) {
                                    shouldCopy = false;
                                    break;
                                }
                            }
                            if (shouldCopy) {
                                if (!Files.exists(differentDir.toPath())) {
                                    Files.createDirectory(differentDir.toPath());
                                }
                                org.apache.commons.io.FileUtils.copyDirectory(new File(rootDir), new File(differentDir, mutant));
                            }
                        }

                        List<File> xcompFiles = findXcompFiles(dir.toFile());
                        if (xcompFiles.size() >= 2) {
                            boolean hasDiff = hasDiff(xcompFiles);
                            if (hasDiff) {
                                log.debug("inconsistant in " + dir);
                                if (!Files.exists(differentDir.toPath())) {
                                    Files.createDirectory(differentDir.toPath());
                                }
                                org.apache.commons.io.FileUtils.copyDirectory(new File(rootDir),
                                        new File(differentDir, mutant));
                            }
                        }
                    } catch (IOException e) {
                        log.error("Error in checkDiff() method: " + e.getMessage());
                    }
                });
    }

    private boolean isPrimeType(String varType) {
        return varType.equals("double") || varType.equals("float") || varType.equals("long")
                || varType.equals("int") || varType.equals("short") || varType.equals("byte") || varType.equals("char");
    }

    private String addPatchCodeToFile(CodeNode cn, Patch patch) {
        String javaCode = FileHelper.readFile(this.javaTemp);
        String fixedCodeStr1 = patch.getFixedCodeStr1();
        String fixedCodeStr2 = patch.getFixedCodeStr2();
        int exactBuggyCodeStartPos = patch.getBuggyCodeStartPos();
        int exactBuggyCodeEndPos = patch.getBuggyCodeEndPos();
        String patchCode = fixedCodeStr1;
        boolean needBuggyCode = false;
        if (exactBuggyCodeEndPos > exactBuggyCodeStartPos) {
            if (exactBuggyCodeStartPos != -1 && exactBuggyCodeStartPos < cn.startPos) {
                // Remove the buggy method declaration.
            } else {
                needBuggyCode = true;
                if (exactBuggyCodeStartPos == 0) {
                    // Insert the missing override method, the buggy node is TypeDeclaration.
                    int pos = cn.codeAstNode.getPos() + cn.codeAstNode.getLength() - 1;
                    for (int i = pos; i >= 0; i--) {
                        if (javaCode.charAt(i) == '}') {
                            exactBuggyCodeStartPos = i;
                            exactBuggyCodeEndPos = i + 1;
                            break;
                        }
                    }
                } else if (exactBuggyCodeStartPos == -1) {
                    // Insert generated patch code before the buggy code.
                    exactBuggyCodeStartPos = cn.startPos;
                    exactBuggyCodeEndPos = cn.endPos;
                } else {
                    // Insert a block-held statement to surround the buggy code
                }
            }
        } else if (exactBuggyCodeStartPos == -1 && exactBuggyCodeEndPos == -1) {
            // Replace the buggy code with the generated patch code.
            exactBuggyCodeStartPos = cn.startPos;
            exactBuggyCodeEndPos = cn.endPos;
        } else if (exactBuggyCodeStartPos == exactBuggyCodeEndPos) {
            // Remove buggy variable declaration statement.
            exactBuggyCodeStartPos = cn.startPos;
        }

        patch.setBuggyCodeStartPos(exactBuggyCodeStartPos);
        patch.setBuggyCodeEndPos(exactBuggyCodeEndPos);
        String buggyCode;
        try {
            buggyCode = javaCode.substring(exactBuggyCodeStartPos, exactBuggyCodeEndPos);
            if (needBuggyCode) {
                patchCode += buggyCode;
                if (fixedCodeStr2 != null) {
                    patchCode += fixedCodeStr2;
                }
            }
            String tmpDir = rootDir + this.curIter + "/";
            String javaTemp = tmpDir + config.targetCase.replace(".", "/") + ".java";
            File newFile = new File(javaTemp);
            String patchedJavaFile = javaCode.substring(0, exactBuggyCodeStartPos) + patchCode + javaCode.substring(exactBuggyCodeEndPos);
            patchedJavaFile = patch.doPostProcess(patchedJavaFile);
            FileHelper.outputToFile(newFile, patchedJavaFile, false);
            return javaTemp;
            //            cn.targetJavaFile.delete();
            //            newFile.renameTo(cn.targetJavaFile);
        } catch (StringIndexOutOfBoundsException e) {
            log.debug(exactBuggyCodeStartPos + " ==> " + exactBuggyCodeEndPos + " : " + javaCode.length());
            e.printStackTrace();
            buggyCode = "===StringIndexOutOfBoundsException===";
        }
        return null;
        //        patch.setBuggyCodeStr(buggyCode);
        //        patch.setFixedCodeStr1(patchCode);
    }

    private void findSatisfiableMutator(ITree codeAstNode, Set<Mutators> satisfiableMutators, Map<String, String> varTypesMap) {
        int type = codeAstNode.getType();
        if (Checker.isCastExpression(type)) {
            int castType = codeAstNode.getChild(0).getType();
            //            if (!Checker.isPrimitiveType(castType)) {
            //                satisfiableMutators.add(Mutators.optimisticTypeAssertions);
            //            }
        } else if (Checker.isSimpleName(type) || Checker.isQualifiedName(type)) {
            String varName = ContextReader.readVariableName(codeAstNode);
            String varType = varTypesMap.getOrDefault(varName, null);
            if (varType == null) {
                varType = varTypesMap.get("this." + varName);
            }
            if (varType != null) {
                satisfiableMutators.add(Mutators.inline);
                if (!isPrimeType(varType)) {
                    //                    satisfiableMutators.add(Mutators.optimisticNullnessAssertions);
                    //                    satisfiableMutators.add(Mutators.nullCheckElimination);
                    //                satisfiableMutators.add(Mutators.typeTestElimination);
                    satisfiableMutators.add(Mutators.optimisticTypeStrengthening);
                } else {
                    satisfiableMutators.add(Mutators.algebraicSimplification);
                }
            }
        } else if (Checker.isOperator(type)) {
            String operator = codeAstNode.getLabel();
            switch (operator) {
                case ">>", "<<", ">>>", "<<<" -> {
                    //                    satisfiableMutators.add(Mutators.commonSubexpressionElimination);
                    satisfiableMutators.add(Mutators.operatorStrengthReduction);
                }
                case "+", "-" -> satisfiableMutators.add(Mutators.commonSubexpressionElimination);

                //                satisfiableMutators.add(Mutators.algebraicSimplification);
                case "*", "/", "%" -> satisfiableMutators.add(Mutators.commonSubexpressionElimination);

                //                satisfiableMutators.add(Mutators.algebraicSimplification);
                case "|", "^" -> satisfiableMutators.add(Mutators.commonSubexpressionElimination);
            }
            if (Inline.isValidOp(operator)) {
                satisfiableMutators.add(Mutators.inline);
            }
        } else if (Checker.isAssignment(type)) {
            satisfiableMutators.add(Mutators.escapeAnalysis);
            satisfiableMutators.add(Mutators.conditionalConstantPropagation);
            satisfiableMutators.add(Mutators.autoboxElimination);
        } else if (Checker.isNumberLiteral(type)) {
            satisfiableMutators.add(Mutators.algebraicSimplification);
            satisfiableMutators.add(Mutators.inline);
        } else if (Checker.isVariableDeclarationFragment(type)) {
            satisfiableMutators.add(Mutators.escapeAnalysis);
            satisfiableMutators.add(Mutators.autoboxElimination);
        } else if (Checker.isArrayAccess(type)) {
            satisfiableMutators.add(Mutators.optimisticArrayLengthStrengthening);
            //            satisfiableMutators.add(Mutators.nullCheckElimination);
            //            satisfiableMutators.add(Mutators.rangeCheckElimination);
        }
        if (Checker.isClassInstanceCreation(type) || Checker.isArrayCreation(type)) {
            satisfiableMutators.add(Mutators.escapeAnalysis2);
        }

        //        if (Checker.isClassInstanceCreation(type) || Checker.isMethodInvocation(type) || Checker.isFieldAccess(type) || Checker.isQualifiedName(type)) {
        //            satisfiableMutators.add(Mutators.deReflection);
        //        }

        if (!Checker.isVariableDeclarationStatement(type)) {
            // construct optimzation-able loops
            // ignore variable declaration statement to avoid too many statements
            satisfiableMutators.add(Mutators.loopPeeling);
            satisfiableMutators.add(Mutators.loopUnrolling);
            //            satisfiableMutators.add(Mutators.loop);
            satisfiableMutators.add(Mutators.loopUnswitch);
            //            satisfiableMutators.add(Mutators.safepointElimination);
            //            satisfiableMutators.add(Mutators.iterationRangeSplitting);
        }


        // no condition
        //        if (!config.isCompiler) {
        //            satisfiableMutators.add(Mutators.untakenBranchPruning);
        //        }
        satisfiableMutators.add(Mutators.deadCodeElimination);
        satisfiableMutators.add(Mutators.lockElision);
        satisfiableMutators.add(Mutators.lockFusion);
        if (Configuration.funcPath != null) {
            satisfiableMutators.add(Mutators.synthesizer);
        }
        //        if (!Configuration.isRegression)
        //            satisfiableMutators.add(Mutators.verticalClassMerger);

        for (var child : codeAstNode.getChildren()) {
            findSatisfiableMutator(child, satisfiableMutators, varTypesMap);
        }
    }

    private void selectMutationPoint(Configuration config, List<ITree> statementNode,
                                     CompilationUnit unit, Pair<Integer, Integer> targetRegion) {
        int statementIndex = -1;
        String javaFileContent = FileHelper.readFile(this.javaTemp == null ? config.javaFilePath : this.javaTemp);
        if (Configuration.lineNumber == -1) {
            Random rand = new Random();
            while (true) {
                if (statementNode.isEmpty()) {
                    log.error("No statement node in this suspicious code!");
                    throw new RuntimeException("No statement node in this suspicious code!");
                }
                statementIndex = rand.nextInt(statementNode.size());
                ITree mutationPoint = statementNode.get(statementIndex);
                int startPosition = mutationPoint.getPos();
                int endPosition = startPosition + mutationPoint.getLength();
                int startLine = unit.getLineNumber(startPosition);
                int endLine = unit.getLineNumber(endPosition);
                if (targetRegion != null) {
                    if (startLine < targetRegion.getFirst() || endLine > targetRegion.getSecond()) {
                        statementNode.remove(statementIndex);
                        continue;
                    }
                }
                if (startLine == endLine) {
                    String codeStr = readSuspiciousCode(javaFileContent, mutationPoint);
                    this.mutationPoint = new CodeNode(startPosition, endPosition, mutationPoint, codeStr, startLine);
                    Configuration.lineNumber = startLine;
                    break;
                } else {
                    statementNode.remove(statementIndex);
                }
            }
        } else if (Configuration.lineNumber > 0) {
            // specify the linenumber
            for (ITree tree : statementNode) {
                int startPosition = tree.getPos();
                int endPosition = startPosition + tree.getLength();
                int startLine = unit.getLineNumber(startPosition);
                int endLine = unit.getLineNumber(endPosition);
                //                System.out.println(String.valueOf(startLine));
                if (startLine <= Configuration.lineNumber && endLine >= Configuration.lineNumber) {
                    String codeStr = readSuspiciousCode(javaFileContent, tree);
                    this.mutationPoint = new CodeNode(startPosition, endPosition, tree, codeStr, startLine);
                    break;
                }
            }
        }
    }

    protected void testGeneratedPatches(CodeNode cn, Patch patch) {
        // Testing generated patches.
        String javaTemp = addPatchCodeToFile(cn, patch);
        String tmpDir = rootDir + this.curIter + "/";
        File classTemp = new File(tmpDir + this.packageName.replace(".", "/") + "/" + targetCaseName + ".class");

        //        String buggyCode = patch.getBuggyCodeStr();
        //        if ("===StringIndexOutOfBoundsException===".equals(buggyCode)) continue;
        //        String patchCode = patch.getFixedCodeStr1();
        //        scn.targetClassFile.delete();
        //        if (buggyCode.equals(patchCode))
        //            continue;
        //
        String compileCmd = JDK.getJavac() + " -cp \"" + iter0Path + config.classPathSplitter
                + "libs/android.jar" + "\" -d " + tmpDir + " " + javaTemp;

        log.debug("Compiling\n" + compileCmd);
        try {
            Process p1 = runCmd(compileCmd);
            ShellUtils.getShellOut(p1, 1, rootDir + "log.txt");
            if (!classTemp.exists()) {
                log.debug("perform differential fuzzing!");
                runtestCase(rootDir + (this.curIter -1) + "/"); // run the final mutant
                // TODO: re mutate from last mutant
                log.error("compile command: \n" + compileCmd);
                log.error("line: " + this.mutationPoint.line);
                throw new RuntimeException(config.projectPath + " --- Fail because of failed compiling! ");
            }

        } catch (IOException e) {
            throw new RuntimeException(config.projectPath + " ---Error! ");
        }
    }

    private String doCleanStaticKeyworkds(String fileContent, String targetCaseName) {
        // 将static 块中的内容全部移动到mainTest函数的body中
        //        static {
        //            FuzzerUtils.init(Test0001.iArrFld, 34192);
        //        }
        // Step 1: Extract content from static blocks
        Pattern staticPattern = Pattern.compile("static\\s*\\{([^}]*)\\}");
        Matcher staticMatcher = staticPattern.matcher(fileContent);
        StringBuilder staticContent = new StringBuilder();

        while (staticMatcher.find()) {
            staticContent.append(staticMatcher.group(1)).append("\n");
        }

        // Step 2: Remove all static blocks
        fileContent = staticMatcher.replaceAll("");

        // Step 3: Insert static block content into mainTest method
        Pattern mainTestPattern = Pattern.compile("(public\\s+void\\s+mainTest\\s*\\([^)]*\\)\\s*\\{)");
        Matcher mainTestMatcher = mainTestPattern.matcher(fileContent);
        if (mainTestMatcher.find()) {
            fileContent = mainTestMatcher.replaceFirst("$1\n" + staticContent.toString());
        }

        // Step 4: Replace "ClassName." with ""
        fileContent = fileContent.replaceAll(targetCaseName + "\\.", "");

        // Step 5: Replace all "static " with ""
        fileContent = fileContent.replaceAll("\\bstatic\\s+", "");

        // Step 6: Change 'public void main' to 'public static void main'
        fileContent = fileContent.replaceAll("public\\s+void\\s+main\\(String", "public static void main(String");
        return fileContent;
    }

    private String doFixCurlyBrackets(String patchedJavaFile, ITree tree) {
        // parent is if / for / enhance for with only one children
        boolean flag = false;
        int start = -1;
        int end = -1;
        ITree parent = tree.getParent();
        int parentType = tree.getParent().getType();
        ITree parentParent = parent.getParent();
        int childrenSize = parent.getChildren().size();
        if (Checker.isBlock(parentType) && Checker.isIfStatement(parentParent.getType())) {
            flag = true;
            start = parent.getPos();
            end = parent.getEndPos();
        } else if (Checker.isForStatement(parentType) && childrenSize <= 4) {
            flag = true;
            start = tree.getPos();
            end = parent.getEndPos();
        } else if (Checker.isEnhancedForStatement(parentType) && childrenSize == 3) {
            flag = true;
            start = tree.getPos();
            end = parent.getEndPos();
        }

        if (flag) {
            patchedJavaFile = patchedJavaFile.substring(0, start) + "{\n" + patchedJavaFile.substring(start, end) + "\n}" +
                    patchedJavaFile.substring(end);
            Configuration.lineNumber += 1;
        }
        return patchedJavaFile;
    }

    private String lowLoopTimes(String fileContent) {
        String forLoopRegex =
                "for\\s*\\(\\s*(\\w+)\\s*=\\s*(\\d+)\\s*;\\s*(.*?)\\s*;\\s*((?:\\+\\+\\w+|\\w+\\+\\+|--\\w+|\\w+--|\\w+\\s*[+\\-]=\\s*\\d+))\\s*\\)";

        Pattern pattern = Pattern.compile(forLoopRegex);
        Matcher matcher = pattern.matcher(fileContent);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            int initValue = Integer.parseInt(matcher.group(2));
            String conditionStr = matcher.group(3).trim();
            String stepPart = matcher.group(4).trim();

            // 匹配条件两种形式：
            // 1. varName operator number: 如 i < 394
            // 2. number operator varName: 如 143 > i
            String varNumRegex = "(\\w+)\\s*(>|>=|<|<=)\\s*(\\d+)";
            String numVarRegex = "(\\d+)\\s*(>|>=|<|<=)\\s*(\\w+)";

            Matcher varNumMatcher = Pattern.compile(varNumRegex).matcher(conditionStr);
            Matcher numVarMatcher = Pattern.compile(numVarRegex).matcher(conditionStr);

            String condVarName;
            String operator;
            int condValue;

            if (varNumMatcher.find()) {
                // varName operator number
                condVarName = varNumMatcher.group(1);
                operator = varNumMatcher.group(2);
                condValue = Integer.parseInt(varNumMatcher.group(3));
            } else if (numVarMatcher.find()) {
                // number operator varName -> 转换为 varName 对应的反向操作
                int tempVal = Integer.parseInt(numVarMatcher.group(1));
                operator = numVarMatcher.group(2);
                String tempVar = numVarMatcher.group(3);

                // 反转 143 > i 为 i < 143
                condVarName = tempVar;
                condValue = tempVal;
                operator = reverseOperator(operator);
            } else {
                // 未匹配到期望的条件格式，直接跳过或保留原样
                matcher.appendReplacement(sb, matcher.group(0));
                continue;
            }

            // 根据步进部分判断循环方向
            // 简单判断：如果含有++或+=正数，认为递增；--或-=正数则递减
            boolean isIncrement = stepPart.contains("++") || stepPart.contains("+=");
            boolean isDecrement = stepPart.contains("--") || stepPart.contains("-=");

            // 构造新的条件，使循环只执行一次或极少次数
            String newCondition;
            int step = 3;
            if (isIncrement) {
                // 递增循环：varName < initValue+1
                newCondition = varName + " < " + (initValue + step);
            } else if (isDecrement) {
                // 递减循环：varName > initValue-1
                newCondition = varName + " > " + (initValue - step);
            } else {
                // 无法明确方向的默认处理
                if (operator.contains(">")) {
                    newCondition = varName + " > " + (initValue - step);
                } else {
                    newCondition = varName + " < " + (initValue + step);
                }
            }

            // 重构for循环
            String newForLoop = "for (" + varName + " = " + initValue + "; " + newCondition + "; " + stepPart + ")";
            matcher.appendReplacement(sb, newForLoop);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private static String reverseOperator(String op) {
        // 将 number op varName 转换成 varName 对应op number时需要反转操作符
        return switch (op) {
            case ">" -> "<";
            case ">=" -> "<=";
            case "<" -> ">";
            case "<=" -> ">=";
            default -> op;
        };
    }


    public void prepare(Configuration config) {

        this.targetCaseName = config.targetCase.substring(config.targetCase.lastIndexOf(".") + 1);

        String rootDir = Configuration.TEMP_FILES_PATH + targetCaseName + "_" + getCurrentTime() + "/";
        String tmpDir = rootDir + this.curIter++ + "/";
        configureLogging(rootDir + "log.txt");
        this.rootDir = rootDir;
        String javaTemp = tmpDir + config.targetCase.replace(".", "/") + ".java";
        this.javaTemp = javaTemp;
        File javaTempFile = new File(javaTemp);
        javaTempFile.getParentFile().mkdirs();
        // Compile original file.
        try {
            //            if (javaTempFile.exists()) javaTempFile.delete();
            //            if (classTemp.exists()) classTemp.delete();

            String fileContent = FileHelper.readFile(config.javaFilePath);
            //            fileContent = doFixCurlyBrackets(fileContent, mutationPoint.codeAstNode);
            if (config.targetCase.startsWith("Test0."))
                fileContent = doCleanStaticKeyworkds(fileContent, targetCaseName);
            // do not need to loop 10 times
            fileContent = fileContent.replace("for (int i = 0; i < 10; i++ ) {",
                    "for (int i = 0; i < 1; i++ ) {");

            //  lower loop times
            fileContent = lowLoopTimes(fileContent);

            FileHelper.outputToFile(javaTemp, fileContent, false);

            parseSuspiciousCode(config, null, null);
            getClassAndMethodName(mutationPoint.codeAstNode);

            File classTemp = new File(tmpDir + this.packageName.replace(".", "/") + "/" + targetCaseName + ".class");
            trueTestCase = this.packageName == "" ? targetCaseName : this.packageName + "." + targetCaseName;

            log.debug(config.javaFilePath + " === line " + Configuration.lineNumber);

            if (javaTempFile.length() != fileContent.length())
                parseSuspiciousCode(config, null, null);

            JDK jdk = config.jdks[0];

            String compileCmd = JDK.getJavac() + " -cp \"" + (!config.isRegression ? config.javaFuzzerRoot : jdk.getTestLibRoot())
                    + config.classPathSplitter + "libs/android.jar" + "\" -d " + tmpDir + " " + javaTemp;
            iter0Path = tmpDir;
            iter0CodeStr = this.mutationPoint.codeStr;
            String runCmd;
            if (Configuration.isRegression)
                runCmd = jdk.getJITJava(compileOnly, tmpDir, null, trueTestCase);
            else
                runCmd = jdk.getDefaultJava(compileOnly, tmpDir, iter0Path, trueTestCase);

            //            System.out.println(compileCmd);
            log.debug("compile cmd:\n" + compileCmd);
            Process p = runCmd(compileCmd);
            ShellUtils.getShellOut(p, 1, rootDir + "log.txt");
            log.debug("run cmd:\n" + runCmd);
            if (classTemp.exists()) {
                log.debug("Successfully compile original file === " + javaTemp);
                Process p2 = runCmd(runCmd);

                ShellUtils.getShellOut(p2, 1, tmpDir + "xcomp1.log");
                File xcomp = new File(tmpDir + "xcomp1.log");
                String iter0Content;
                if (xcomp.exists()) {
                    iter0Content = FileHelper.readFile(xcomp).strip();
                } else {
                    log.error("\ntimeout to run the command:\n" + runCmd);
                    throw new RuntimeException("Fail to run original file === " + javaTemp);
                }

                if (iter0Content.startsWith("Error") || iter0Content.startsWith("Exception")) {
                    log.error("Fail to run original file === " + runCmd);
                    //                    throw new RuntimeException("Fail to run original file === " + javaTemp);
                }

                if (Configuration.enableProfileGuidance)
                    parentVector = ProfileDeltaRegex.getProfileVector(tmpDir + "/vm.log");

            } else {
                log.error("compile\n" + compileCmd + "\nrun:\n" + runCmd);
                throw new RuntimeException("Fail to compile and run original file === " + javaTemp);
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.debug(config.projectPath + " --- fail because of javac exception! ");
        }

    }

    public List<Integer> readAllNodeTypes(ITree suspCodeAstNode) {
        List<Integer> nodeTypes = new ArrayList<>();
        nodeTypes.add(suspCodeAstNode.getType());
        List<ITree> children = suspCodeAstNode.getChildren();
        for (ITree child : children) {
            int childType = child.getType();
            if (Checker.isFieldDeclaration(childType) || Checker.isMethodDeclaration(childType) || Checker.isTypeDeclaration(childType) || Checker.isStatement(childType))
                break;
            nodeTypes.addAll(readAllNodeTypes(child));
        }
        return nodeTypes;
    }

    private String getCurrentTime() {
        // 获取当前日期和时间
        LocalDateTime now = LocalDateTime.now();

        // 创建一个DateTimeFormatter对象，指定格式为月_日_时_分
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM_dd_HH_mm_ss");

        // 使用formatter格式化当前时间
        return now.format(formatter);
    }

    public void parseSuspiciousCode(Configuration config, String targetClazz, String targetMethod) {
        var javaFile = new File(this.javaTemp == null ? config.javaFilePath : this.javaTemp);
        ITree rootTree = new ASTGenerator().generateTreeForJavaFile(javaFile, ASTGenerator.TokenType.EXP_JDT);
        List<ITree> statementNodes = new ArrayList<>();
        CompilationUnit unit = new Scheduler.MyUnit().createCompilationUnit(javaFile);
        Pair<Integer, Integer> targetRegion = getMethodRegion(rootTree, unit, targetClazz, targetMethod);
        identifySuspiciousCodeAst(rootTree, statementNodes, unit);
        if (statementNodes.isEmpty()) throw new RuntimeException("No suspicious code found!");
        mutationPoint = null;
        selectMutationPoint(this.config, statementNodes, unit, targetRegion);
        if (mutationPoint == null) {
            log.error("No mutation point to select === " + config.javaFilePath + " line = " + Configuration.lineNumber);
        }
    }

    private Pair<Integer, Integer> getMethodRegion(ITree rootTree, CompilationUnit unit,
                                                   String targetClazz, String targetMethod) {
        if (targetClazz == null) return null;
        List<ITree> children = rootTree.getChildren();
        for (ITree child : children) {
            if (Checker.isTypeDeclaration(child.getType())) {
                String className = ContextReader.readClassName(child);
                if (className.equals(targetClazz)) {
                    List<ITree> typeChildren = child.getChildren();
                    for (ITree typeChild : typeChildren) {
                        if (Checker.isMethodDeclaration(typeChild.getType())) {
                            String methodName = null;
                            String mLabel = typeChild.getLabel();
                            // private, @@int, MethodName:test, @@Argus:int+a+int[]+b+
                            String[] split = mLabel.split(", ");
                            for (String s : split) {
                                if (s.startsWith("MethodName:")) {
                                    methodName = s.substring(11);
                                    break;
                                }
                            }
                            if (methodName.equals(targetMethod)) {
                                int methodStart = unit.getLineNumber(typeChild.getPos());
                                int methodEnd = unit.getLineNumber(typeChild.getEndPos());
                                return new Pair<>(methodStart, methodEnd);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public void runtestCase(String tmpDir) {
        try {
            for (int i = 0; i < config.jdks.length; i++) {
                JDK jdk = config.jdks[i];
                String runCmd;
                if (jdk.isART) {
                    runCmd = jdk.processART(tmpDir, iter0Path, packageName, trueTestCase);
                    //                        System.out.println(runCmd);
                    //                        continue;
                } else {
                    if (Configuration.isRegression)
                        runCmd = jdk.getJITJava(compileOnly, tmpDir, iter0Path, trueTestCase);
                    else
                        runCmd = jdk.getDefaultJava(compileOnly, tmpDir, iter0Path, trueTestCase);
                }

                log.debug("running with command: \n" + runCmd);
                Process p = runCmd(runCmd);
                ShellUtils.getShellOut(p, 2, tmpDir + "xcomp" + (i + 1) + ".log");
            }

            //                if (!Configuration.isRegression) {
            //                    String runCmd = config.jdks[0].processProguard(tmpDir, iter0Path, packageName, trueTestCase);
            //                    log.debug("running with command: \n" + runCmd);
            //                    Process p2 = runCmd(runCmd);
            //                    ShellUtils.getShellOut(p2, 2, tmpDir + "xcomp" + (config.jdks.length + 1) + ".log");
            //                }

            File xcomLog = new File(tmpDir + "xcomp1.log");

            // for (int i = 1; i < config.jdks.length; i++) {
            //     File xcomLogx = new File(tmpDir + "xcomp" + (i + 1) + ".log");
            //     if (xcomLogx.exists()) {
            //         if (!compareFilesIgnoringLines(xcomLog, xcomLogx)) {
            //             log.error("inconsistant results between JDKs: " + tmpDir);
            //         }
            //     }
            //                    File xcomLogj9 = new File(tmpDir + "xcompJ" + (i + 1) + ".log");
            //                    if (xcomLogj9.exists()) {
            //                        if (!compareFilesIgnoringLines(xcomLog, xcomLogj9)) {
            //                            log.error("inconsistant results between JDKs: " + tmpDir);
            //                        }
            //                    }
            // }

            //                String option = String.format("-XX:+LogVMOutput -XX:LogFile=%s/vm.log -XX:-DisplayVMOutput "
            //                        + " -XX:+UnlockDiagnosticVMOptions %s", tmpDir, profileFlags);
            //                String runCmd1 = config.java + (config.isCompiler ? compileOnly + option : " ") + " -cp \"" + tmpDir
            //                        + config.classPathSplitter + iter0Path + "\" "
            //                        + (config.test_jdk == null ? "" : config.test_jdk) + trueTestCase;
            //                log.debug("running with -Xcomp: \n" + runCmd1);
            //                long startTimestamp = System.currentTimeMillis();
            //                Process p2 = runCmd(runCmd1);
            //                ShellUtils.getShellOut(p2, 2, tmpDir + "xcomp.log");
            //
            //                File xcomLog = new File(tmpDir + "xcomp.log");
            //                File vmLog = new File(tmpDir + "vm.log");
            //                if (!xcomLog.exists()) {
            //                    throw new RuntimeException(" Timeout testcase " + tmpDir);
            //                }
            //
            //                if (Configuration.secondJDK != null) {
            //                    String runCmd2 = Configuration.secondJDK + "/java " + " -XX:-DisplayVMOutput -cp \"" + tmpDir
            //                            + config.classPathSplitter + iter0Path + "\" "
            //                            + (config.test_jdk == null ? "" : config.test_jdk)
            //                            + (Configuration.secondJDK.contains("openj9") ? " -Xjit:count=0 " : " -Xcomp ") + trueTestCase;
            //                    log.debug("running with second jdk: \n" + runCmd2);
            //                    Process p3 = runCmd(runCmd2);
            //                    ShellUtils.getShellOut(p3, 2, tmpDir + "xcomp2.log");
            //                    File xcomLog2 = new File(tmpDir + "xcomp2.log");
            //                    if (xcomLog2.exists()) {
            //                        if (!compareFilesIgnoringLines(xcomLog, xcomLog2)) {
            //                            log.error("inconsistant results between JDKs: " + tmpDir);
            //                        }
            //                    }
            //
            //                }
            //                }

        } catch (Exception e) {
            log.error(config.projectPath + e.getMessage());
            throw new RuntimeException(config.projectPath + e.getMessage());
        } finally {
            String jtregTest = config.jtregTestsRoot + config.targetCase.replace(".", "/") + ".java";
            String jtregTest_bk = config.jtregTestsRoot_bk + config.targetCase.replace(".", "/") + ".java";
            if (usejTreg) {
                try {
                    // copy javaTemp to jtregTestsRoot
                    FileUtils.copyFile(javaTemp, jtregTest);
                    String jtregJDK = config.JDKRoot.substring(0, config.JDKRoot.indexOf("bin"));
                    log.debug("running jtreg with addtional options");
                    String runJtregCmd2 = config.jtreg + " -jdk:" + jtregJDK + " -verbose:summary -w " + tmpDir + "jtreg2" + " -r "
                            + tmpDir + "result2 " + "-vmoptions:\"" + "\" -timeout:4 " + jtregTest;
                    log.debug("running jtreg: \n" + runJtregCmd2);

                    Process p4 = runCmd(runJtregCmd2);
                    ShellUtils.getShellOut(p4, 2, tmpDir + "jtreg_random.log");

                    File summary = new File(tmpDir + "result2/text/summary.txt");
                    File jtregLog = new File(tmpDir + "jtreg_random.log");

                    if (!summary.exists() && !jtregLog.exists()) {
                        usejTreg = false;
                        log.debug("jtreg timeout! " + tmpDir);
                    } else if (jtregLog.exists()) {
                        String logContent = FileHelper.readFile(jtregLog);
                        if (!logContent.contains("Test results") || logContent.contains("Not a test")
                                || (summary.exists() && FileHelper.readFile(summary).contains("timeout"))) {
                            usejTreg = false;
                            log.debug("jtreg timeout! " + tmpDir);
                        }
                    }
                    File xcomLog = new File(tmpDir + "xcomp.log");
                    String logContent = FileHelper.readFile(xcomLog);
                    if (!usejTreg && (!xcomLog.exists() || logContent.contains("Exception"))) {
                        throw new RuntimeException("jtreg timeout! " + tmpDir);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage());
                    throw new RuntimeException(config.projectPath + " ---Fixer: runing fail because of java exception! ");
                } finally {
                    // recovery java file
                    FileUtils.copyFile(jtregTest_bk, jtregTest);
                }
            }
        }

    }

    private String readSuspiciousCode(String javaFileContent, ITree suspiciousCodeAstNode) {
        int startPos = suspiciousCodeAstNode.getPos();
        int endPos = startPos + suspiciousCodeAstNode.getLength();
        return javaFileContent.substring(startPos, endPos);
    }

    private String readPackageName(String label) {
        // 35@@@test
        // * @bug 6855215
        // * @summary Calculation error (NaN) after about 1500 calculations
        // * @run main/othervm -Xbatch -XX:UseSSE=0 compiler.c1.Test6855215
        // */
        // package compiler.c1
        // get package name from label
        String[] lines = label.split("\n");
        if (lines.length == 1) {
            if (label.startsWith("package")) {
                return label.substring(label.indexOf("package") + 8);
            }
            return label;
        }
        String packageName = "";
        for (String line : lines) {
            if (line.startsWith("package")) {
                packageName = line.substring(line.indexOf("package") + 8);
                break;
            }
        }
        return packageName;
    }

    private void identifySuspiciousCodeAst(ITree tree, List<ITree> statementNode, CompilationUnit unit) {
        List<ITree> children = tree.getChildren();
        //        if (Checker.isBlock(tree.getType())) {
        //            if (("ThenBody".equals(tree.getLabel()) || "ElseBody".equals(tree.getLabel()))
        //                    && tree.getChildren().size() == 1)
        //                return;
        //        }
        //        String returnType = readMethodReturnType(tree);
        //        if (returnType != null && returnType.equals("=CONSTRUCTOR="))
        //            return;

        for (ITree child : children) {
            int childType = child.getType();
            if (Checker.isPackageDeclaration(childType)) packageName = readPackageName(child.getLabel());
            if (Checker.isMethodDeclaration(childType)) {
                String label = child.getLabel();
                if (!this.isExecutable) this.isExecutable = label.contains("main");
            }
            if (Checker.isInitializer(childType)) continue;
            if (Checker.isStatement3(childType)) {
                String label = child.getLabel();

                if (label.startsWith("Class<?>")) continue;

                if (label.contains("System.")) continue;

                if (label.contains("print")) continue;

                if (Checker.isReturnStatement(childType)) continue;

                if (label.contains("->")) continue;

                if (!inMethod(child)) continue;

                if (skipArrayInitializer(child)) continue;

                if (Checker.isSuperConstructorInvocation(childType)) continue;

                // skip simple prefix/postfix expression. ++i; i++;
                if ((Checker.isExpressionStatement(childType) || Checker.isVariableDeclarationStatement(childType)) && (Checker.isPrefixExpression(child.getChild(0).getType()) || Checker.isPostfixExpression(child.getChild(0).getType())))
                    continue;

                //                if (!config.isRegression && inSwitch(child)) continue;

                // only declare variable. int x;
                if (Checker.isVariableDeclarationStatement(childType) && child.getChild(1).getChildren().size() <= 1)
                    continue;

                // only simply declare variable. int x = 1;
                if (Checker.isVariableDeclarationStatement(childType)) {
                    ITree child1 = child.getChild(1);
                    if (child1.getChild(1).getChildren().isEmpty()) continue;
                }

                // skip simple assignment. x = 1;
                if (Checker.isExpressionStatement(childType) && Checker.isAssignment(child.getChild(0).getType())) {
                    ITree right = child.getChild(0).getChild(2);
                    if (right.getChildren().isEmpty()) continue;
                }

                //                    || (Checker.withBlockStatement2(tree.getType()) && child == children.get(0))) {
                // aviod no statement to select
                //                int weight = 1;
                //                int weight = sumStatementWeight(child);
                //                if (weight == 0) weight = 1;
                //                else weight += 2;
                //                Pair<ITree, Integer> pair = new Pair<>(child, weight);
                if (!statementNode.contains(child)) {
                    int startPosition = child.getPos();
                    int endPosition = startPosition + child.getLength();
                    int startLine = unit.getLineNumber(startPosition);
                    int endLine = unit.getLineNumber(endPosition);
                    if (startLine == endLine) statementNode.add(child);
                }
            }
            identifySuspiciousCodeAst(child, statementNode, unit);
        }
    }

    private boolean inSwitch(ITree tree) {
        ITree parent = tree.getParent();
        while (parent != null) {
            if (Checker.isSwitchCase(parent.getType()) || Checker.isSwitchStatement(parent.getType())) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private boolean skipArrayInitializer(ITree tree) {
        Queue<ITree> queue = new LinkedList<>();
        queue.offer(tree);
        while (!queue.isEmpty()) {
            ITree node = queue.poll();
            if (Checker.isArrayInitializer(node.getType())) return true;
            queue.addAll(node.getChildren());
        }
        return false;
    }

    private int sumStatementWeight(ITree tree) {
        int w = 0;
        if (isInLoop(tree)) w += 1;
        w += sumBinaryOperatorAndArrayAccessAndFiledAccess(tree);
        return w;
    }

    private int sumBinaryOperatorAndArrayAccessAndFiledAccess(ITree tree) {
        Queue<ITree> queue = new LinkedList<>();
        boolean[] flag = new boolean[3];
        queue.offer(tree);
        while (!queue.isEmpty()) {
            ITree node = queue.poll();
            int type = node.getType();
            switch (type) {
                case -1 -> {
                    if (!Objects.equals(node.getLabel(), "=") && Checker.isInfixExpression(node.getParent().getType()))
                        flag[0] = true;
                }
                case 2, 3, 4 -> flag[1] = true;
                case 22, 40, 47 -> flag[2] = true;
            }
            for (var child : node.getChildren())
                queue.offer(child);
        }
        int sum = 0;
        for (var b : flag)
            if (b) sum++;
        return sum;
    }

    private boolean isInLoop(ITree tree) {
        boolean inLoop = false;
        ITree parent = tree.getParent();
        while (parent != null) {
            int parentType = parent.getType();
            if (Checker.isForStatement(parentType) || Checker.isEnhancedForStatement(parentType) || Checker.isWhileStatement(parentType) || Checker.isDoStatement(parentType)) {
                inLoop = true;
                break;
            }
            parent = parent.getParent();
        }
        return inLoop;
    }

    private boolean inMethod(ITree tree) {
        ITree parent = tree.getParent();
        while (parent != null) {
            int parentType = parent.getType();
            if (Checker.isMethodDeclaration(parentType)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isRequiredAstNode(ITree tree) {
        int astNodeType = tree.getType();
        return Checker.isStatement(astNodeType) || Checker.isFieldDeclaration(astNodeType) || Checker.isMethodDeclaration(astNodeType) || Checker.isTypeDeclaration(astNodeType);
    }

    private static class MyUnit {

        public CompilationUnit createCompilationUnit(File javaFile) {
            char[] javaCode = readFileToCharArray(javaFile);
            ASTParser parser = createASTParser(javaCode);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);

            return (CompilationUnit) parser.createAST(null);
        }

        private ASTParser createASTParser(char[] javaCode) {
            ASTParser parser = ASTParser.newParser(AST.JLS22);
            parser.setSource(javaCode);

            return parser;
        }

        private char[] readFileToCharArray(File javaFile) {
            StringBuilder fileData = new StringBuilder();
            BufferedReader br = null;

            char[] buf = new char[10];
            int numRead;
            try {
                FileReader fileReader = new FileReader(javaFile);
                br = new BufferedReader(fileReader);
                while ((numRead = br.read(buf)) != -1) {
                    String readData = String.valueOf(buf, 0, numRead);
                    fileData.append(readData);
                    buf = new char[1024];
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (br != null) {
                        br.close();
                        br = null;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (!fileData.isEmpty()) return fileData.toString().toCharArray();
            else return new char[0];
        }
    }


}
