package edu.hust.xzf.mutator.info;

import edu.hust.xzf.mutator.mutatetemplate.MutateTemplate;
import edu.hust.xzf.mutator.mutatetemplate.MutateTemplate_B;

/**
 * Store the information of generated patches.
 *
 * @author kui.liu
 */
public class Patch {

    public String buggyFileName;
    private String fixPattern;
    private String buggyCodeStr = "";
    private String fixedCodeStr1 = "";
    private String fixedCodeStr2 = null;
    private int buggyCodeStartPos = -1;


    private int offset = 0;
    /*
     * if (buggyCodeEndPos == buggyCodeStartPos) then
     * 		replace buggyCodeStr with fixedCodeStr1;
     * else if (buggyCodeEndPos > buggyCodeStartPos && buggyCodeStartPos == -1) then
     * 		if (buggyCodeEndPos == 0) then
     * 			insert the missing override method. // FIXME: This is removed.
     * 		else if (buggyCodeEndPos == originalBuggyCodeStartPos) then
     * 			insert fixedCodeStr1 before buggyCodeStr;
     * else if (buggyCodeEndPos > buggyCodeStartPos && buggyCodeStartPos == originalBuggyCodeStartPos) then
     * 		fixedCodeStr1 + buggCodeStr + fixedCodeStr2;
     * else if (buggyCodeEndPos > buggyCodeStartPos && buggyCodeStartPos < originalBuggyCodeStartPos) then
     * 		remove the buggy method declaration.
     */
    private int buggyCodeEndPos = -1;

    private MutateTemplate template;
    private MutateTemplate nouse;

    public Patch(String fixPattern, MutateTemplate template) {
        this.fixPattern = fixPattern;
        this.template = template;
    }

    public Patch(String fixPattern, MutateTemplate_B template) {
        this.fixPattern = fixPattern;
    }

    public String getBuggyCodeStr() {
        return buggyCodeStr;
    }

    public void setBuggyCodeStr(String buggyCodeStr) {
        this.buggyCodeStr = buggyCodeStr;
    }

    public String getFixedCodeStr1() {
        return fixedCodeStr1;
    }

    public void setFixedCodeStr1(String fixedCodeStr1) {
        this.fixedCodeStr1 = fixedCodeStr1;
    }

    public String getFixedCodeStr2() {
        return fixedCodeStr2;
    }

    public void setFixedCodeStr2(String fixedCodeStr2) {
        this.fixedCodeStr2 = fixedCodeStr2;
    }

    public int getBuggyCodeStartPos() {
        return buggyCodeStartPos;
    }

    public void setBuggyCodeStartPos(int buggyCodeStartPos) {
        this.buggyCodeStartPos = buggyCodeStartPos;
    }

    public int getBuggyCodeEndPos() {
        return buggyCodeEndPos;
    }

    public void setBuggyCodeEndPos(int buggyCodeEndPos) {
        this.buggyCodeEndPos = buggyCodeEndPos;
    }

    public String getFixPattern() {
        return fixPattern;
    }

    public void setFixPattern(String fixPattern) {
        this.fixPattern = fixPattern;
    }

    public String getBuggyFileName() {
        return buggyFileName;
    }

    public void setBuggyFileName(String buggyFileName) {
        this.buggyFileName = buggyFileName;
    }

    @Override
    public String toString() {
        return this.fixedCodeStr1 + "\n" + this.fixedCodeStr2;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Patch p) {
            if (!buggyFileName.equals(p.buggyFileName)) return false;
            if (!buggyCodeStr.equals(p.buggyCodeStr)) return false;
            if (buggyCodeStartPos != p.buggyCodeStartPos) return false;
            if (buggyCodeEndPos != p.buggyCodeEndPos) return false;
            if (!fixedCodeStr1.equals(p.fixedCodeStr1)) return false;
            if (fixedCodeStr2 == null) {
                return p.fixedCodeStr2 == null;
            } else return fixedCodeStr2.equals(p.fixedCodeStr2);
        } else return false;
    }

    public String doPostProcess(String patchedJavaFile) {
        return template.doPostProcess(patchedJavaFile);
    }


    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

}
