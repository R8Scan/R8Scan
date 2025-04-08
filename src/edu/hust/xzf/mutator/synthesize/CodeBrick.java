/**
 * MIT License
 * <p>
 * Copyright (c) 2021 Cong Li (congli@smail.nju.edu.cn, cong.li@inf.ethz.ch)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package edu.hust.xzf.mutator.synthesize;

import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.util.ModelList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static edu.hust.xzf.mutator.mutatetemplate.MutateTemplate.generateUniqueMethodName;
import static edu.hust.xzf.mutator.synthesize.CbManager.MCB_METHOD_NAME;

/**
 * A code brick is a special type of code skeleton which have inputs and a list of statements. To
 * use a code brick, synthesize a declaration for each input and link the statement. Sometimes, you
 * may need to import the imports (but this often does not need since Spoon can take care of auto
 * imports in its setting by Spoon.getEnvironment().setAutoImports(true)).
 */
/* package */ class CodeBrick {
    private static int newVarIndex = 1;
    private final int mId;
    // The method that hangs the code brick
    private final CtMethod<?> mMethod;

    private Set<CtMethod<?>> otherMethods = null;
    // Required imports when using this brick elsewhere
    private final ModelList<CtImport> mImports;

    // Score for this code brick
    private final double mScore;

    public List<List<String>> parameterSets = new ArrayList<>();

    public CodeBrick(int id, CtMethod<?> cbMethod, ModelList<CtImport> imports) {
        mId = id;
        mMethod = cbMethod;
        String newMethodName = generateUniqueMethodName();
        mMethod.setSimpleName(newMethodName);
        mImports = imports;
        mScore = evaluateCbScore(cbMethod);
    }

    public Set<CtMethod<?>> getOtherMethods() {
        return otherMethods;
    }

    public double getScore() {
        return mScore;
    }

    public void setOtherMethods(Set<CtMethod<?>> otherMethods) {
        this.otherMethods = otherMethods;

        Map<String, String> nameMap = new HashMap<>();
        // should rename the method name and invoke target
        mMethod.getBody().getElements(new TypeFilter<>(CtInvocation.class))
                .forEach(ctInvocation -> {
                    var exec = ctInvocation.getExecutable();
                    if (exec != null) {
                        String name = exec.getSimpleName();
                        if (name.equals(MCB_METHOD_NAME))
                            exec.setSimpleName(mMethod.getSimpleName());

                        for (CtMethod<?> otherMtehod : otherMethods) {
                            if (otherMtehod.getSimpleName().equals(name)) {
                                if (nameMap.containsKey(name)) {
                                    exec.setSimpleName(nameMap.get(name));
                                } else {
                                    String newName = generateUniqueMethodName();
                                    nameMap.put(name, newName);
                                    exec.setSimpleName(newName);
                                }
                                break;
                            }
                        }
                    }
                });
        // rename the method name in other methods
        otherMethods.forEach(ctMethod -> {
            String name = ctMethod.getSimpleName();
            if (nameMap.get(name) == null)
                return;
            else
                ctMethod.setSimpleName(nameMap.get(name));
        });

        for (Iterator<CtMethod<?>> iterator = otherMethods.iterator(); iterator.hasNext(); ) {
            CtMethod<?> otherMethod = iterator.next();
            String name = otherMethod.getSimpleName();
            if (!nameMap.containsValue(name)) {
                iterator.remove();
            }
        }
    }

    public int getId() {
        return mId;
    }

    /**
     * Get the inputs of this code brick. Just take care. The inputs returned are already linked. So
     * please be sure to clone if they are expected to use elsewhere.
     *
     * @return The inputs of this code brick.
     */
    public CtParameter<?>[] unsafeGetInputs() {
        List<CtParameter<?>> params = mMethod.getParameters();
        CtParameter<?>[] inputs = new CtParameter<?>[params.size()];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = params.get(i);
        }
        return inputs;
    }

    /**
     * Get all statements of this code brick. Just take care. The statements returned are already
     * linked. So please be sure to clone if they are expected to use elsewhere.
     *
     * @return All statements of this code brick.
     */
    public CtBlock<?> unsafeGetStatements() {
        return mMethod.getBody();
    }

    /**
     * Get required imports if using this code brick elsewhere. The imports returned are already
     * linked. So please be sure to clone if they are expected to use elsewhere.
     *
     * @return Set of imports
     */
    public ModelList<CtImport> unsafeGetImports() {
        return mImports;
    }

    public CtMethod<?> getMethod() {
        return mMethod;
    }

    @Override
    public String toString() {
        return mMethod.toString();
    }

    private double evaluateCbScore(CtMethod<?> cbMethod) {
        return 1.0;
    }
    public void extractParameterSets(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        List<String> currentSet = null;

        for (String line : lines) {
            line = line.trim();
            // 检测到新的参数集起始标志
            if (line.startsWith("// parameter set")) {
                currentSet = new ArrayList<>();
                parameterSets.add(currentSet);
            } else if (currentSet != null && line.startsWith("//") && line.contains("=")) {
                // 处理形如 "// paramName = paramValue;" 的行
                String content = line.substring(2).trim(); // 去掉前缀"//"
                int eqIndex = content.indexOf("=");
                if (eqIndex != -1) {
                    String paramValue = content.substring(eqIndex + 1).trim();
                    // 移除末尾的分号（如果存在）
                    if (paramValue.endsWith(";")) {
                        paramValue = paramValue.substring(0, paramValue.length() - 1).trim();
                    }
                    currentSet.add(paramValue);
                }
            } else if (currentSet != null && !line.startsWith("//")) {
                // 当参数集块遇到非注释行时，结束当前参数集的收集
                currentSet = null;
            }
        }

//        String[][] result = new String[parameterSets.size()][];
//        for (int i = 0; i < parameterSets.size(); i++) {
//            List<String> set = parameterSets.get(i);
//            result[i] = set.toArray(new String[0]);
//        }
//        return result;
    }


}
