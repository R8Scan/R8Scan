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

import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtAbstractVisitor;
import spoon.reflect.visitor.EarlyTerminatingScanner;

import java.lang.annotation.Annotation;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class PPoint {

    // Context to consider: in which context we need to consider when computing required information
    // for example accessible variables, types, etc.
    private final CtClass<?> mContext;
    private final Which mWhich;
    private final CtStatement mStmt;
    private CtClass<?> mClass;
    private CtMethod<?> mMethod;
    private LimitedLexScope mScope;
    private boolean mScanned;

    PPoint(CtClass<?> ctx, CtStatement stmt, Which beforeOrAfter) {
        mContext = ctx;
        mWhich = beforeOrAfter;
        mStmt = stmt;
        mMethod = null;
        mClass = null;
        mScanned = false;
        mScope = null;
    }

    public static PPoint beforeStmt(CtClass<?> context, CtStatement stmt) {
        return new PPoint(context, stmt, Which.BEFORE);
    }

    public static PPoint afterStmt(CtClass<?> context, CtStatement stmt) {
        return new PPoint(context, stmt, Which.AFTER);
    }

    public CtStatement getStatement() {
        return mStmt;
    }

    public CtMethod<?> getMethod() {
        ensureScanned();
        return mMethod;
    }

    public CtClass<?> getClazz() {
        ensureScanned();
        return mClass;
    }

    public <T> void forEachAccVariable(Consumer<CtVariable<?>> con) {
        ensureScanned();
        LimitedLexScope curr = mScope;
        while (curr != null) {
            curr.variables.forEach(con);
            curr = curr.link;
        }
    }

    public <T> void forEachAccVariable(CtTypeReference<?> type, Consumer<CtVariable<?>> con) {
        forEachAccVariable(var -> {
            if (type.equals(var.getType())) {
                con.accept(var);
            }
        });
    }

    public <T> void forEachAccVariable(String type, Consumer<CtVariable<?>> con) {
        forEachAccVariable(var -> {
            if (type.equals(var.getType().getSimpleName())) {
                con.accept(var);
            }
        });
    }

//    public <T> void forEachAccVariable(CtTypeReference<?> type, Consumer<CtVariable<?>> con) {
//        forEachAccVariable(var -> {
//            if (type.equals(var.getType())) {
//                con.accept(var);
//            }
//        });
//    }

    private void ensureScanned() {
        if (!mScanned) {
            PPScanner scanner = new PPScanner();
            scanner.scan(mContext);

            mScope = scanner.scopes.peek();
            AxChecker.check(mScope != null,
                    "Fail to compute the program point of statement: " + mStmt);

            LimitedLexScope curr = mScope;
            while (curr != null) {
                if (mMethod == null && curr.element instanceof CtMethod) {
                    mMethod = (CtMethod<?>) curr.element;
                } else if (mClass == null && curr.element instanceof CtClass) {
                    mClass = (CtClass<?>) curr.element;
                }
                curr = curr.link;
            }
            AxChecker.check(mMethod != null, "No method found: " + mStmt);
            AxChecker.check(mClass != null, "No class found: " + mStmt);

            mScanned = true;
        }
    }

    public enum Which {
        BEFORE, AFTER
    }

    // LimitedLexScope is similar to but different from the lexical scope (i.e., LexicalScope),
    // where lexical scope gathers all variables in the scope and its parent scopes. However,
    // LimitedLexScope only contains those limited to variables which are accessible to the
    // current statement mStmt.
    private static class LimitedLexScope {
        CtElement element; // Element which created this lexical scope
        List<CtVariable<?>> variables; // Accessible variables in this scope
        LimitedLexScope link; // Linked parent scope and the accessible variables

        public LimitedLexScope(CtElement ele, LimitedLexScope prev) {
            element = ele;
            link = prev;
            variables = new LinkedList<>();
        }
    }

    private class PPScanner extends EarlyTerminatingScanner<Void> {
        final Deque<LimitedLexScope> scopes = new LinkedList<>();

        @Override
        protected void enter(CtElement e) {
            if (e == mStmt && mWhich == Which.BEFORE) {
                terminate();
            } else {
                findVariables(scopes.peek(), e);
                if (e == mStmt && mWhich == Which.AFTER) {
                    terminate();
                }
            }
        }

        @Override
        protected void exit(CtElement ele) {
            if (isTerminated()) {
                return;
            }
            LimitedLexScope top = scopes.peek();
            if (top != null && top.element == ele) {
                scopes.pop();
            }
        }

        private void findVariables(LimitedLexScope top, CtElement ele) {
            ele.accept(new CtAbstractVisitor() {

                @Override
                public <T> void visitCtClass(CtClass<T> clazz) {
                    scopes.push(new LimitedLexScope(clazz, top));
                }

                @Override
                public <T> void visitCtInterface(CtInterface<T> inf) {
                    scopes.push(new LimitedLexScope(inf, top));
                }

                @Override
                public <T extends Enum<?>> void visitCtEnum(CtEnum<T> enm) {
                    scopes.push(new LimitedLexScope(enm, top));
                }

                @Override
                public <A extends Annotation> void visitCtAnnotationType(CtAnnotationType<A> anno) {
                    scopes.push(new LimitedLexScope(anno, top));
                }

                @Override
                public <T> void visitCtMethod(CtMethod<T> method) {
                    scopes.push(new LimitedLexScope(method, top));
                }

                @Override
                public <T> void visitCtConstructor(CtConstructor<T> ctor) {
                    scopes.push(new LimitedLexScope(ctor, top));
                }

                @Override
                public <T> void visitCtLambda(CtLambda<T> lambda) {
                    scopes.push(new LimitedLexScope(lambda, top));
                }

                @Override
                public void visitCtTryWithResource(CtTryWithResource tryRes) {
                    scopes.push(new LimitedLexScope(tryRes, top));
                }

                @Override
                public void visitCtCatch(CtCatch katch) {
                    scopes.push(new LimitedLexScope(katch, top));
                }

                @Override
                public void visitCtFor(CtFor forr) {
                    scopes.push(new LimitedLexScope(forr, top));
                }

                @Override
                public void visitCtForEach(CtForEach foreach) {
                    scopes.push(new LimitedLexScope(foreach, top));
                }

                @Override
                public void visitCtWhile(CtWhile whyle) {
                    scopes.push(new LimitedLexScope(whyle, top));
                }

                @Override
                public void visitCtDo(CtDo doo) {
                    scopes.push(new LimitedLexScope(doo, top));
                }

                @Override
                public void visitCtIf(CtIf iff) {
                    scopes.push(new LimitedLexScope(iff, top));
                }

                @Override
                public <R> void visitCtBlock(CtBlock<R> block) {
                    scopes.push(new LimitedLexScope(block, top));
                }

                @Override
                public <T> void visitCtField(CtField<T> field) {
                    addVariable(field);
                }

                @Override
                public <T> void visitCtParameter(CtParameter<T> param) {
                    addVariable(param);
                }

                @Override
                public <T> void visitCtLocalVariable(CtLocalVariable<T> local) {
                    addVariable(local);
                }

                private void addVariable(CtVariable<?> var) {
                    if (top != null) {
                        top.variables.add(var);
                    }
                }
            });
        }
    }
}
