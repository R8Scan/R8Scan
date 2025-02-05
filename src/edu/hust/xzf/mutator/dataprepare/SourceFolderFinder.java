package edu.hust.xzf.mutator.dataprepare;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SourceFolderFinder implements ClassFinder {

    private String srcFolder;

    public SourceFolderFinder() {
    }

    public SourceFolderFinder(String srcFolder) {
        this.srcFolder = srcFolder;
    }

    @Override
    public String[] getClasses() {
        return getClassesLoc(new File(srcFolder), null).toArray(new String[0]);
    }

    public List<String> getClassesLoc(File testSrcFolder, String pack) {
        List<String> classes = new ArrayList<>();
        for (File file : testSrcFolder.listFiles()) {
            if (file.isDirectory())
                classes.addAll(getClassesLoc(file, pack == null ? file.getName() : pack + '.' + file.getName()));
            else if (file.getName().endsWith(".java")) {
                String className = pack == null ? file.getName() : pack + '.' + file.getName();
                className = className.substring(0, className.length());
                classes.add(className);
            } else if (file.getName().endsWith(".class")) {
                String className = pack == null ? file.getName() : pack + '.' + file.getName();
                className = className.substring(0, className.length());
                classes.add(className);
            }
        }
        return classes;
    }
}