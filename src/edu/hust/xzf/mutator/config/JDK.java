package edu.hust.xzf.mutator.config;

import edu.hust.xzf.mutator.GCoptions.GetGCOptions;
import edu.hust.xzf.mutator.utils.ShellUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import static edu.hust.xzf.mutator.Scheduler.runCmd;
import static edu.hust.xzf.mutator.config.Configuration.isWin;

public class JDK {

    // random choose a num from 8, 11, 17, 21
    //    static final Map versionsMapApi = new HashMap() {{
    //        put(8, 26);
    //        put(11, 33);
    //        put(17, 34);
    //    }};
    static final int[] versions = {8, 11, 17};
    private static final Logger log = LoggerFactory.getLogger(JDK.class);
    static String javac;
    public final String profileFlags = " -XX:+TraceLoopOpts -XX:+PrintCEE -XX:+PrintInlining -XX:+TraceDeoptimization -XX:+PrintEscapeAnalysis" +
            " -XX:+PrintEliminateLocks -XX:+PrintOptoStatistics -XX:+PrintEliminateAllocations -XX:+PrintBlockElimination -XX:+PrintPhiFunctions" +
            " -XX:+PrintCanonicalization -XX:+PrintNullCheckElimination -XX:+TraceRangeCheckElimination -XX:+PrintOptimizePtrCompare ";
    public boolean isART = false;
    public boolean isOpenJDK = false;
    public Configuration config;
    public boolean isBiSheng = false;
    public boolean isJ9 = false;
    public boolean isGraal = false;
    public int version;
    public int targetVersion = -1;
    boolean isFirstJDK = false;
    String java;
    String xcomp;
    String classPathSplitter;
    String art;
    //    String test_jdk;
    String testLibRoot;


    public JDK(String jdkRoot, Configuration config, int jdkIndex) {
        this.config = config;
        isFirstJDK = jdkIndex == 0;
        if (isWin) {
            this.java = jdkRoot + "/java.exe ";
            if (jdkIndex == 0)
                JDK.javac = jdkRoot + "/javac.exe ";
            classPathSplitter = ";";
        } else {
            this.java = jdkRoot + "/java ";
            if (jdkIndex == 0)
                JDK.javac = jdkRoot + "/javac ";
            classPathSplitter = ":";
        }

        testLibRoot = "JtregTestLib";

        //        test_jdk = " -D\"test.jdk=" + jdkRoot.substring(0, jdkRoot.indexOf("bin")) + "\" ";

        String lowerCase = jdkRoot.toLowerCase();

        isGraal = lowerCase.contains("graalvm");

        isOpenJDK = !lowerCase.contains("openj9") && !isGraal;

        isJ9 = lowerCase.contains("openj9");

        isART = lowerCase.contains("android") || lowerCase.contains("art");
        if (isART)
            art = jdkRoot;

        isBiSheng = lowerCase.contains("bisheng");

        if (!isART) {
            String jdkRepo = jdkRoot;
            if (jdkRoot.contains("build"))
                jdkRepo = jdkRoot.substring(0, jdkRoot.indexOf("build"));

            if (jdkRepo.contains("1.8")) {
                version = 8;
            } else if (jdkRepo.contains("11")) {
                version = 11;
            } else if (jdkRepo.contains("17")) {
                version = 17;
            } else if (jdkRepo.contains("21")) {
                version = 21;
            } else if (jdkRepo.contains("23")) {
                version = 23;
            } else if (jdkRepo.contains("8")) {
                version = 8;
            } else {
                throw new RuntimeException("Unsupported JDK version: " + jdkRepo);
            }
        } else {
            if (jdkRoot.contains("8"))
                version = 8;
            else if (jdkRoot.contains("7")) {
                version = 7;
            } else
                version = 15;
        }
    }


    public static String getJavac() {
        // random choose a num from map
//        int targetVersion = versions[new Random().nextInt(versions.length)];
        return javac + " -source 17 -target 17 -Xlint:none -encoding UTF-8 ";
    }

