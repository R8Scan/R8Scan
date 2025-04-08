package edu.hust.xzf.mutator.synthesize;

import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.support.util.ModelList;

/**
 * A code brick is a special type of code skeleton which have inputs and a list of statements. To
 * use a code brick, synthesize a declaration for each input and link the statement. Sometimes, you
 * may need to import the imports (but this often does not need since Spoon can take care of auto
 * imports in its setting by Spoon.getEnvironment().setAutoImports(true)).
 */
/* package */ class ClassCBS {
    private final int mId;
    // The method that hangs the code brick
    private final CtMethod<?> mainMethod;
    private final CtClass<?> mainClazz;
    // Required imports when using this brick elsewhere
    private final ModelList<CtImport> mImports;

    // Score for this code brick
    private final double mScore;

    public ClassCBS(int id, CtClass<?> cbClass, CtMethod<?> cbMethod, ModelList<CtImport> imports) {
        mId = id;
        mainClazz = cbClass;
        if (cbClass.isPublic()) cbClass.removeModifier(ModifierKind.PUBLIC);
        if (cbClass.isPrivate()) cbClass.removeModifier(ModifierKind.PRIVATE);
        if (cbClass.isProtected()) cbClass.removeModifier(ModifierKind.PROTECTED);

        mainMethod = cbMethod;
        mImports = imports;
        // mPackageName = packageName;
        mScore = evaluateCbScore(cbMethod);
    }

    public int getId() {
        return mId;
    }

    /**
     * Get all statements of this code brick. Just take care. The statements returned are already
     * linked. So please be sure to clone if they are expected to use elsewhere.
     *
     * @return All statements of this code brick.
     */
    public CtBlock<?> unsafeGetStatements() {
        return mainMethod.getBody();
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
        return mainMethod;
    }

    public CtClass<?> getClazz() {
        return mainClazz;
    }

    @Override
    public String toString() {
        return mainClazz.toString();
    }

    private double evaluateCbScore(CtMethod<?> cbMethod) {
        return 1.0;
    }
}
