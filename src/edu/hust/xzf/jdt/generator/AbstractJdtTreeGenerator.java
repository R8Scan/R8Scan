package edu.hust.xzf.jdt.generator;

import edu.hust.xzf.jdt.tree.TreeContext;
import edu.hust.xzf.jdt.visitor.AbstractJdtVisitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;

public abstract class AbstractJdtTreeGenerator extends TreeGenerator {

    private static char[] readerToCharArray(Reader r) throws IOException {
        StringBuilder fileData = new StringBuilder();
        try (BufferedReader br = new BufferedReader(r)) {
            char[] buf = new char[10];
            int numRead = 0;
            while ((numRead = br.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
                buf = new char[1024];
            }
        }
        return fileData.toString().toCharArray();
    }

    @Override
    public TreeContext generate(Reader r) throws IOException {
        return generate(r, ASTParser.K_COMPILATION_UNIT);
    }

    @Override
    public TreeContext generate(Reader r, int astParserType) throws IOException {
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setKind(astParserType);
        Map<String, String> pOptions = JavaCore.getOptions();
        pOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        pOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        pOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        pOptions.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
        parser.setCompilerOptions(pOptions);
        parser.setSource(readerToCharArray(r));
        AbstractJdtVisitor v = createVisitor();
        parser.createAST(null).accept(v);
        return v.getTreeContext();
    }

    protected abstract AbstractJdtVisitor createVisitor();
}