    public static void processJar(String tmpDir, String iter0Path, String packageName, String trueTestCase) throws IOException {
        File classesJar = new File(tmpDir + "/classes.jar");
        if (classesJar.exists()) {
            return;
        }

        // jar cvf the class to jar
        String jar_cmd = "jar cvf " + tmpDir + "/classes.jar ";

        if (new File(iter0Path + "/FuzzerUtils.class").exists()) {
            if (isWin)
                jar_cmd += "-C " + iter0Path + " FuzzerUtils.class -C " + iter0Path + " \"FuzzerUtils$1.class\" ";
            else
                jar_cmd += "-C " + iter0Path + " FuzzerUtils.class -C " + iter0Path + " \"FuzzerUtils\\$1.class\" ";
        }

        jar_cmd += tmpDir + packageName.replace(".", "/") + "/*.class";
        for (File file : Objects.requireNonNull(new File(tmpDir + packageName.replace(".", "/")).listFiles())) {
            if (file.getName().endsWith(".class"))
                jar_cmd += " -C " + tmpDir + packageName.replace(".", "/") + " \"" + file.getName() + "\"";
        }

        log.debug("jar cvf command: \n" + jar_cmd);
        Process proc1 = runCmd(jar_cmd);
        ShellUtils.getShellOut(proc1, 2, tmpDir + "jarlog.log");
        // check the jar file
        if (!classesJar.exists()) {
            throw new RuntimeException(" jar failed");
        }
    }

    public String getJITJava(String compileOnly, String tmpDir, String iter0Path, String trueTestCase) {
        String cp = iter0Path == null ? (Configuration.isRegression ? getTestLibRoot() : Configuration.javaFuzzerRoot) : iter0Path;
        String addtionOption = (Configuration.useRandomJITOptions ? getRandomOption() : "") + (Configuration.useRandomGCOptions ? getRandomGCOptions() : "");

        if (Configuration.useRandomJITOptions || Configuration.useRandomGCOptions)
            addtionOption = "-XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions " + addtionOption;

        if (isOpenJDK || isBiSheng) {
            String option = String.format(" -XX:+LogVMOutput -XX:LogFile=%s/vm.log -XX:-DisplayVMOutput "
                    + "%s", tmpDir, profileFlags);
            String logvm = Configuration.enableProfileGuidance ? (compileOnly + option) : "";
            // return java + " -Xcomp " + logvm + addtionOption + " -cp \"" + tmpDir + classPathSplitter + cp + "\" " + trueTestCase;
            return java + logvm + addtionOption + " -cp \"" + tmpDir + classPathSplitter + cp + "\" " + trueTestCase;
        } else if (isJ9) {
            return java + " -Xshareclasses:none -Xmx1G -Xjit:count=0 -cp \"" + tmpDir + classPathSplitter + cp + "\" " + trueTestCase;
        } else
            return java + " -cp \"" + tmpDir + classPathSplitter + cp + "\" " + trueTestCase;
    }

    public String getDefaultJava(String compileOnly, String tmpDir, String iter0Path, String trueTestCase) {
        String cp = iter0Path == null ? (Configuration.isRegression ? getTestLibRoot() : Configuration.javaFuzzerRoot) : iter0Path;
        String addtionOption = (Configuration.useRandomJITOptions ? getRandomOption() : "") + (Configuration.useRandomGCOptions ? getRandomGCOptions() : "");

        if (Configuration.useRandomJITOptions || Configuration.useRandomGCOptions)
            addtionOption = "-XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions " + addtionOption;


        if (isOpenJDK || isBiSheng) {
            String option = String.format(" -XX:+LogVMOutput -XX:LogFile=%s/vm.log -XX:-DisplayVMOutput "
                    + "%s", tmpDir, profileFlags);
            String logvm = Configuration.enableProfileGuidance ? (compileOnly + option) : "";
            return java + " " + logvm + addtionOption + "-Xcomp -cp \"" + tmpDir + classPathSplitter
                    + cp + classPathSplitter + "libs/android.jar" + "\" " + trueTestCase;
        } else if (isJ9) {
            return java + " -Xshareclasses:none -Xmx1G -cp \"" + tmpDir + classPathSplitter
                    + cp + classPathSplitter + "libs/android.jar" + "\" " + trueTestCase;
        } else
            return java + " -cp \"" + tmpDir + classPathSplitter + cp + classPathSplitter
                    + "libs/android.jar" + "\" " + trueTestCase;
    }

