package edu.hust.xzf.mutator.deoptpatterns;

import edu.hust.xzf.jdt.tree.ITree;
import edu.hust.xzf.mutator.context.ContextReader;
import edu.hust.xzf.mutator.mutatetemplate.MutateTemplate;
import edu.hust.xzf.mutator.utils.Checker;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EscapeAnalysis extends MutateTemplate {
    public boolean isImportBoolean = false;
    public boolean isImportChar = false;
    public boolean isImportByte = false;
    public boolean isImportShort = false;
    public boolean isImportInt = false;
    public boolean isImportLong = false;
    public boolean isImportFloat = false;
    public boolean isImportDouble = false;
    public boolean isImportString = false;
    String varType;

    @Override
    public void generatePatches() {
        ITree codeAst = this.getSuspiciousCodeTree();
        if (Checker.isVariableDeclarationStatement(codeAst.getType())) {
            ITree type = null;
            List<ITree> children = codeAst.getChildren();
            for (ITree child : children) {
                if (Checker.isModifier(child.getType())) continue;
                type = child;
                break;
            }
            String str = "";
            String str2 = ")).value";
            String fixedCodeStr1 = "";
            String dataType = type.getLabel();

            switch (dataType) {
                case "int" -> str += "new MyInteger((int)(";
                case "float" -> str += "new MyFloat((float)(";
                case "long" -> str += "new MyLong((long)(";
                case "double" -> str += "new MyDouble((double)(";
                case "boolean" -> str += "new MyBoolean((boolean)(";
                case "char" -> str += "new MyChar((char)(";
                case "short" -> str += "new MyShort((short)(";
                case "byte" -> str += "new MyByte((byte)(";
                case "String" -> str += "new MyString((String)(";
                default -> {
                    return;
                }
            }
            String typeStr = getSubSuspiciouCodeStr(type.getPos(), type.getEndPos());
            fixedCodeStr1 += typeStr + " ";
            int index = codeAst.getChildPosition(type) + 1;
            for (; index < children.size(); index++) {
                ITree varDefine = children.get(index);
                ITree var = varDefine.getChild(0);
                ITree exp = varDefine.getChild(1);
                //                int varStart = var.getPos();
                //                int varEnd = varStart + var.getLength();
                //                String varName = this.getSubSuspiciouCodeStr(varStart, varEnd);
                String expStr = this.getSubSuspiciouCodeStr(exp.getPos(), exp.getEndPos());
                String varDef = this.getSubSuspiciouCodeStr(var.getPos(), var.getEndPos());
                fixedCodeStr1 += varDef + " = " + str + expStr + str2;
                if (index != children.size() - 1)
                    fixedCodeStr1 += ",";
                else fixedCodeStr1 += ";";
            }
            generatePatch(fixedCodeStr1);
            setImport(dataType);
        } else if (Checker.isExpressionStatement(codeAst.getType())) {
            ITree assignmentExp = codeAst.getChild(0);
            if (Checker.isAssignment(assignmentExp.getType())) {
                ContextReader.readAllVariablesAndFields(codeAst, allVarNamesMap, varTypesMap, allVarNamesList, this.sourceCodePath, dic);
                ITree var = assignmentExp.getChild(0);
                String varName = var.getLabel();
                if (Checker.isQualifiedName(var.getType())) {
                    varName = varName.substring(varName.lastIndexOf(".") + 1);
                }
                String varType = varTypesMap.get(varName);
                if (varType == null) {
                    varType = varTypesMap.get("this." + varName);
                }
                if (varType != null) {
                    String str = "";
                    String str2 = ")).value";
                    String fixedCodeStr1 = "";
                    switch (varType) {
                        case "int" -> str += "new MyInteger((int)(";
                        case "float" -> str += "new MyFloat((float)(";
                        case "long" -> str += "new MyLong((long)(";
                        case "double" -> str += "new MyDouble((double)(";
                        case "boolean" -> str += "new MyBoolean((boolean)(";
                        case "char" -> str += "new MyChar((char)(";
                        case "short" -> str += "new MyShort((short)(";
                        case "byte" -> str += "new MyByte((byte)(";
                        case "String" -> str += "new MyString((String)(";
                        default -> {
                            return;
                        }
                    }
                    ITree exp = assignmentExp.getChild(2);
                    String expStr = this.getSubSuspiciouCodeStr(exp.getPos(), exp.getEndPos());
                    fixedCodeStr1 += var.getLabel() + " = " + str + expStr + str2 + ";";
                    generatePatch(fixedCodeStr1);
                    setImport(varType);
                }
            }
        }

        if (getPatch() == null) {
            // select a primitive type variable and replace it with a wrapper class
            List<ITree> suspVars = new ArrayList<>();
            ContextReader.identifySuspiciousVariables(codeAst, suspVars, new ArrayList<>());
            if (suspVars.isEmpty()) return;
            ITree variable = getRandomElementFromList(suspVars);
            if (variable == null)
                return;
            String fixedCodeStr = "";
            String str = "";
            String str2 = ").value";
            int varStart = variable.getPos();
            int varEnd = variable.getEndPos();
            String varStr = ContextReader.readVariableName(variable);
            fixedCodeStr += this.getSubSuspiciouCodeStr(suspCodeStartPos, varStart);
            // no need to cast here
            switch (varType) {
                case "int" -> str += "new MyInteger(";
                case "float" -> str += "new MyFloat(";
                case "long" -> str += "new MyLong(";
                case "double" -> str += "new MyDouble(";
                case "boolean" -> str += "new MyBoolean(";
                case "char" -> str += "new MyChar(";
                case "short" -> str += "new MyShort(";
                case "byte" -> str += "new MyByte(";
                case "String" -> str += "new MyString(";
                default -> {
                    return;
                }
            }
            fixedCodeStr += str + varStr + str2;
            fixedCodeStr += this.getSubSuspiciouCodeStr(varEnd, suspCodeEndPos);
            generatePatch(fixedCodeStr);
            setImport(varType);
        }
    }

    public ITree getRandomElementFromList(List<ITree> list) {
        // 如果List为空，返回null
        if (list.isEmpty()) {
            return null;
        }
        Random random = new Random();

        while (!list.isEmpty()) {
            int randomIndex = random.nextInt(list.size());
            var select = list.get(randomIndex);
            String suspVarName = select.getLabel();
            if (suspVarName.startsWith("Name:"))
                suspVarName = suspVarName.substring(5);
            varType = varTypesMap.get(suspVarName);
            if (varType == null) {
                varType = varTypesMap.get("this." + suspVarName);
            }
            if (varType == null) {
                list.remove(randomIndex);
                continue;
            }
            if ("int".equals(varType) || "long".equals(varType) || "short".equals(varType) || "byte".equals(varType)
                    || "float".equals(varType) || "double".equals(varType) || "char".equals(varType)) {
                return select;
            }
            list.remove(randomIndex);
        }
        return null;
    }

    private boolean checkImport(ITree tree, String str) {
        boolean flag = false;
        ITree parent = tree.getParent();

        while (parent != null) {
            if (Checker.isCompilationUnit(parent.getType())) {
                for (ITree child : parent.getChildren()) {
                    if (Checker.isTypeDeclaration(child.getType())) {
                        String classLabel = child.getLabel();
                        String className = classLabel.substring(classLabel.indexOf("ClassName:") + 10);
                        className = className.substring(0, className.indexOf(", "));
                        if (className.equals(str)) {
                            flag = true;
                            break;
                        }
                    }
                }
            }
            parent = parent.getParent();
        }
        return flag;
    }

    private void setImport(String type) {
        switch (type) {
            case "int" -> {
                boolean imported = checkImport(this.getSuspiciousCodeTree(), "MyInteger");
                if (!imported) {
                    isImportInt = true;
                }
            }
            case "float" -> {
                boolean imported = checkImport(this.getSuspiciousCodeTree(), "MyFloat");
                if (!imported) {
                    isImportFloat = true;
                }
            }
            case "long" -> {
                boolean imported = checkImport(this.getSuspiciousCodeTree(), "MyLong");
                if (!imported) {
                    isImportLong = true;
                }
            }
            case "double" -> {
                boolean imported = checkImport(this.getSuspiciousCodeTree(), "MyDouble");
                if (!imported) {
                    isImportDouble = true;
                }
            }
            case "boolean" -> {
                boolean imported = checkImport(this.getSuspiciousCodeTree(), "MyBoolean");
                if (!imported) {
                    isImportBoolean = true;
                }
            }
            case "char" -> {
                boolean imported = checkImport(this.getSuspiciousCodeTree(), "MyChar");
                if (!imported) {
                    isImportChar = true;
                }
            }
            case "short" -> {
                boolean imported = checkImport(this.getSuspiciousCodeTree(), "MyShort");
                if (!imported) {
                    isImportShort = true;
                }
            }
            case "byte" -> {
                boolean imported = checkImport(this.getSuspiciousCodeTree(), "MyByte");
                if (!imported) {
                    isImportByte = true;
                }
            }
            case "String" -> {
                boolean imported = checkImport(this.getSuspiciousCodeTree(), "MyString");
                if (!imported) {
                    isImportString = true;
                }
            }
        }
    }


    @Override
    public String doPostProcess(String patchedJavaFile) {
        String importStr = "";
        if (isImportBoolean) {
            importStr += "\nclass MyBoolean {\n" +
                    "public boolean value;\n" +
                    "    public MyBoolean(boolean value) {\n" +
                    "        this.value = value;\n" +
                    "    }\n" +
                    "    public boolean v() {\n" +
                    "        return value;\n" +
                    "    }\n" +
                    "}\n";
        } else if (isImportShort) {
            importStr += "\nclass MyShort {\n" +
                    "public short value;\n" +
                    "    public MyShort(short value) {\n" +
                    "        this.value = value;\n" +
                    "    }\n" +
                    "    public short v() {\n" +
                    "        return value;\n" +
                    "    }\n" +
                    "}\n";
        } else if (isImportByte) {
            importStr += "\nclass MyByte {\n" +
                    "public byte value;\n" +
                    "    public MyByte(byte value) {\n" +
                    "        this.value = value;\n" +
                    "    }\n" +
                    "    public byte v() {\n" +
                    "        return value;\n" +
                    "    }\n" +
                    "}\n";
        } else if (isImportChar) {
            importStr += "\nclass MyChar {\n" +
                    "public char value;\n" +
                    "    public MyChar(char value) {\n" +
                    "        this.value = value;\n" +
                    "    }\n" +
                    "    public char v() {\n" +
                    "        return value;\n" +
                    "    }\n" +
                    "}\n";
        } else if (isImportInt) {
            importStr += "\nclass MyInteger {\n" +
                    "public int value;\n" +
                    "    public MyInteger(int value) {\n" +
                    "        this.value = value;\n" +
                    "    }\n" +
                    "    public int v() {\n" +
                    "        return value;\n" +
                    "    }\n" +
                    "}\n";
        } else if (isImportLong) {
            importStr += "\nclass MyLong {\n" +
                    "public long value;\n" +
                    "    public MyLong(long value) {\n" +
                    "        this.value = value;\n" +
                    "    }\n" +
                    "    public long v() {\n" +
                    "        return value;\n" +
                    "    }\n" +
                    "}\n";
        } else if (isImportFloat) {
            importStr += "\nclass MyFloat {\n" +
                    "public float value;\n" +
                    "    public MyFloat(float value) {\n" +
                    "        this.value = value;\n" +
                    "    }\n" +
                    "    public float v() {\n" +
                    "        return value;\n" +
                    "    }\n" +
                    "}\n";
        } else if (isImportDouble) {
            importStr += "\nclass MyDouble {\n" +
                    "public double value;\n" +
                    "    public MyDouble(double value) {\n" +
                    "        this.value = value;\n" +
                    "    }\n" +
                    "    public double v() {\n" +
                    "        return value;\n" +
                    "    }\n" +
                    "}\n";
        } else if (isImportString) {
            importStr += "\nclass MyString {\n" +
                    "public String value;\n" +
                    "    public MyString(String value) {\n" +
                    "        this.value = value;\n" +
                    "    }\n" +
                    "    public String v() {\n" +
                    "        return value;\n" +
                    "    }\n" +
                    "}\n";
        }
        patchedJavaFile += importStr;
        return patchedJavaFile;
    }


}
