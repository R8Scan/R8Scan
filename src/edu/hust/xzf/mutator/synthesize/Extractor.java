package edu.hust.xzf.mutator.synthesize;

import ch.qos.logback.classic.Level;
import edu.hust.xzf.mutator.config.Configuration;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Extractor {
    private static final Logger log = LoggerFactory.getLogger(Extractor.class);
    public static int classCounter = 71814;
    String outputPath = "cbs_top_4";

    /*
    要求：
    1. 只接受基本类型（包括数组）作为参数，如果参数涉及user-defined的引用类型，则follow数据流移除所有相关的语句
    2. 只返回基本类型，为了结果可观测。如果函数是void，则先清除所有的return，并在结尾添加一个return语句，返回checksum？
    3. 不包含任何user-defined的引用，如类、方法、变量等，如果有，也follow数据流移除所有相关的语句；除非是基本类型的全局常量，则可以引入进来
    4. 容忍side effects。但输出必须是确定性的，即没有random, time, date等不确定性的操作
     */
    Path scannedFilesPath = Path.of("scanned_files.txt");

    public static void main(String[] args) {
        Configuration.isWin = System.getProperty("os.name").toLowerCase().contains("windows");

        Extractor extractor = new Extractor();
        // extractor.debug();
        extractor.run();
    }

    public static void findSourceRoots(Path projectRoot, List<Path> sourceRoots) {
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // 检查是否是常见的源码目录
                    if (dir.endsWith("src/main/java") || dir.endsWith("src/java")) {
                        sourceRoots.add(dir);
                        return FileVisitResult.SKIP_SUBTREE; // 找到后跳过子目录
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    System.err.println("Error accessing file: " + file + " - " + exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isLibraryType(CtTypeReference<?> typeRef) {
        if (typeRef == null)
            return false;
        // 检查类型是否来自 Java 库或 Android SDK，或者是基本类型，或者是基本数组类型，或者是null类型
        String qualifiedName = typeRef.getQualifiedName();
        String lowerName = qualifiedName.toLowerCase();
        if (lowerName.contains("random"))
            return false;
        return qualifiedName.startsWith("java.")
                || qualifiedName.startsWith("android.")
                || typeRef.isPrimitive()
                || (typeRef instanceof CtArrayTypeReference<?> arrayTypeReference && arrayTypeReference.getComponentType().isPrimitive())
                || qualifiedName.equals("<nulltype>")
                || qualifiedName.equals("?")
                || typeRef instanceof CtTypeParameterReference;
    }

    private static boolean isLibraryFunctionCall(CtStatement statement) {
        return statement.getElements(new TypeFilter<>(CtInvocation.class)).stream()
                .allMatch(invocation -> isLibraryType(invocation.getExecutable().getDeclaringType()));
    }

    private void debug() {
        Extractor extractor = new Extractor();
        extractor.extract("debug/Test.java");
    }

    private void setLogLevel(Level level) {
        Logger logger = LoggerFactory.getLogger("spoon");
        ((ch.qos.logback.classic.Logger) logger).setLevel(level);
    }

    private void run() {
        setLogLevel(Level.ERROR);
        ArrayList<Path> sourceRoots = new ArrayList<>();

        // findSourceRoots(Path.of("D:\\repository\\CollectProject"), sourceRoots);
        // findSourceRoots(Path.of("D:\\topJavaProjects"), sourceRoots);
        findSourceRoots(Path.of("D:\\topJavaProjects2"), sourceRoots);
        ExecutorService executorService = Executors.newFixedThreadPool(16);

        Set<String> scannedFiles = Collections.synchronizedSet(new HashSet<>());

        // Load previously scanned files
        if (Files.exists(scannedFilesPath)) {
            try {
                scannedFiles.addAll(Files.readAllLines(scannedFilesPath));
            } catch (IOException e) {
                log.error("Error reading scanned files", e);
            }
        }

        for (Path sourceRoot : sourceRoots) {
            if (scannedFiles.contains(sourceRoot.toString())) {
                continue;
            }
            executorService.submit(() -> {
                Extractor extractor = new Extractor();
                extractor.extract(sourceRoot.toString());
            });
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.MINUTES)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private boolean isValidMethod(CtMethod<?> method) {
        // 检查返回类型是否为有效的Java标准库或Android SDK类型
        if (!isValidType(method.getType())) {
            return false;
        }

        // 检查参数类型是否都为有效类型
        for (CtParameter<?> paramType : method.getParameters()) {
            if (!isValidType(paramType.getType())) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidType(CtTypeReference<?> type) {
        // 获取类型的包名
        String qualifiedName = type.getQualifiedName();

        // 检查类型是否为基本类型
        if (type.isPrimitive()) {
            return true;
        }

        // 允许的Java库包和Android SDK包，可以根据需求进行扩展
        String[] allowedPackages = {"java.", "javax.", "android.", "androidx.", "org.json.", "kotlin."};

        // 检查是否属于允许的包
        for (String allowedPackage : allowedPackages) {
            if (qualifiedName.startsWith(allowedPackage)) {
                return true;
            }
        }

        // 如果不在允许的包中，返回false
        return false;
    }

    private void extract(String projectPath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(scannedFilesPath.toFile(), true))) {
            // add the scanned file to the file
            writer.println(projectPath);
        } catch (IOException e) {
            return;
        }

        Launcher launcher = new Launcher();
        //        launcher.addInputResource("D:\\repository\\CollectProject\\AaltoXml_9_buggy\\src\\main\\java"); // 添加项目路径
        launcher.addInputResource(projectPath); // 添加项目路径
        launcher.getEnvironment().setSourceClasspath(new String[]{
                "D:/java_tools/jdk-1.8/jre/lib/rt.jar",
                // "D:\\repository\\r8\\third_party\\openjdk\\openjdk-rt-1.8\\rt.jar",
                "D:/repository/JVM-Tesing-by-Anti-Optimization/libs/android.jar",
        }); // 设置源码路径

        launcher.buildModel();

        CtModel model = launcher.getModel();
        Set<Pair<CtMethod<?>, CtMethod<?>>> pairs = new HashSet<>();

        // 遍历项目中的每个类
        for (CtType<?> ctType : model.getAllTypes()) {
            // 遍历每个类中的方法
            for (CtMethod<?> method : ctType.getMethods()) {
                // 检查方法形参和返回值是否符合要求
                //                if (isValidMethod(method)) {
                Set<String> validVariables = new HashSet<>();
                method.getParameters().forEach(param -> validVariables.add(param.getSimpleName()));
                CtMethod<?> extractedMethod = extractValidFragment(method, validVariables);
                if (extractedMethod != null) {
                    pairs.add(Pair.of(method, extractedMethod));
                }
                //                }
            }
        }

        // 保存提取出来的新方
        saveExtractedMethods(pairs);
    }

    private boolean isGeneric(String type) {
        return type.equals("T") || type.equals("E") || type.equals("K") || type.equals("V") || type.equals("A") || type.equals("S");
    }

    private List<CtParameter<?>> replaceProjectSpecificParameters(List<CtParameter<?>> parameters) {
        List<CtParameter<?>> newParameters = new ArrayList<>();
        for (CtParameter<?> param : parameters) {
            CtTypeReference<?> paramType = param.getType();
            CtParameter<?> newParam = param.getFactory().createParameter();
            newParam.setSimpleName(param.getSimpleName());

            if (isGeneric(paramType.getSimpleName())) {
                newParam.setType(param.getFactory().Type().objectType());
            } else if (!isLibraryType(paramType)) {
                continue;
                //                newParameters.add(newParam);
            } else {
                newParam.setType(paramType);
                //                newParameters.add(param);
            }

            // 如果泛型实参类型，替换为Object
            var actualTypeArguments = paramType.getActualTypeArguments();
            if (!actualTypeArguments.isEmpty()) {
                for (int i = 0; i < actualTypeArguments.size(); i++) {
                    var actualTypeArgument = actualTypeArguments.get(i);
                    if (actualTypeArgument instanceof CtWildcardReference || !isLibraryType(actualTypeArgument))
                        actualTypeArguments.set(i, param.getFactory().Type().objectType());
                }
            }

            newParameters.add(newParam);
        }
        return newParameters;
    }

    private void replaceGenericsWithObject(CtStatement statement) {
        statement.getElements(new TypeFilter<>(CtTypeReference.class))
                .stream().filter(typeRef -> !typeRef.getActualTypeArguments().isEmpty())
                .forEach(typeRef -> {
                    for (int i = 0; i < typeRef.getActualTypeArguments().size(); i++) {
                        CtTypeReference<?> actualTypeArgument = typeRef.getActualTypeArguments().get(i);
                        if (actualTypeArgument instanceof CtWildcardReference || !isLibraryType(actualTypeArgument)) {
                            typeRef.getActualTypeArguments().set(i, typeRef.getFactory().Type().objectType());
                        }
                    }
                });
    }

    private CtMethod<?> extractValidFragment(CtMethod<?> method, Set<String> validVariables) {
        if (method.getBody() == null) {
            return null;
        }

        CtMethod<?> newMethod = method.getFactory().createMethod();
        var factory = method.getFactory();
        newMethod.setSimpleName("method");
        newMethod.setType(method.getType());
        newMethod.setParameters(replaceProjectSpecificParameters(method.getParameters()));
        newMethod.addModifier(ModifierKind.PUBLIC);
        newMethod.addModifier(ModifierKind.STATIC);
        newMethod.setPosition(method.getPosition());

        boolean setVoid = false;
        var returnType = method.getType();
        if (returnType.isPrimitive()) {
            newMethod.setType(returnType);
        } else {
            setVoid = true;
            newMethod.setType(factory.Type().voidPrimitiveType());
        }
        newMethod.setThrownTypes(Set.of(factory.Type().createReference(Exception.class)));
        newMethod.setBody(factory.createBlock());
        CtBlock<?> newMethodBody = newMethod.getBody();

        handleBlock(method.getBody(), newMethodBody, validVariables);

        if (method.getBody() == null || newMethodBody.getStatements().isEmpty()) {
            return null;
        }

        if (setVoid) {
            for (CtStatement stmt : newMethodBody.getStatements()) {
                if (stmt instanceof CtReturn) {
                    stmt.replace(factory.createCodeSnippetStatement(""));
                }
            }
            newMethodBody.getElements(new TypeFilter<>(CtReturn.class)).forEach(returnStatement ->
                    returnStatement.replace(method.getFactory().createCodeSnippetStatement("")));
        }

        if (isTrivialMethod(newMethodBody)) {
            return null;
        }

        return newMethod;
    }

    private void handleIfStatement(CtIf ctIf, CtBlock<?> newMethodBody, Set<String> validVariables) {
        boolean canAddStmt = false;
        CtIf newIfStmt = ctIf.getFactory().createIf();
        // newIfStmt.setParent(newMethodBody);
        CtExpression<Boolean> condition = ctIf.getCondition();
        if (containsProjectSpecificReference(condition) || containUndefinedVariable(condition, newMethodBody, validVariables)) {
            newIfStmt.setCondition(ctIf.getFactory().createLiteral(true));
        } else {
            newIfStmt.setCondition(condition);
            canAddStmt = true;
        }
        // do not forget the 模式变量
        condition.getElements(new TypeFilter<>(CtLocalVariable.class))
                .forEach(var -> validVariables.add(var.getSimpleName()));

        CtBlock<?> thenBlock = ctIf.getThenStatement();
        CtBlock<?> elseBlock = ctIf.getElseStatement();

        if (thenBlock != null) {
            CtBlock<?> newThenStmp = thenBlock.getFactory().createBlock();
            // newThenStmp.setParent(newIfStmt);
            handleBlock(thenBlock, newThenStmp, validVariables);
            if (!newThenStmp.getStatements().isEmpty()) canAddStmt = true;
            newIfStmt.setThenStatement(newThenStmp);
        }
        if (elseBlock != null) {
            CtBlock<?> newElseStmp = elseBlock.getFactory().createBlock();
            // newElseStmp.setParent(newIfStmt);
            if (!newElseStmp.getStatements().isEmpty()) canAddStmt = true;
            handleBlock(elseBlock, newElseStmp, validVariables);
            newIfStmt.setElseStatement(newElseStmp);
        }
        if (canAddStmt)
            newMethodBody.addStatement(newIfStmt);
    }

    private void handleLoopStatement(CtStatement loopStmt, CtBlock<?> newMethodBody, Set<String> validVariables) {
        if (loopStmt instanceof CtFor ctFor) {
            // only if all the initial, condition, updates are valid, we can add the loop
            // initial
            boolean validInitial = false;
            CtFor newFor = ctFor.getFactory().createFor();

            List<CtStatement> newInitStmts = new ArrayList<>();
            List<CtStatement> initStmts = ctFor.getForInit();
            initStmts.forEach(stmt -> {
                if (checkValidSimpleStatement(stmt, newMethodBody, validVariables))
                    newInitStmts.add(stmt);
            });
            if (newInitStmts.size() == initStmts.size()) {
                validInitial = true;
            }
            newFor.setForInit(newInitStmts);

            // condition
            boolean validCondition = false;
            CtExpression<Boolean> loopCondition = ctFor.getExpression();
            if (!(containsProjectSpecificReference(loopCondition) || containUndefinedVariable(loopCondition, newMethodBody, validVariables)))
                validCondition = true;
            newFor.setExpression(loopCondition);

            // updates
            boolean validUpdate = false;
            List<CtStatement> newUpdateStmts = new ArrayList<>();
            List<CtStatement> updateStmts = ctFor.getForUpdate();
            updateStmts.forEach(stmt -> {
                if (checkValidSimpleStatement(stmt, newMethodBody, validVariables)) newUpdateStmts.add(stmt);
            });
            if (newUpdateStmts.size() == updateStmts.size()) {
                validUpdate = true;
            }
            newFor.setForUpdate(newUpdateStmts);
            if (validInitial && validCondition && validUpdate) {
                CtBlock<?> newLoopBody = ctFor.getFactory().createBlock();
                handleBlock((CtBlock<?>) ctFor.getBody(), newLoopBody, validVariables);
                newFor.setBody(newLoopBody);
                if (!newLoopBody.getStatements().isEmpty()) {
                    if (ctFor.getLabel() != null)
                        newFor.setLabel(ctFor.getLabel());
                    newMethodBody.addStatement(newFor);
                }
            }
        } else if (loopStmt instanceof CtForEach ctForEach) {
            // Handling CtForEach loop
            CtForEach newForEach = ctForEach.getFactory().createForEach();
            // newForEach.setParent(newMethodBody);

            // Variable
            boolean validVariable = false;
            CtLocalVariable<?> loopVariable = ctForEach.getVariable();
            if (!containsProjectSpecificReference(loopVariable)) {
                validVariable = true;
            }
            validVariables.add(loopVariable.getSimpleName());
            newForEach.setVariable(loopVariable);

            // Expression
            boolean validExpression = false;
            CtExpression<?> loopExpression = ctForEach.getExpression();
            if (!(containsProjectSpecificReference(loopExpression)
                    || containUndefinedVariable(loopExpression, newMethodBody, validVariables))) {
                validExpression = true;
            }
            newForEach.setExpression(loopExpression);

            if (validVariable && validExpression) {
                CtBlock<?> newLoopBody = ctForEach.getFactory().createBlock();
                // newLoopBody.setParent(newForEach);
                handleBlock((CtBlock<?>) ctForEach.getBody(), newLoopBody, validVariables);
                newForEach.setBody(newLoopBody);
                if (!newLoopBody.getStatements().isEmpty()) {
                    if (ctForEach.getLabel() != null)
                        ctForEach.setLabel(ctForEach.getLabel());
                    newMethodBody.addStatement(newForEach);
                }
            }
        } else if (loopStmt instanceof CtWhile ctWhile) {
            // Handling CtWhile loop
            CtWhile newWhile = ctWhile.getFactory().createWhile();
            // newWhile.setParent(newMethodBody);

            // Condition
            boolean validCondition = false;
            CtExpression<Boolean> loopCondition = ctWhile.getLoopingExpression();
            if (!(containsProjectSpecificReference(loopCondition)
                    || containUndefinedVariable(loopCondition, newMethodBody, validVariables))) {
                validCondition = true;
            }
            newWhile.setLoopingExpression(loopCondition);

            if (validCondition) {
                CtBlock<?> newLoopBody = ctWhile.getFactory().createBlock();
                // newLoopBody.setParent(newWhile);
                handleBlock((CtBlock<?>) ctWhile.getBody(), newLoopBody, validVariables);
                newWhile.setBody(newLoopBody);
                if (!newLoopBody.getStatements().isEmpty()) {
                    if (ctWhile.getLabel() != null)
                        newWhile.setLabel(ctWhile.getLabel());
                    newMethodBody.addStatement(newWhile);
                }
            }
        } else if (loopStmt instanceof CtDo ctDo) {
            // Handling CtDo loop
            CtDo newDo = ctDo.getFactory().createDo();
            // newDo.setParent(newMethodBody);

            // Condition
            boolean validCondition = false;
            CtExpression<Boolean> loopCondition = ctDo.getLoopingExpression();
            if (!(containsProjectSpecificReference(loopCondition)
                    || containUndefinedVariable(loopCondition, newMethodBody, validVariables))) {
                validCondition = true;
            }
            newDo.setLoopingExpression(loopCondition);

            if (validCondition) {
                CtBlock<?> newLoopBody = ctDo.getFactory().createBlock();
                // newLoopBody.setParent(newDo);
                handleBlock((CtBlock<?>) ctDo.getBody(), newLoopBody, validVariables);
                newDo.setBody(newLoopBody);
                if (!newLoopBody.getStatements().isEmpty()) {
                    if (ctDo.getLabel() != null)
                        newDo.setLabel(ctDo.getLabel());
                    newMethodBody.addStatement(newDo);
                }
            }
        }

    }


    private void handleTryStatement(CtTry ctTry, CtBlock<?> newMethodBody, Set<String> validVariables) {
        boolean canAddStmt = false;
        // initial
        CtTry newTry = ctTry.getFactory().createTry();
        CtBlock<?> tryBlock = ctTry.getBody();
        CtBlock<?> newTryBlock = ctTry.getFactory().createBlock();

        // do not forget the resource
        if (ctTry instanceof CtTryWithResource ctTryWithResource) {
            newTry = ctTryWithResource.getFactory().createTryWithResource();
            List<CtResource<?>> resources = ctTryWithResource.getResources();
            List<CtResource<?>> newResources = new ArrayList<>();
            boolean validResource = true;
            for (CtResource<?> resource : resources) {
                if (containsProjectSpecificReference((CtCodeElement) resource)) {
                    validResource = false;
                    break;
                }
                newResources.add(resource);
            }
            if (validResource) {
                ((CtTryWithResource) newTry).setResources(newResources);
            } else {
                return;
            }
        }

        // process try body
        handleBlock(tryBlock, newTryBlock, validVariables);
        if (!newTryBlock.getStatements().isEmpty()) canAddStmt = true;
        newTry.setBody(newTryBlock);

        // process catchers
        for (CtCatch ctCatch : ctTry.getCatchers()) {
            CtCatch newCtCache = ctCatch.getFactory().createCatch();
            // newCtCache.setParent(newTry);

            // handle catch parameter
            CtCatchVariable catchParameter = ctCatch.getParameter();
            CtCatchVariable newCatchParameter = catchParameter.getFactory().createCatchVariable();
            // newCatchParameter.setParent(newCtCache);
            newCatchParameter.setSimpleName(catchParameter.getSimpleName());
            validVariables.add(catchParameter.getSimpleName());

            newCatchParameter.setType(catchParameter.getFactory().Type().createReference(Exception.class));

            // handle catch block
            CtBlock<?> newCatchBody = ctCatch.getFactory().createBlock();
            // newCatchBody.setParent(newCtCache);
            handleBlock(ctCatch.getBody(), newCatchBody, validVariables);
            if (!newCatchBody.getStatements().isEmpty()) canAddStmt = true;
            newCtCache.setParameter(newCatchParameter);
            newCtCache.setBody(newCatchBody);
            newTry.addCatcher(newCtCache);
        }


        // process finally
        CtBlock<?> finallyBlock = ctTry.getFinalizer();
        if (finallyBlock != null) {
            CtBlock<?> newFinallyBlock = ctTry.getFactory().createBlock();
            // newFinallyBlock.setParent(newTry);
            handleBlock(finallyBlock, newFinallyBlock, validVariables);
            if (!newFinallyBlock.getStatements().isEmpty()) canAddStmt = true;
            newTry.setFinalizer(newFinallyBlock);
        }

        if (canAddStmt)
            newMethodBody.addStatement(newTry);
    }

    private void handleSimpleStatement(CtStatement statement, CtBlock<?> newMethodBody, Set<String> validVariables) {
        // if (setVoid) {
        //     statement.getElements(new TypeFilter<>(CtReturn.class)).forEach(returnStatement ->
        //             returnStatement.replace(statement.getFactory().createCodeSnippetStatement("")));
        //     if (statement instanceof CtReturn) {
        //         return;
        //     }
        // }
        if (checkValidSimpleStatement(statement, newMethodBody, validVariables)) {
            CtStatement clone = statement.clone();
            if (clone instanceof CtLocalVariable<?> localVar) {
                if (localVar.getModifiers() != null) {
                    localVar.setModifiers(null);
                }
                if (localVar.getAssignment() == null) {
                    String type = localVar.getType().getSimpleName();
                    int random = new Random().nextInt(100);
                    switch (type) {
                        case "int":
                            localVar.setAssignment(localVar.getFactory().createCodeSnippetExpression(String.valueOf(random)));
                            break;
                        case "double":
                            localVar.setAssignment(localVar.getFactory().createCodeSnippetExpression(random + ".0"));
                            break;
                        case "float":
                            localVar.setAssignment(localVar.getFactory().createCodeSnippetExpression(random + ".0f"));
                            break;
                        case "long":
                            localVar.setAssignment(localVar.getFactory().createCodeSnippetExpression(random + "L"));
                            break;
                        case "short":
                            localVar.setAssignment(localVar.getFactory().createCodeSnippetExpression("(short)" + random));
                            break;
                        case "byte":
                            localVar.setAssignment(localVar.getFactory().createCodeSnippetExpression("(byte)" + random));
                            break;
                        case "char":
                            localVar.setAssignment(localVar.getFactory().createCodeSnippetExpression("'a'"));
                            break;
                        case "boolean":
                            localVar.setAssignment(localVar.getFactory().createCodeSnippetExpression("false"));
                            break;
                        default:
                            localVar.setAssignment(localVar.getFactory().createCodeSnippetExpression("null"));
                            break;
                    }
                }

            }
            newMethodBody.addStatement(clone);
        }
    }

    private boolean checkValidSimpleStatement(CtStatement stmt, CtBlock<?> newMethodBody, Set<String> validVariables) {
        if (containsProjectSpecificReference(stmt)) {
            return false;
        }

        // add first
        stmt.getElements(new TypeFilter<>(CtLocalVariable.class))
                .forEach(var -> validVariables.add(var.getSimpleName()));

        // remember the lambda expression
        stmt.getElements(new TypeFilter<>(CtParameter.class))
                .forEach(lambda -> validVariables.add(lambda.getSimpleName()));


        if (containUndefinedVariable(stmt, newMethodBody, validVariables)) {
            // delete the variable
            stmt.getElements(new TypeFilter<>(CtLocalVariable.class))
                    .forEach(var -> validVariables.remove(var.getSimpleName()));

            // delete the lambda expression
            stmt.getElements(new TypeFilter<>(CtParameter.class))
                    .forEach(lambda -> validVariables.remove(lambda.getSimpleName()));
            return false;
        }
        return true;
    }

    private void handleBlock(CtBlock<?> block, CtBlock<?> newMethodBody, Set<String> validVariables) {
        for (CtStatement statement : block.getStatements()) {
            replaceGlobalConstant(statement);
            if (statement instanceof CtIf) {
                handleIfStatement((CtIf) statement, newMethodBody, validVariables);
            } else if (statement instanceof CtFor || statement instanceof CtForEach
                    || statement instanceof CtWhile || statement instanceof CtDo) {
                handleLoopStatement(statement, newMethodBody, validVariables);
            } else if (statement instanceof CtTry tryStmt) {
                handleTryStatement(tryStmt, newMethodBody, validVariables);
            } else if (statement instanceof CtSynchronized ctSynchronized) {
                handleSynchronizedStatement(ctSynchronized, newMethodBody, validVariables);
            } else if (statement instanceof CtSwitch<?> ctSwitch) {
                handleSwitchStatement(ctSwitch, newMethodBody, validVariables);
            } else if (statement instanceof CtBlock<?> blockStmt) {
                handleBlock(blockStmt, newMethodBody, validVariables);
            } else {
                handleSimpleStatement(statement, newMethodBody, validVariables);
            }
        }
    }

    private void handleSynchronizedStatement(CtSynchronized ctSynchronized, CtBlock<?> newMethodBody, Set<String> validVariables) {
        CtExpression<?> expression = ctSynchronized.getExpression();
        CtBlock<?> synchronizedBlock = ctSynchronized.getBlock();

        // Create new synchronized statement
        CtSynchronized newSynchronizedStmt = ctSynchronized.getFactory().createSynchronized();
        // newSynchronizedStmt.setParent(newMethodBody);

        if (!containsProjectSpecificReference(expression)) {
            newSynchronizedStmt.setExpression(expression);
        } else {
            return;
        }
        expression.getElements(new TypeFilter<>(CtLocalVariable.class))
                .forEach(var -> validVariables.add(var.getSimpleName()));


        CtBlock<?> newSynchronizedBlock = synchronizedBlock.getFactory().createBlock();
        // newSynchronizedBlock.setParent(newSynchronizedStmt);
        handleBlock(synchronizedBlock, newSynchronizedBlock, validVariables);

        newSynchronizedBlock.getStatements();
        newSynchronizedStmt.setBlock(newSynchronizedBlock);

        newMethodBody.addStatement(newSynchronizedStmt);
    }

    private void handleSwitchStatement(CtSwitch<?> ctSwitch, CtBlock<?> newMethodBody, Set<String> validVariables) {
        boolean canAddStmt = false;
        CtExpression<?> selector = ctSwitch.getSelector();
        if (containsProjectSpecificReference(selector) || containUndefinedVariable(selector, newMethodBody, validVariables)) {
            return;
        }

        CtSwitch newSwitchStmt = ctSwitch.getFactory().createSwitch();
        // newSwitchStmt.setParent(newMethodBody);
        newSwitchStmt.setSelector(selector);

        List<CtCase<?>> newCases = new ArrayList<>();
        for (CtCase<?> ctCase : ctSwitch.getCases()) {
            CtCase<?> newCase = ctCase.getFactory().createCase();
            CtExpression caseExpression = ctCase.getCaseExpression();
            if (caseExpression != null) {
                if (containsProjectSpecificReference(caseExpression) || containUndefinedVariable(caseExpression, newMethodBody, validVariables)) {
                    continue;
                }
                newCase.setCaseExpression(caseExpression);
            }

            List<CtStatement> newCaseStmts = new ArrayList<>();
            // newCaseBlock.setParent(newCase);
            for (CtStatement statement : ctCase.getStatements()) {
                if (checkValidSimpleStatement(statement, newMethodBody, validVariables))
                    newCaseStmts.add(statement.clone());
            }

            if (!newCaseStmts.isEmpty()) {
                canAddStmt = true;
            }
            newCase.setStatements(newCaseStmts);
            newCases.add(newCase);
        }
        newSwitchStmt.setCases(newCases);

        if (canAddStmt) {
            newMethodBody.addStatement(newSwitchStmt);
        }
    }


    private boolean isTrivialMethod(CtBlock<?> methodBody) {
        var stmts = methodBody.getStatements();
        if (stmts.size() == 1) {
            var stmt = stmts.get(0);
            return stmt instanceof CtThrow || stmt instanceof CtReturn || stmt instanceof VariableDeclaration;
        }
        // for (CtStatement statement : stmts) {
        //     if (!(statement instanceof CtLocalVariable || (statement instanceof CtAssignment &&
        //             (((CtAssignment<?, ?>) statement).getAssignment() instanceof CtVariableRead ||
        //                     ((CtAssignment<?, ?>) statement).getAssignment() instanceof CtLiteral)))) {
        //         return false;
        //     }
        // }
        return false;
    }


    private boolean containUndefinedVariable(CtCodeElement statement, CtBlock<?> newBlock, Set<String> validVariables) {
        if (newBlock == null) {
            return false;
        }

        // Collect all defined variables in the method
        // newBlock.getElements(new TypeFilter<>(CtLocalVariable.class))
        //         .forEach(var -> validVariables.add(var.getSimpleName()));
        // statement can define new variables as well

        // statement can define new exception variables as well
        // rootStmt.getElements(new TypeFilter<>(CtCatchVariable.class))
        //         .forEach(var -> validVariables.add(var.getSimpleName()));


        //         Check if the statement uses any undefined variable
        return statement.getElements(new TypeFilter<>(CtVariableAccess.class)).stream()
                .filter(varAccess -> !(varAccess instanceof CtFieldAccess))
                .map(CtVariableAccess::getVariable)
                .map(CtVariableReference::getSimpleName)
                //                .peek(varName -> {
                //                    if (!validVariables.contains(varName)) {
                //                        System.out.println("Undefined variable: " + varName);
                //                    }
                //                })
                .anyMatch(varName -> !validVariables.contains(varName));
    }

    private boolean containsProjectSpecificReference(CtCodeElement statement) {
        boolean hasDeniedField = statement.getElements(new TypeFilter<>(CtFieldAccess.class)).stream()
                .anyMatch(fieldAccess -> !isLibraryType(fieldAccess.getVariable().getType()));
        boolean hasDeniedVariable = statement.getElements(new TypeFilter<>(CtVariableAccess.class)).stream()
                .anyMatch(varAccess -> !isLibraryType(varAccess.getVariable().getType()) || varAccess.getVariable().getSimpleName().equals("this"));
        boolean hasDeniedConstructor = statement.getElements(new TypeFilter<>(CtConstructorCall.class)).stream()
                .anyMatch(constructorCall -> !isLibraryType(constructorCall.getType()));
        boolean hasDeniedVariableDeclaration = statement.getElements(new TypeFilter<>(CtLocalVariable.class)).stream()
                .anyMatch(localVar -> !isLibraryType(localVar.getType()));
        boolean hasDeniedTypeReference = statement.getElements(new TypeFilter<>(CtTypeReference.class)).stream()
                .anyMatch(typeRef -> !isLibraryType(typeRef));

        return hasDeniedField || hasDeniedVariable || hasDeniedConstructor || hasDeniedVariableDeclaration || hasDeniedTypeReference;
    }


    //    private boolean containsGlobalConstant(CtStatement statement) {
    //        // 检查语句是否包含全局常量
    //        return statement.getElements(new TypeFilter<>(CtFieldAccess.class)).stream()
    //                .anyMatch(Extractor::isGlobalConstant);
    //    }
    //
    //    private static boolean isGlobalConstant(CtFieldAccess<?> fieldAccess) {
    //        // 判断变量是否是全局常量（即 static final 变量）
    //        CtFieldReference<?> fieldRef = fieldAccess.getVariable();
    //        CtField<?> field = fieldRef.getDeclaration();
    //        return field != null && field.hasModifier(ModifierKind.STATIC) && field.hasModifier(ModifierKind.FINAL);
    //    }

    private void replaceGlobalConstant(CtStatement statement) {
        // 将全局常量替换为实际值或生成随机值
        statement.getElements(new TypeFilter<>(CtFieldAccess.class)).forEach(fieldAccess -> {
            var parent = fieldAccess.getParent();
            // 检查是否在单目表达式中
            if (parent instanceof CtUnaryOperator) {
                return;
            }
            // left value is not allowed
            if (parent instanceof CtAssignment && ((CtAssignment<?, ?>) parent).getAssigned() == fieldAccess) {
                return;
            }

            CtFieldReference<?> fieldRef = fieldAccess.getVariable();
            CtField<?> field = fieldRef.getDeclaration();

            if (field != null && field.hasModifier(ModifierKind.FINAL)) {
                CtExpression<?> defaultValue = field.getDefaultExpression();
                if (defaultValue instanceof CtLiteral) {
                    // 用常量值替换原始变量引用
                    fieldAccess.replace(defaultValue.clone());
                }
            } else if (field != null) {
                CtTypeReference<?> fieldType = field.getType();
                CtExpression<?> randomValue = generateRandomValue(fieldType);
                if (randomValue != null) {
                    fieldAccess.replace(randomValue);
                }
            }
        });
    }

    private CtExpression<?> generateRandomValue(CtTypeReference<?> fieldType) {
        if (fieldType.isPrimitive() || fieldType.getQualifiedName().equals("java.lang.String")) {
            if (fieldType.getSimpleName().equals("int")) {
                return fieldType.getFactory().createLiteral((int) (Math.random() * 100));
            } else if (fieldType.getSimpleName().equals("double")) {
                return fieldType.getFactory().createLiteral(Math.random() * 100);
            } else if (fieldType.getSimpleName().equals("boolean")) {
                return fieldType.getFactory().createLiteral(Math.random() > 0.5);
            } else if (fieldType.getSimpleName().equals("char")) {
                return fieldType.getFactory().createLiteral((char) (Math.random() * 26 + 'a'));
            } else if (fieldType.getSimpleName().equals("java.lang.String")) {
                return fieldType.getFactory().createLiteral("RandomString" + (int) (Math.random() * 100));
            }
        }
        return null;
    }

    private boolean isCompilable(File javaFile) {
        // 用 javac 编译它
        try {
            String compileCmd = "javac -cp D:/repository/JVM-Tesing-by-Anti-Optimization/libs/android.jar  -encoding UTF-8 " + javaFile.getAbsolutePath();
            Process p1 = Runtime.getRuntime().exec(compileCmd);
            p1.waitFor();
            // Process p1 = runCmd(compileCmd);
            // ShellUtils.getShellOut(p1, 1, "log.txt");

            boolean ret = false;
            // delete the class file
            String classFileName = javaFile.getName().replace(".java", ".class");
            File classFile = new File(javaFile.getParent(), classFileName);
            if (classFile.exists()) {
                ret = true;
                classFile.delete();
            }
            return ret;
        } catch (IOException | InterruptedException e) {
            log.error("Error when compiling the file: " + javaFile.getAbsolutePath());
        }
        return false;
    }

    //    private boolean isFileCompilable(File file) {
    //        // 检查文件是否可以编译
    //        // 此处我们假设 Spoon 的 AST 是正确的，并且不进行复杂的编译器检查
    //        // 创建Spoon的Launcher实例
    //        Launcher launcher = new Launcher();
    //        launcher.addInputResource(inputPath);  // 添加源代码文件或目录
    //        launcher.getEnvironment().setNoClasspath(true);  // 设置noClasspath模式以处理缺少依赖的情况
    //
    //        // 生成模型
    //        CtModel model = launcher.buildModel();
    //
    //        // 获取编译器
    //        SpoonCompiler compiler = new JDTBasedSpoonCompiler(launcher.getFactory());
    //
    //        try {
    //            // 编译源代码
    //            compiler.compileInputSources();
    //
    //            System.out.println("Compilation successful: " + inputPath);
    //        } catch (Exception e) {
    //            // 如果出现编译错误，捕获并输出
    //            System.err.println("Compilation failed for: " + inputPath);
    //            e.printStackTrace();
    //        }
    //    }

    private boolean repairCompilerError(CtMethod<?> method, CtClass<?> clazz) {
        var factory = method.getFactory();
        var returnType = method.getType();
        var methodBody = method.getBody();
        // 如果返回值是void, 则函数设为int，返回一个checksum，它是所有可达基本类型变量（除了布尔）的和，并强制转化为int.
        // 如果返回值是基本类型，则返回一个checksum，它是所有可达基本类型变量的和，并强制转化为对应类型，则返回这个checksum是否大于0
        if (!returnType.toString().equals("void")) {
            // 首先消除所有的return语句
            methodBody.getElements(new TypeFilter<>(CtReturn.class))
                    .forEach(returnStatement -> returnStatement.replace(factory.createCodeSnippetStatement("")));
        }
        method.setType(factory.Type().integerPrimitiveType());

        PPoint point = new PPoint(clazz, methodBody.getLastStatement(), PPoint.Which.BEFORE);

        Set<String> intVariables = new HashSet<>();
        Set<String> longVariables = new HashSet<>();
        Set<String> floatVariables = new HashSet<>();
        Set<String> doubleVariables = new HashSet<>();

        point.forEachAccVariable(var -> {
            if (var.getType().isPrimitive() && !var.getType().getSimpleName().equals("boolean")) {
                switch (var.getType().getSimpleName()) {
                    case "int":
                        intVariables.add(var.getSimpleName());
                        break;
                    case "long":
                        longVariables.add(var.getSimpleName());
                        break;
                    case "float":
                        floatVariables.add(var.getSimpleName());
                        break;
                    case "double":
                        doubleVariables.add(var.getSimpleName());
                        break;
                    default:
                        intVariables.add(var.getSimpleName());
                }
            }
        });
        CtReturn<?> returnStatement = methodBody.getFactory().createReturn();
        StringBuilder checksumExpression = new StringBuilder();

        // 加和 int 变量
        if (!intVariables.isEmpty()) {
            checksumExpression.append(String.join(" + ", intVariables));
        }

        // 加和 long 变量，并强制转换为 int
        if (!longVariables.isEmpty()) {
            if (!checksumExpression.isEmpty()) {
                checksumExpression.append(" + ");
            }
            checksumExpression.append("(int) (").append(String.join(" + ", longVariables)).append(")");
        }

        // 加和 float 变量，并强制转换为 int
        if (!floatVariables.isEmpty()) {
            if (!checksumExpression.isEmpty()) {
                checksumExpression.append(" + ");
            }
            checksumExpression.append("(int) (").append(String.join(" + ", floatVariables)).append(")");
        }

        // 加和 double 变量，并强制转换为 int
        if (!doubleVariables.isEmpty()) {
            if (!checksumExpression.isEmpty()) {
                checksumExpression.append(" + ");
            }
            checksumExpression.append("(int) (").append(String.join(" + ", doubleVariables)).append(")");
        }

        // 如果有可加和的变量，生成返回表达式
        if (!checksumExpression.isEmpty()) {
            returnStatement.setReturnedExpression(factory.createCodeSnippetExpression(checksumExpression.toString()));
            methodBody.addStatement(returnStatement);
            return true;
        } else {
            return false;
        }
    }

    private File saveMethod(CtMethod<?> method, String outputPath, String newClassName) {
        // 分别把clazz保存到不同的文件
        File outputFile = new File(outputPath, newClassName + ".java");
        try (PrintWriter out = new PrintWriter(outputFile, java.nio.charset.StandardCharsets.UTF_8)) {
            out.println("// Original Position: " + method.getPosition().toString());
            out.println("public class " + newClassName + " {\n");
            out.println(method);
            out.println("}");
        } catch (IOException e) {
            log.error("Error when saving the file: " + outputFile.getAbsolutePath());
        }
        return outputFile;
    }

    private void saveExtractedMethods(Set<Pair<CtMethod<?>, CtMethod<?>>> pairs) {
        if (!new File(outputPath).exists()) {
            new File(outputPath).mkdirs();
        }
        // 保存提取出的函数，可以将其输出到新的 Java 类中或保存到项目文件
        for (Pair<CtMethod<?>, CtMethod<?>> pair : pairs) {
            CtMethod<?> originMethod = pair.getLeft();
            CtMethod<?> newMethod = pair.getRight();

            CtClass<?> clazz = newMethod.getFactory().createClass();
            clazz.addMethod(newMethod);

            // 生成新类名
            String newClassName = "TplClass" + (classCounter++);
            if (newMethod.getType().getSimpleName().equals("void")) {
                if (!repairCompilerError(newMethod, clazz)) {
                    log.debug("No live variable for void method: " + newClassName);
                    continue;
                }
            }
            File outputFile = saveMethod(newMethod, outputPath, newClassName);

            boolean compileSuccess = isCompilable(outputFile);

            // try to repair the compilation error
            if (!compileSuccess) {
                if (!repairCompilerError(newMethod, clazz)) {
                    log.debug("No live variable to repair method: " + newClassName);
                    continue;
                }
                outputFile = saveMethod(newMethod, outputPath, newClassName);
                compileSuccess = isCompilable(outputFile);
            }

            if (!compileSuccess) {
                // delete the file
                outputFile.delete();
                saveMethod(originMethod, outputPath, newClassName + "_ori");
                saveMethod(newMethod, outputPath, newClassName + "_err");
                log.debug("Compilation failed for: " + newClassName);
            }
        }
    }
}
