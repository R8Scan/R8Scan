package edu.hust.xzf.mutator.synthesize;


import edu.hust.xzf.mutator.config.Configuration;
import edu.hust.xzf.mutator.mutatetemplate.MutateTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.declaration.CtMethodImpl;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static edu.hust.xzf.mutator.deoptpatterns.Inline.locateNewMethod;
import static edu.hust.xzf.mutator.synthesize.CbManager.MCB_CLASS_NAME_PREFIX;
import static edu.hust.xzf.mutator.utils.CodeUtils.countChar;

public class Synthesizer extends MutateTemplate {
    private static final Logger log = LoggerFactory.getLogger(Synthesizer.class);
    private static final AxRandom mRand = AxRandom.getInstance();
    private static CbManager funcManager;
    private final NewInstance mNewIns;
    private final List<CtImport> imports = new ArrayList<>(5);
    private Map<Integer, List<Integer>> funcMap = null;
    private static int start = 1;

    int importPos1 = -1;
    String insertedMethod = null;
    String insertedClass = "";
    private Launcher mSpoon;
    private CtClass<?> mClass;
    private CtMethod<?> mMethod;

    public Synthesizer() {
        mNewIns = new NewInstance();
        String funcPath = Configuration.funcPath;
        if (funcPath == null) {
            throw new RuntimeException("Code bricks path is not set");
        }
        funcManager = new CbManager(new File(funcPath));
        try {
            funcManager.init();
            if (funcMap == null)
                funcMap = funcManager.getFuncMap(Configuration.funcMappingPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 判断新的语句是否在更深的嵌套层次中
    private static boolean isNested(CtStatement current, CtStatement potentialInner) {
        if (potentialInner == null || current == null) {
            return false;
        }
        CtElement parent = potentialInner.getParent();
        while (parent != null) {
            if (current.equals(parent)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private ArrayList<String> findBeforeLoopVars(CtStatement stmp) {
        ArrayList<String> loopVars = new ArrayList<>();
        CtElement parent = stmp.getParent();
        while (!(parent instanceof CtMethodImpl<?>)) {
            if (parent instanceof CtFor forLoop) {
                forLoop.getForInit().forEach(init -> {
                    init.getElements(new TypeFilter<>(CtLocalVariable.class)).forEach(var -> {
                        loopVars.add(var.getSimpleName());
                    });
                });
            }
            if (parent instanceof CtForEach forEachLoop) {
                forEachLoop.getVariable().getElements(new TypeFilter<>(CtLocalVariable.class)).forEach(var -> {
                    loopVars.add(var.getSimpleName());
                });
            }

            if (parent instanceof CtDo doLoop) {
                doLoop.getLoopingExpression().getElements(new TypeFilter<>(CtLocalVariable.class)).forEach(var -> {
                    loopVars.add(var.getSimpleName());
                });
            }
            parent = parent.getParent();
        }
        return loopVars;
    }

    @Override
    public void generatePatches() {

        initSpoon();
        // locate the CStatement based on line number
        CtStatement stmt = getStatementByLine(Configuration.lineNumber);
        // get current class and method
        mClass = stmt.getParent(CtClass.class);
        mMethod = stmt.getParent(CtMethod.class);

        List<CtStatement> toInsertedStmts = synMainBlock(stmt, imports);


        if (toInsertedStmts.size() > 500) toInsertedStmts = toInsertedStmts.subList(0, 500);

        StringBuilder fixedCodeStr = new StringBuilder();
        for (CtStatement s : toInsertedStmts) {
            fixedCodeStr.append(s.toString()).append(";\n");
        }

        fixedCodeStr = new StringBuilder("\ntry {\n" + fixedCodeStr + "} catch (Exception eeeee) {}\n");

        generatePatch(this.suspCodeStartPos, this.suspCodeEndPos, "", fixedCodeStr.toString());
        offset += countChar(fixedCodeStr.toString(), '\n');
    }

    private void initSpoon() {
        mSpoon = new Launcher();
        mSpoon.getEnvironment().setComplianceLevel(21);
        // Use simple name instead of full-qualified name
        //        mSpoon.getEnvironment().setAutoImports(true);
        mSpoon.getEnvironment().setCommentEnabled(false);
        // Let's temporarily disable all other classpath (e.g., rt.jar) other than the parent folder
        //        mSpoon.getEnvironment().setSourceClasspath(new String[] {mInput.getParent()});
        // Don't copy any resource when output, just the given test file
        mSpoon.getEnvironment().setCopyResources(false);
        mSpoon.addInputResource(this.sourceCodeFile.getAbsolutePath());
        mSpoon.buildModel();
    }

    private CtStatement getStatementByLine(int line) {
        // 遍历所有的语句（CtStatement）
        CtStatement closestStatement = null;
        CtModel model = mSpoon.getModel();
        // 遍历项目中的每个类
        for (CtType<?> ctType : model.getAllTypes()) {
            for (var subtype : ctType.getTypeMembers()) {
                if (subtype instanceof CtClass<?> ctClass) {
                    for (CtTypeMember member : ctClass.getTypeMembers()) {
                        if (member instanceof CtFormalTypeDeclarer formal) {
                            closestStatement = getStatementByLineInMethod(line, formal);
                            if (closestStatement != null)
                                return closestStatement;
                        }
                    }
                } else if (subtype instanceof CtFormalTypeDeclarer formal) {
                    closestStatement = getStatementByLineInMethod(line, formal);
                    if (closestStatement != null)
                        return closestStatement;
                }
            }
        }
        return closestStatement;
    }

    private CtStatement getStatementByLineInMethod(int line, CtFormalTypeDeclarer method) {
        CtStatement closestStatement = null;
        // 遍历每个类中的方法
        for (CtStatement statement : method.getElements(new TypeFilter<>(CtStatement.class))) {
            int startLine;
            int endLine;
            try {
                startLine = statement.getPosition().getLine();
                endLine = statement.getPosition().getEndLine();
            } catch (UnsupportedOperationException e) {
                continue;
            }

            // 检查语句是否位于目标行
            if (line >= startLine && line <= endLine) {
                // 如果此语句比之前找到的语句更深层，则更新closestStatement
                if (closestStatement == null || isNested(closestStatement, statement)) {
                    closestStatement = statement;
                }
            }
        }
        return closestStatement;
    }

    /**
     * Return whether the given element is synthetic or not
     *
     * @param ele Element to test
     * @return True if the given element is synthetic; or false.
     */
    public boolean isSyn(CtElement ele) {
        SourcePosition pos = ele.getPosition();
        return !pos.isValidPosition() || funcManager.isCodeBrick(pos.getFile());
    }

    /*
      Synthesize a loop at the given program point using the given skeleton.

      @param pp  Program point
     * @param skl Loop skeleton used to do synthesis
     * @param imp Types that are required to be imported when using the synthetic loop
     * @return The synthetic loop
     */
    //    public CtStatement synCodeBrick(PPoint pp, List<CtImport> imp) {
    //        Factory fact = mSpoon.getFactory();
    //
    //        // Synthesize our main loop using cb and save reused variables
    //        Set<CtVariable<?>> reusedSet = new HashSet<>();
    //        List<CtStatement> toInsertedStmts = synMainLoop(pp, imp, reusedSet);

    // Transfer reused set to a list to enable a 1-1 mapping
    //        List<CtVariable<?>> reusedList = new ArrayList<>(reusedSet);
    // Create backups for our reused variables
    //        List<CtLocalVariable<?>> backupList = synBackups(reusedList);
    // Create restores for our reused variables
    //        List<CtStatement> restoreList = synRestores(reusedList, backupList);

    // Our final loop should be ``backups; try { mainLoop; } finally { restores; }``
    //        CtBlock<?> finLoop = fact.createBlock();
    //        backupList.forEach(finLoop::addStatement);
    //        {
    //            CtTry loopRestore = fact.createTry();
    //            loopRestore.setBody(mainLoop);

    //            CtBlock<?> finalizer = fact.createBlock();
    //            restoreList.forEach(finalizer::addStatement);
    //            loopRestore.setFinalizer(finalizer);

    //            finLoop.addStatement(loopRestore);
    //        }

    //        return finLoop;
    //    }

    /*
      Synthesize a piece of code segment at given program point

      @param pp  Program point
     * @param imp Types that are required to be imported when using the synthetic code
     * @return The synthetic code segment
     */
    //    public CtBlock<?> synCodeSeg(PPoint pp, List<CtImport> imp) {
    //        CtBlock<?> seg = mSpoon.getFactory().createBlock();
    //
    //        // Choose a random code brick to instantiate
    //        CodeBrick cb = ensureGetUnusedCb();
    //        log.debug("Using CodeBrick#" + cb.getId());
    //
    //        // Create a declaration for every code brick input
    //        Set<CtVariable<?>> reusedSet = new HashSet<>();
    //        synForCbInputs(pp, cb, reusedSet).forEach(seg::addStatement);
    //
    //        // Add the code brick as body
    //        synForCbStmts(cb).forEach(seg::addStatement);
    //
    //        // Add backups and restores
    //        List<CtVariable<?>> reusedList = new ArrayList<>(reusedSet);
    //        List<CtLocalVariable<?>> backupList = synBackups(reusedList);
    //        backupList.forEach(seg::insertBegin);
    //        List<CtStatement> restoreList = synRestores(reusedList, backupList);
    //        restoreList.forEach(seg::addStatement);
    //
    //        // Append imports to imp
    //        cb.unsafeGetImports().forEach(e -> imp.add(e.clone()));
    //
    //        return seg;
    //    }

    /**
     * Synthesize an expression of the given type
     *
     * @param type Type of the expression
     * @return The synthetic expression
     */
    public CtExpression<?> synExpr(CtTypeReference<?> type) {
        return mNewIns.newInstance(mSpoon.getFactory(), type);
    }

    private CtStatement synFunctionCall(CodeBrick cb, String[] inputNames, List<CtVariable<?>> reusableSet) {
        // create a method invocation for the code brick
        Factory factory = mSpoon.getFactory();
        CtInvocation<?> methodInvocation = factory.createInvocation();
        // set the arguments of the method invocation
        methodInvocation.setTarget(factory.createCodeSnippetExpression(mClass.getSimpleName()));
        methodInvocation.setExecutable(factory.createExecutableReference().setSimpleName(cb.getMethod().getSimpleName()));

        for (String inputName : inputNames) {
            methodInvocation.addArgument(factory.createCodeSnippetExpression(inputName));
        }

        if (reusableSet.isEmpty()) {
            return methodInvocation;
        }

        /*
         * Find a possible variable from reusableSet as the return value of the method invocation.
         * The return type of the method invocation is the primitive type.
         * Randomly select a variable from reusableSet as the return value of the method invocation.
         * If the type does not match, then cast it.
         */
        var returnTypeName = cb.getMethod().getType().getSimpleName();
        if (returnTypeName.equals("void")) {
            return methodInvocation;
        }
        var returnType = cb.getMethod().getType();

        Collections.shuffle(reusableSet);
        Optional<CtVariable<?>> matchingVariable = reusableSet.stream()
                .filter(var -> var.getType().getSimpleName().equals(returnTypeName))
                .findFirst();

        CtStatement returnStatement;
        if (matchingVariable.isPresent()) {
            CtVariable<?> variable = matchingVariable.get();
            CtAssignment<?, ?> assignment = factory.createAssignment();
            // Set the variable as the left-hand side of the assignment
            CtVariableWrite variableWrite = factory.createVariableWrite();
            variableWrite.setVariable(variable.getReference());
            assignment.setAssigned(variableWrite);

            // Set the method invocation as the right-hand side of the assignment
            assignment.setAssignment((CtInvocationImpl) methodInvocation);
            //            assignment.setAssigned(assignedExpression);
            returnStatement = assignment;

        } else {
            CtVariable<?> variableToUse = reusableSet.get(new Random().nextInt(reusableSet.size()));
            if (isSafePrimitiveCast(returnType, variableToUse.getType())) {
                // Type doesn't match, so cast the return value and assign
                CtAssignment<?, ?> assignment = factory.createAssignment();
                CtVariableWrite variableWrite = factory.createVariableWrite();
                variableWrite.setVariable(variableToUse.getReference());
                assignment.setAssigned(variableWrite);
                methodInvocation.setTypeCasts(new ArrayList<>(List.of(cb.getMethod().getType())));
                // Set the method invocation as the right-hand side of the assignment
                assignment.setAssignment((CtInvocationImpl) methodInvocation);
                assignment.setType(cb.getMethod().getType());
                returnStatement = assignment;
            } else {
                // can not find a safe primitive cast, so directly return the method invocation
                returnStatement = methodInvocation;
            }
        }

        return returnStatement;
    }

    private boolean isSafePrimitiveCast(CtTypeReference<?> fromType, CtTypeReference<?> toType) {
        String fromTypeName = fromType.getSimpleName();
        String toTypeName = toType.getSimpleName();

        // 自动提升的安全转换
        return switch (fromTypeName) {
            case "byte" -> toTypeName.equals("short") || toTypeName.equals("int") || toTypeName.equals("long") ||
                    toTypeName.equals("float") || toTypeName.equals("double");
            case "short", "char" -> toTypeName.equals("int") || toTypeName.equals("long") ||
                    toTypeName.equals("float") || toTypeName.equals("double");
            case "int" -> toTypeName.equals("long") || toTypeName.equals("float") || toTypeName.equals("double");
            case "long" -> toTypeName.equals("float") || toTypeName.equals("double");
            case "float" -> toTypeName.equals("double");
            case "double" -> false; // double 是最高精度，不能安全转换为其他原生类型
            case "boolean" -> false; // boolean 不能转换为其他类型
            default -> false; // 其他未知类型
        };
    }


    private List<CtStatement> synMainBlock(CtStatement stmt, List<CtImport> typesToImport) {
        CodeBrick cb = ensureGetUnusedMCb();
        log.debug("Using Method#" + cb.getId());
        log.info("The Function Content: \n" + cb);
        CtParameter<?>[] inputs = cb.unsafeGetInputs();

        String[] inputNames = new String[inputs.length];

        ArrayList<String> loopVars = findBeforeLoopVars(stmt);

        PPoint pp = PPoint.beforeStmt(mClass, stmt);
        List<CtStatement> toInsertedStmts = new ArrayList<>(synForCbInputs(pp, cb, inputNames));

        List<CtVariable<?>> accVars = new ArrayList<>();
        pp.forEachAccVariable(var -> {
            // non-static variable dFld cannot be referenced from a static context
            if (!var.isFinal() && (!(var instanceof CtField) || var.isStatic() || !mMethod.isStatic())) {
                if (!loopVars.contains(var.getSimpleName()))
                    accVars.add(var);
            }
        });

        // create a method invocation for the code brick

        CtStatement methodInvocation = synFunctionCall(cb, inputNames, accVars);

        toInsertedStmts.add(methodInvocation);
        importPos1 = locateNewMethod(getSuspiciousCodeTree());

        insertedMethod = "\n" + cb.getMethod().toString()
                .replace(MCB_CLASS_NAME_PREFIX + cb.getId() + ".", "") + "\n";

        Set<CtMethod<?>> otherMethods = cb.getOtherMethods();
        if (otherMethods != null && !otherMethods.isEmpty()) {
            for (CtMethod<?> otherMethod : otherMethods) {
                insertedMethod += "\n" + otherMethod.toString()
                        .replace(MCB_CLASS_NAME_PREFIX + cb.getId() + ".", "") + "\n";
            }
        }

        cb.unsafeGetImports().forEach(e -> typesToImport.add(e.clone()));

        return toInsertedStmts;
    }

    private List<CtLocalVariable<?>> synBackups(List<CtVariable<?>> reusedList) {
        Factory fact = mSpoon.getFactory();
        List<CtLocalVariable<?>> backupList = new ArrayList<>(reusedList.size());
        // Create a backup for each var in reusedList, 1-1 mapping
        for (CtVariable<?> reusedVar : reusedList) {
            CtLocalVariable<?> local = fact.createLocalVariable(reusedVar.getType().clone(), AxNames.getInstance().nextName(), (CtExpression) fact.createVariableRead(reusedVar.getReference(), reusedVar.isStatic()));
            local.addModifier(ModifierKind.FINAL);
            backupList.add(local);
        }
        return backupList;
    }

    private List<CtStatement> synRestores(List<CtVariable<?>> reusedList, List<CtLocalVariable<?>> backupList) {
        Factory fact = mSpoon.getFactory();
        List<CtStatement> restoreList = new ArrayList<>(reusedList.size());
        for (int i = 0; i < reusedList.size(); i++) {
            // reusedList and backupList maintains a 1-1 mapping
            CtVariable<?> var = reusedList.get(i);
            CtLocalVariable<?> backup = backupList.get(i);
            restoreList.add(fact.createVariableAssignment(var.getReference(), var.isStatic(), (CtExpression) fact.createVariableRead(backup.getReference(), backup.isStatic())));
        }
        return restoreList;
    }

    private List<CtStatement> synForCbInputs(PPoint pp, CodeBrick cb, String[] inputNames) {
        Factory fact = mSpoon.getFactory();
        CtMethod<?> meth = pp.getMethod();
        CtParameter<?>[] inputs = cb.unsafeGetInputs();

        List<CtStatement> decls = new ArrayList<>();
        for (int i = 0; i < inputs.length; i++) {
            CtVariable<?> inp = inputs[i];
            CtTypeReference<?> inpType = inp.getType().clone();
            String inpName = generateUniqueVarName();
            CtExpression<?> inpInit = null;
            List<CtVariable<?>> reusableSet = new ArrayList<>();

            // Let's find a reusable variables and replace all occurrences with that variable.
            // We only consider reuse primitive types since reusing references (incl. array) is
            // risky to be implicitly modified by our code brick. Just be careful.
            // We always prefer to reuse existing variables than synthesize a new declaration.
            if (Spoons.isPrimitiveAlikeType(inpType)) {
                // We never use variables that are accessed by current program point's statements
                // because it is likely that we change the semantics. For example, when wrapping a
                // statement a = b + c, if our brick assigns b a new value, then a's is changed.
                Set<CtVariable<?>> stmtUsingVarSet = pp.getStatement().getElements(new TypeFilter<>(CtVariableAccess.class)).stream().map(vacc -> (CtVariable<?>) vacc.getVariable().getDeclaration()).collect(Collectors.toSet());

                pp.forEachAccVariable(inpType, var -> {
                    // Ensure the same final; cannot use non-static in static environments
                    if (var.isFinal() == inp.isFinal() && (!(var instanceof CtField) || var.isStatic() || !meth.isStatic()) && !stmtUsingVarSet.contains(var)) {
                        reusableSet.add(var);
                    }
                });

                if (!reusableSet.isEmpty()) {
                    // Randomly select a variable, and rename all input occurrences
                    CtVariable<?> reusedVar = reusableSet.get(AxRandom.getInstance().nextInt(reusableSet.size()));
                    log.info("Reuse existing variable " + reusedVar + " to fill input " + inp);
                    inputNames[i] = reusedVar.getSimpleName();
                    //                    Spoons.renameVariable(inp, reusedVar.getSimpleName());
                    continue;
                }
            }

            // If there's no reusable variables, let's try to find an existing initializer.
            // We don't always use initializers, let's flip a coin to introduce some randomness.
            if (AxRandom.getInstance().nextFloat() > 0.5f) {
                List<CtExpression<?>> reusableInitzSet = new ArrayList<>();
                funcManager.forEachInitz(inpType, reusableInitzSet::add);
                if (!reusableInitzSet.isEmpty()) {
                    inpInit = reusableInitzSet.get(AxRandom.getInstance().nextInt(reusableInitzSet.size())).clone();
                    log.info("Reuse existing initializer " + inpInit + " to fill input " + inp);
                }
            }

            // There's no initializers, either. Let's compromise to decl synthesis.
            if (inpInit == null) {
                inpInit = synExpr(inpType);
                log.info("Synthesized an initializer " + inpInit + " to fill input " + inp);
            }

            // It's okay if inpInit is still null.
            decls.add(fact.createLocalVariable(inpType, inpName, (CtExpression) inpInit));
            inputNames[i] = inpName;

            //            if (inpInit == null)
            //                useTryCatch = true;
        }

        return decls;
    }

    private CodeBrick ensureGetUnusedMCb() {
        List<Integer> ids = funcMap.get(start++);
        while (ids.isEmpty()) {
            ids = funcMap.get(start++);
        }
        if (funcMap.get(start) == null) {
            start = 1;
        }

        int select = ids.get(mRand.nextInt(ids.size()));
        return funcManager.getMCodeBrick(select);
    }


    @Override
    public String doPostProcess(String patchedJavaFile) {
        if (importPos1 != -1 && insertedMethod != null) {
            patchedJavaFile = patchedJavaFile.substring(0, importPos1) + insertedMethod + patchedJavaFile.substring(importPos1);
            offset += countChar(insertedMethod, '\n');
        }

        String imp = "";
        for (CtImport s : imports) {
            imp += s.toString() + "\n";
        }
        offset += countChar(imp, '\n');

        return imp + patchedJavaFile + insertedClass;
    }
}
