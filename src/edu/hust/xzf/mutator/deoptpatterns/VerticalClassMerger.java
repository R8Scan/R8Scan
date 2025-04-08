package edu.hust.xzf.mutator.deoptpatterns;

import edu.hust.xzf.jdt.tree.ITree;
import edu.hust.xzf.mutator.context.ContextReader;
import edu.hust.xzf.mutator.mutatetemplate.MutateTemplate;
import edu.hust.xzf.mutator.utils.Checker;

import java.util.HashSet;
import java.util.Set;

import static edu.hust.xzf.mutator.utils.CodeUtils.countChar;

public class VerticalClassMerger extends MutateTemplate {

    ITree mainMethod = null;
    Set<ITree> methods = new HashSet<>();
    String currentClassName = null;
    ITree currentMethod = null;
    String currentParentClassName = null;
    String newParentClassName = null;
    ITree classNode = null;
    boolean isAddExtends = false;
    String parentClassStr = null;
    int pos1 = -1; // class start
    int pos2 = -1; // class end
    int pos3 = -1; // to delete method start
    int pos4 = -1; // to delete method end


    @Override
    public void generatePatches() {
        ITree codeAst = this.getSuspiciousCodeTree();

        findMainMethod(codeAst);
        methods.remove(currentMethod);
        methods.remove(mainMethod);
        parentClassStr = this.getFileCodeStr(classNode.getPos(), classNode.getEndPos());
        if (parentClassStr.startsWith("Test"))
            parentClassStr = "class " + parentClassStr;

        // randomly delete one method (except the current method) from the child class
        if (!methods.isEmpty()) {
            int index = (new java.util.Random()).nextInt(methods.size());
            ITree method = (ITree) methods.toArray()[index];
            String methodStr = this.getFileCodeStr(method.getPos(), method.getEndPos());
            pos1 = method.getPos();
            pos2 = method.getEndPos();
            if (codeAst.getPos() > pos1)
                offset = offset - countChar(this.getFileCodeStr(method.getPos(), method.getEndPos()), '\n');
        } else {
            return;
        }

        generatePatch(this.getSuspiciousCodeStr());

        if (currentParentClassName == null)
            newParentClassName = currentClassName + "_p";
        else
            newParentClassName = currentParentClassName + "_p";

        // change the class type
        parentClassStr = parentClassStr.replace("public class", "class");

        if (currentParentClassName == null)
            parentClassStr = parentClassStr.replace("class " + currentClassName + " {", "class " + newParentClassName + " {");
        else
            parentClassStr = parentClassStr.replace("class " + currentClassName + " extends " + currentParentClassName,
                    "class " + newParentClassName + " extends " + currentParentClassName);

        // set the new offset
        //        if (pos3 < codeAst.getPos()) {
        //            offset = offset + countChar(this.getFileCodeStr(pos3, pos4), '\n');
        //        }
        //        if (mainMethod.getPos() < codeAst.getPos()) {
        //            offset = offset - countChar(this.getFileCodeStr(mainMethod.getPos(), mainMethod.getEndPos()), '\n');
        //        }
    }

    private void findMainMethod(ITree tree) {
        // find the main method
        ITree parent = tree.getParent();
        while (parent != null) {
            if (Checker.isTypeDeclaration(parent.getType())) {
                int parentPos = parent.getPos();
                String parentLabel = parent.getLabel();
                if (currentClassName == null) {
                    currentClassName = ContextReader.readClassName(parent);
                    currentParentClassName = ContextReader.readSuperClassName(parent);
                    classNode = parent;
                    for (ITree child : parent.getChildren()) {
                        String cLabel = child.getLabel();
                        if (Checker.isMethodDeclaration(child.getType())) {
                            if (mainMethod == null) {
                                // private, @@int, MethodName:test, @@Argus:int+a+int[]+b+
                                String[] split = cLabel.split(", ");
                                String methodName = "";
                                for (String s : split) {
                                    if (s.startsWith("MethodName:")) {
                                        methodName = s.substring(11);
                                        break;
                                    }
                                }
                                if ("main".equals(methodName) && mainMethod == null) {
                                    mainMethod = child;
                                }
                            }
                            methods.add(child);
                        }
                    }
                }
            } else if (Checker.isMethodDeclaration(parent.getType())) {
                if (currentMethod == null) {
                    currentMethod = parent;
                }
            }
            parent = parent.getParent();
        }
    }

    @Override
    public String doPostProcess(String patchedJavaFile) {
        patchedJavaFile = patchedJavaFile.substring(0, pos1) + patchedJavaFile.substring(pos2);

        if (currentParentClassName == null) {
            patchedJavaFile = patchedJavaFile.replace("class " + currentClassName + " {",
                    "class " + currentClassName + " extends " + newParentClassName + " {");
        } else
            patchedJavaFile = patchedJavaFile.replace("class " + currentClassName + " extends " + currentParentClassName + " {",
                    "class " + currentClassName + " extends " + newParentClassName + " {");


        return patchedJavaFile + "\n" + parentClassStr;
    }
}

