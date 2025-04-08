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

import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.reference.CtArrayTypeReferenceImpl;
import spoon.support.reflect.reference.CtTypeReferenceImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

/**
 * CbManager manages all code bricks and initializers that we have collected. Since there's numerous
 * bricks and initializers, it follows a lazy load manner to load them. There's an implicit defining
 * convention of our bricks and initializers. This class assumes our bricks and initializers are
 * already in a correct format (i.e., following our conventions) and thereby it does not
 * additionally check that convention. Specifically,
 * // @formatter:off
 * (1) Each code brick should be named as CB_INITZ_CLASS_NAME_PREFIX_{i}.java and there's a method 
 *     namely MCB_METHOD_NAME saving all the brick. Parameters of that method are inputs (holes).
 * (2) All initializers are managed according to their types. Each initializer should be set as a
 *     field of its specific INITZ_CLASS_NAME_{type} class. For initializers of primitive types,
 *     should guarantee that the initializer can be safely assigned to the boxed type. Should ensure
 *     that all initializers don't throw any RuntimeExceptions like NPE, NegativeArraySizeException.
 * // @formatter:on
 */
/* package */ class CbManager {

    private static final String MANIFEST_NAME = "MANIFEST";
    private static final String MANIFEST_LINE_COMMENT = "#";
    private static final String MANIFEST_LINE_CB_INITZ_PREFIX = "InitzCount=";
    private static final String MANIFEST_LINE_CB_PREFIX = "CbCount=";
    private static final String MANIFEST_LINE_CB_BL_PREFIX = "CbBlacklist=";
    private static final String CB_INITZ_CLASS_NAME_PREFIX = "InitzCls";
    private static final String INITZ_CLASS_NAME_STRING = CB_INITZ_CLASS_NAME_PREFIX + "String";
    private static final String INITZ_CLASS_NAME_ARRAY = CB_INITZ_CLASS_NAME_PREFIX + "Array";
    private static final String INITZ_CLASS_NAME_REF = CB_INITZ_CLASS_NAME_PREFIX + "Ref";
    private static final String INITZ_CLASS_NAME_BYTE = CB_INITZ_CLASS_NAME_PREFIX + "Byte";
    private static final String INITZ_CLASS_NAME_BOOLEAN = CB_INITZ_CLASS_NAME_PREFIX + "Boolean";
    private static final String INITZ_CLASS_NAME_SHORT = CB_INITZ_CLASS_NAME_PREFIX + "Short";
    private static final String INITZ_CLASS_NAME_CHAR = CB_INITZ_CLASS_NAME_PREFIX + "Char";
    private static final String INITZ_CLASS_NAME_INT = CB_INITZ_CLASS_NAME_PREFIX + "Int";
    private static final String INITZ_CLASS_NAME_LONG = CB_INITZ_CLASS_NAME_PREFIX + "Long";
    private static final String INITZ_CLASS_NAME_FLOAT = CB_INITZ_CLASS_NAME_PREFIX + "Float";
    private static final String INITZ_CLASS_NAME_DOUBLE = CB_INITZ_CLASS_NAME_PREFIX + "Double";
    protected static final String MCB_CLASS_NAME_PREFIX = "Class";
    private static final String CCB_CLASS_NAME_PREFIX = "c";
    static final String MCB_METHOD_NAME = "method";
    private static final String CCB_METHOD_NAME = "main";

    // Code bricks: lazy load
    private final CbLazyLoader mCbLoader;
    private final Map<Integer, CodeBrick> mCodeBricks;
    private final List<Integer> mCbBlist;
    private final File mCbFolder;
    // Initializers: lazy load
    private final InitzLazyLoader mInitzLoader;
    private int mCbCount;
    // Class Code bricks: lazy load
    private CbLazyLoader cCbLoader;
    private Map<Integer, ClassCBS> cCodeBricks;
    private List<Integer> cCbBlist;
    private File cCbFolder;
    private int cCbCount;
    private int mInitzCount;
    private CtClass<?> mInitzClsArray;
    private CtClass<?> mInitzClsRef;
    private CtClass<?> mInitzClsByte;
    private CtClass<?> mInitzClsBoolean;
    private CtClass<?> mInitzClsShort;
    private CtClass<?> mInitzClsChar;
    private CtClass<?> mInitzClsInt;
    private CtClass<?> mInitzClsLong;
    private CtClass<?> mInitzClsFloat;
    private CtClass<?> mInitzClsDouble;
    private CtClass<?> mInitzClsString;

    public CbManager(File cbFolder) {
        mCbLoader = new CbLazyLoader();
        mCodeBricks = new HashMap<>();
        mCbBlist = new ArrayList<>();
        mCbFolder = cbFolder;
        mCbCount = -1;
        mInitzLoader = new InitzLazyLoader();

    }

    public void initClassCBS(File class_cb_folder) throws IOException {
        // Parse manifest to learn the stats of initializer and templates
        File mani = new File(class_cb_folder, MANIFEST_NAME);
        cCbLoader = new CbLazyLoader();
        cCodeBricks = new HashMap<>();
        cCbBlist = new ArrayList<>();
        cCbFolder = class_cb_folder;
        cCbCount = -1;

        BufferedReader reader = new BufferedReader(new FileReader(mani));
        String newLine;
        while ((newLine = reader.readLine()) != null) {
            if (newLine.startsWith(MANIFEST_LINE_CB_PREFIX)) {
                cCbCount = Integer.parseInt(newLine.substring(MANIFEST_LINE_CB_PREFIX.length()));
            } else if (newLine.startsWith(MANIFEST_LINE_CB_BL_PREFIX)) {
                cCbBlist.addAll(Arrays
                        .stream(newLine.substring(MANIFEST_LINE_CB_BL_PREFIX.length()).split(","))
                        .map(Integer::parseInt).toList());
            } else {
                AxChecker.check(newLine.startsWith(MANIFEST_LINE_COMMENT),
                        "Unrecognized MANIFEST line: " + newLine);
            }
        }
    }

    public void init() throws IOException {
        // Parse manifest to learn the stats of initializer and templates
        File mani = new File(mCbFolder, MANIFEST_NAME);
        BufferedReader reader = new BufferedReader(new FileReader(mani));
        String newLine;
        while ((newLine = reader.readLine()) != null) {
            if (newLine.startsWith(MANIFEST_LINE_CB_INITZ_PREFIX)) {
                mInitzCount =
                        Integer.parseInt(newLine.substring(MANIFEST_LINE_CB_INITZ_PREFIX.length()));
            } else if (newLine.startsWith(MANIFEST_LINE_CB_PREFIX)) {
                mCbCount = Integer.parseInt(newLine.substring(MANIFEST_LINE_CB_PREFIX.length()));
            } else if (newLine.startsWith(MANIFEST_LINE_CB_BL_PREFIX)) {
                mCbBlist.addAll(Arrays
                        .stream(newLine.substring(MANIFEST_LINE_CB_BL_PREFIX.length()).split(","))
                        .map(Integer::parseInt).toList());
            } else {
                AxChecker.check(newLine.startsWith(MANIFEST_LINE_COMMENT),
                        "Unrecognized MANIFEST line: " + newLine);
            }
        }
    }

    Map<Integer, List<Integer>> getFuncMap(String path) {
        // 结果存储Map
        Map<Integer, List<Integer>> map = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                // 处理每一行
                processLine(line, map);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    private static void processLine(String line, Map<Integer, List<Integer>> map) {
        String[] parts = line.split(": ");
        String className = parts[0];
        String numbersStr = parts[1];

        numbersStr = numbersStr.replace("[", "").replace("]", "");
        String[] numbers = numbersStr.split(", ");

        int classId = Integer.parseInt(className.replaceAll("[^0-9]", ""));

        for (String numStr : numbers) {
            int num = Integer.parseInt(numStr);
            map.computeIfAbsent(num, k -> new ArrayList<>()).add(classId);
        }
    }

    public boolean isCodeBrick(File file) {
        File parent = file.getParentFile();
        return parent != null && parent.equals(mCbFolder);
    }

    public int getmCbCount() {
        return mCbCount;
    }

    public int getcCbCount() {
        return cCbCount;
    }

    public int getInitzCount() {
        return mInitzCount;
    }

    /**
     * Iterate over all existing initializers of the given type
     *
     * @param type Type of initializers to iterate
     * @param con  A consumer to consume an initializer
     */
    public void forEachInitz(CtTypeReference<?> type, Consumer<CtExpression<?>> con) {
        CtClass<?> initzCls = mInitzLoader.ensureLoaded(type);
        if (initzCls == mInitzClsArray || initzCls == mInitzClsRef) {
            initzCls.getFields().forEach(field -> {
                if (field.getType().equals(type)) {
                    con.accept(field.getAssignment());
                }
            });
        } else {
            initzCls.getFields().forEach(field -> con.accept(field.getAssignment()));
        }
    }

    /**
     * Get the brick of at the given index, or null if the brick is blacked
     *
     * @param index Index of the code brick
     * @return The code brick at index, or null if blacked
     */
    public CodeBrick getMCodeBrick(int index) {
        AxChecker.check(0 <= index && index < mCbCount,
                "Code brick with index " + index + " does not exist");
        if (mCbBlist.contains(index)) {
            return null;
        }
        mCbLoader.mcbLoaded(index);
        return mCodeBricks.get(index);
    }

    public ClassCBS getCCodeBrick(int index) {
        AxChecker.check(0 <= index && index < mCbCount,
                "Code brick with index " + index + " does not exist");
        if (cCbBlist.contains(index)) {
            return null;
        }
        cCbLoader.ccbLoaded(index);
        return cCodeBricks.get(index);
    }

    private class CbLazyLoader {

        public void ccbLoaded(int index) {
            if (cCodeBricks.containsKey(index)) {
                return;
            }

            String cbClassName = CCB_CLASS_NAME_PREFIX + index;
            File cbFile =
                    new File(cCbFolder.getAbsolutePath() + File.separator + cbClassName + ".java");
            AxChecker.check(cbFile.exists(),
                    "Code brick class not found: " + cbFile.getAbsolutePath());

            CtCompilationUnit cbUnit = Spoons.ensureCompUnitLoaded(cbFile.getAbsolutePath());

            // We assume that the code brick class have the same name as the file (no packages)
            CtType<?> mainType = cbUnit.getMainType();
            CtClass<?> cbClass = (CtClass<?>) mainType;
            CtMethod<?> cbMethod = null;
            String packageName = cbUnit.getPackageDeclaration().getReference().getSimpleName();
            try {
                cbMethod = cbClass.getMethodsByName(CCB_METHOD_NAME).get(0);
            } catch (IndexOutOfBoundsException e) {
                // noinspection ConstantConditions
                AxChecker.check(false, "No code brick namely " + CCB_METHOD_NAME + "() found in the index " + index);
            }

            AxChecker.check(cbMethod != null,
                    "No code brick namely " + CCB_METHOD_NAME + "() found");
            AxChecker.check(cbMethod.getBody() != null, "No statements found in code brick "
                    + cbClassName + "#" + CCB_METHOD_NAME + "()");

            // Save to the cache
            cCodeBricks.put(index, new ClassCBS(index, cbClass, cbMethod, cbUnit.getImports()));
        }

        public void mcbLoaded(int index) {
            if (mCodeBricks.containsKey(index)) {
                return;
            }

            String cbClassName = MCB_CLASS_NAME_PREFIX + index;
            File cbFile =
                    new File(mCbFolder.getAbsolutePath() + File.separator + cbClassName + ".java");
            AxChecker.check(cbFile.exists(),
                    "Code brick class not found: " + cbFile.getAbsolutePath());

            CtCompilationUnit cbUnit = Spoons.ensureCompUnitLoaded(cbFile.getAbsolutePath());

            // We assume that the code brick class have the same name as the file (no packages)
            CtType<?> mainType = cbUnit.getMainType();
            AxChecker.check(
                    mainType instanceof CtClass && cbClassName.equals(mainType.getQualifiedName()),
                    "The code brick has >=1 code bricks");
            CtClass<?> cbClass = (CtClass<?>) mainType;

            Set<CtMethod<?>> otherMethods = new HashSet<>();
            CtMethod<?> cbMethod = null;
            try {
                cbMethod = cbClass.getMethodsByName(MCB_METHOD_NAME).get(0);
                // exclude the main method
                for (CtMethod<?> method : cbClass.getMethods()) {
                    if (!method.getSimpleName().equals("main") && !method.getSimpleName().equals(MCB_METHOD_NAME)) {
                        otherMethods.add(method);
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                // noinspection ConstantConditions
                AxChecker.check(false, "No code brick namely " + MCB_METHOD_NAME + "() found");
            }
            AxChecker.check(cbMethod != null,
                    "No code brick namely " + MCB_METHOD_NAME + "() found");
            AxChecker.check(cbMethod.getBody() != null, "No statements found in code brick "
                    + cbClassName + "#" + MCB_METHOD_NAME + "()");

            // Save to the cache
            CodeBrick cb = new CodeBrick(index, cbMethod, cbUnit.getImports());
            if (!otherMethods.isEmpty()) {
                cb.setOtherMethods(otherMethods);
            }
            try {
                cb.extractParameterSets(cbFile.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCodeBricks.put(index, cb);
        }
    }

    private class InitzLazyLoader extends Spoons.TypeSwitch<CtClass<?>> {

        @Override
        protected CtClass<?> kaseArray(CtArrayTypeReferenceImpl<?> type) {
            if (mInitzClsArray == null) {
                mInitzClsArray = doLoad(INITZ_CLASS_NAME_ARRAY);
            }
            return mInitzClsArray;
        }

        @Override
        protected CtClass<?> kaseVoid(CtTypeReferenceImpl<?> type) {
            throw new RuntimeException("Cannot have initializers for primitive type void");
        }

        @Override
        protected CtClass<?> kaseBoxedVoid(CtTypeReferenceImpl<?> type) {
            // We treat boxed void as a reference
            return kaseRef(type);
        }

        @Override
        protected CtClass<?> kaseBoolean(CtTypeReferenceImpl<?> type) {
            if (mInitzClsBoolean == null) {
                mInitzClsBoolean = doLoad(INITZ_CLASS_NAME_BOOLEAN);
            }
            return mInitzClsBoolean;
        }

        @Override
        protected CtClass<?> kaseBoxedBoolean(CtTypeReferenceImpl<?> type) {
            return kaseBoolean(type);
        }

        @Override
        protected CtClass<?> kaseByte(CtTypeReferenceImpl<?> type) {
            if (mInitzClsByte == null) {
                mInitzClsByte = doLoad(INITZ_CLASS_NAME_BYTE);
            }
            return mInitzClsByte;
        }

        @Override
        protected CtClass<?> kaseBoxedByte(CtTypeReferenceImpl<?> type) {
            return kaseByte(type);
        }

        @Override
        protected CtClass<?> kaseShort(CtTypeReferenceImpl<?> type) {
            if (mInitzClsShort == null) {
                mInitzClsShort = doLoad(INITZ_CLASS_NAME_SHORT);
            }
            return mInitzClsShort;
        }

        @Override
        protected CtClass<?> kaseBoxedShort(CtTypeReferenceImpl<?> type) {
            return kaseShort(type);
        }

        @Override
        protected CtClass<?> kaseChar(CtTypeReferenceImpl<?> type) {
            if (mInitzClsChar == null) {
                mInitzClsChar = doLoad(INITZ_CLASS_NAME_CHAR);
            }
            return mInitzClsChar;
        }

        @Override
        protected CtClass<?> kaseBoxedChar(CtTypeReferenceImpl<?> type) {
            return kaseChar(type);
        }

        @Override
        protected CtClass<?> kaseInt(CtTypeReferenceImpl<?> type) {
            if (mInitzClsInt == null) {
                mInitzClsInt = doLoad(INITZ_CLASS_NAME_INT);
            }
            return mInitzClsInt;
        }

        @Override
        protected CtClass<?> kaseBoxedInt(CtTypeReferenceImpl<?> type) {
            return kaseInt(type);
        }

        @Override
        protected CtClass<?> kaseLong(CtTypeReferenceImpl<?> type) {
            if (mInitzClsLong == null) {
                mInitzClsLong = doLoad(INITZ_CLASS_NAME_LONG);
            }
            return mInitzClsLong;
        }

        @Override
        protected CtClass<?> kaseBoxedLong(CtTypeReferenceImpl<?> type) {
            return kaseLong(type);
        }

        @Override
        protected CtClass<?> kaseFloat(CtTypeReferenceImpl<?> type) {
            if (mInitzClsFloat == null) {
                mInitzClsFloat = doLoad(INITZ_CLASS_NAME_FLOAT);
            }
            return mInitzClsFloat;
        }

        @Override
        protected CtClass<?> kaseBoxedFloat(CtTypeReferenceImpl<?> type) {
            return kaseFloat(type);
        }

        @Override
        protected CtClass<?> kaseDouble(CtTypeReferenceImpl<?> type) {
            if (mInitzClsDouble == null) {
                mInitzClsDouble = doLoad(INITZ_CLASS_NAME_DOUBLE);
            }
            return mInitzClsDouble;
        }

        @Override
        protected CtClass<?> kaseBoxedDouble(CtTypeReferenceImpl<?> type) {
            return kaseDouble(type);
        }

        @Override
        protected CtClass<?> kaseString(CtTypeReferenceImpl<?> type) {
            if (mInitzClsString == null) {
                mInitzClsString = doLoad(INITZ_CLASS_NAME_STRING);
            }
            return mInitzClsString;
        }

        @Override
        protected CtClass<?> kaseRef(CtTypeReferenceImpl<?> type) {
            if (mInitzClsRef == null) {
                mInitzClsRef = doLoad(INITZ_CLASS_NAME_REF);
            }
            return mInitzClsRef;
        }

        public CtClass<?> ensureLoaded(CtTypeReference<?> type) {
            return svitch(type);
        }

        private CtClass<?> doLoad(String className) {
            File initzFile =
                    new File(mCbFolder.getAbsolutePath() + File.separator + className + ".java");
            AxChecker.check(initzFile.exists(),
                    "Initz class not found: " + initzFile.getAbsolutePath());
            return Spoons.ensureClassLoaded(initzFile.getAbsolutePath(), className);
        }
    }
}