    public String processProguardRules(String tmpDir, String trueTestCase) {
        // process the proguard file
        String outputFile = tmpDir + "/proguard-rules.pro";
        try {
            List<String> lines = Files.lines(Paths.get("libs/proguard-rules.pro"), StandardCharsets.UTF_8)
                    .map(line -> line.replace("Test", trueTestCase))
                    .collect(Collectors.toList());
            // 将替换后的内容写入输出文件
            Path outputFilePath = Paths.get(outputFile);
            Files.write(outputFilePath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputFile;
    }

    public String processProguard(String tmpDir, String iter0Path, String packageName, String trueTestCase) throws IOException {
        processJar(tmpDir, iter0Path, packageName, trueTestCase);
        String outputFile = processProguardRules(tmpDir, trueTestCase);

        // convert jar using proguard
        String proguard_cmd = "java -jar libs/proguard.jar " +
                "@" + outputFile + " -injars " + tmpDir + "/classes.jar" + " -outjars " + tmpDir + "/classes_proguard.jar";
        log.debug("proguuard command: \n" + proguard_cmd);
        Process proc2 = runCmd(proguard_cmd);
        ShellUtils.getShellOut(proc2, 2, tmpDir + "proguard.log");

        return java + " -cp " + tmpDir + "/classes_proguard.jar " + trueTestCase;
    }

    public String processART(String tmpDir, String iter0Path, String packageName, String trueTestCase) throws IOException {
        processJar(tmpDir, iter0Path, packageName, trueTestCase);
        String outputFile = processProguardRules(tmpDir, trueTestCase);

        // convert jar to dex use R8
        String r8_cmd = "java -cp libs/r8.jar com.android.tools.r8.R8 " +
                "--pg-conf " + outputFile + " --min-api 24 --release --output " + tmpDir + " " + tmpDir + "/classes.jar";
        log.debug("r8 command: \n" + r8_cmd);
        Process proc2 = runCmd(r8_cmd);
        ShellUtils.getShellOut(proc2, 2, tmpDir + "r8.log");


        // check the dex file
        if (!new File(tmpDir + "/classes.dex").exists()) {
            throw new RuntimeException(" r8 failed");
        }

        // env ANDROID_LOG_TAGS=*:f ANDROID_DATA=<data_dir> --64 --no-compile -- -cp <dex_file> <class_name>
        // Example: env  ANDROID_LOG_TAGS=*:f ANDROID_DATA=/data2/qiusy/projects/Artemis/release/artemis/jaf/1/mutants/5/android-data-v5budj38 /data2/qiusy/android-src-repo/out/host/linux-x86/bin/art --64 --no-compile -- -cp /data2/qiusy/projects/Artemis/release/artemis/jaf/1/mutants/5/classes.dex   Test
        // String art_cmd = "env ANDROID_LOG_TAGS=*:f ANDROID_DATA=" + tmpDir + "/android-data " + this.art + " --64 --no-compile -- -cp " + tmpDir + "/classes.dex " + trueTestCase;
        String art_high_prefix = "env ANDROID_LOG_TAGS=*:f ANDROID_DATA=" + tmpDir + "/android-data ";
        String art_high_infix = " --64 --no-compile  --";
        String art_cmd = null;
        if (version == 8 || version == 7) {
            new File(tmpDir + "/android-data").mkdir();
            art_cmd = art_high_prefix + this.art + " --64 -cp " + tmpDir + "/classes.dex " + trueTestCase;
        } else {
            art_cmd = art_high_prefix + this.art + art_high_infix + " -cp " + tmpDir + "/classes.dex " + trueTestCase;
        }
        return art_cmd;
    }


    public String getRandomOption() {
        if (isOpenJDK) {
            switch (new Random().nextInt(2)) {
                case 0:
                    return genRandomSubOpions();
                case 1:
                    return genRandomSubOpions();
            }
        } else if (isJ9) {
            switch (new Random().nextInt(2)) {
                case 0:
                    return " -Xjit:optLevel=veryhot ";
                case 1:
                    return "";
            }
        }
        return "";
    }

    private String genRandomSubOpions() {
        String ops = "";
        Random r = new Random();
        for (int i = 0; i < 5; i++) {
            int index = r.nextInt(Option.values().length);
            ops += makeOption(String.valueOf(Option.values()[index]), r.nextInt(2));
        }
        return ops;
    }

    private String makeOption(String option, int i) {
        if (option.isEmpty()) {
            return "";
        }
        int[] result = Option.valueOf(option).getRange();
        if (result[2] != 1) {
            if (i == 1) return " -XX:" + option + "=" + result[2] + " ";
            else return " -XX:" + option + "=" + result[1] + " ";
        } else {
            if (i == 1) return " -XX:+" + option + " ";
            else return " -XX:-" + option + " ";
        }
    }

    public String getRandomGCOptions() {
        if (!Configuration.useRandomGCOptions)
            return "";
        if (isJ9 || isGraal || isART)
            return "";
        if (version != 8) {
            return GetGCOptions.GetRandomGCOptions("g1gc");
        } else
            return GetGCOptions.GetRandomGCOptions("parallelgc");

    }

    public String getTestLibRoot() {
        return testLibRoot;
    }
}
