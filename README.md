# R8Scan

R8Scan is a tool for automated testing of the R8, utilizing optimization-triggering functions that extract from
real-world Java projects.

## Usage of R8Scan

### Step 1: Prerequisites

R8Scan needs the debug build of JVM and ART, so users should download the source code of JVM and set the debug flag.

- Linux environment for running ART
- Java 17+ to run this project
- Maven
- Lasted build of OpenJDK
- Lasted build of ART
- Lasted build of R8

####  Unzip
Unzip benchmark.zip, which stores the test cases generated by JavaFuzzer.
```
$ unzip benchmark.zip
```

Unzip database.zip, which stores optimization-triggering functions.
```
$ unzip database.zip
```

#### Build OpenJDK

```
$ git clone https://github.com/openjdk/jdk.git
$ cd jdk
$ bash configure --enable-debug
$ make images JOBS=40
```

#### Build ART

please follow the AOSP build process [link](https://source.android.google.cn/docs/setup/build/building)

#### Build R8

please follow the R8 build process [link](https://r8.googlesource.com/r8/)

### Step 2: Run the tool


To use R8Scan, users should specify the target test case, the path of the debug build of JVM (at least one),
We will check whether the outputs between OpenJDK and ART are consistant (if perform differential fuzzing). The following is
Parameters of running R8Scan.

```
usage: help [-jdk <arg>] [-project_path <arg>] [-target_case <arg>] [-line_number <arg>] [-is_use_jtreg <arg>] [-max_iter <arg>] [-?] [-enable_profile_guide <arg>]

 -?,--help                     Print this help message
 --func_map_path <arg>         The file record the mapping of functions and passes. e.g., /path/to/func_triggered_passes.txt. Necessary
 --func_path <arg>             Use function to synthesize mutants? e.g., /path/to/func_path. Necessary
 --project_path <arg>          Source code root path. e.g., /home/user/benchmark/JavaFuzzer. Necessary.
 --target_case <arg>           Target java file. e.g., a.b.c denotes a/b/c.java. Necessary.
 --jdk_art <arg>               The jdk and art directory. e.g., path/to/jdk/bin;path/to/art/bin. Necessary
                               We will check whether the outputs of these JDKs are consistent. 
```

**Example Command:**

```bash
# differential testing of among three compilers
path/to/java17/bin/java -jar R8Scan.jar --project_path benchmarks/JavaFuzzer/tests/  --target_case Test0001 --jdk_art path/to/targetJDK1/bin/,path/to/ART/bin/ --func_path database --func_map_path database/func_triggered_passes.txt
```

## Confirmed Bugs

### R8 / JVM / ART bugs

*Detection indicates whether the bug can be detected by any baselines.*

| **Bug ID**      | **Compiler** | **Symptoms**          | **Affected Component** | **Status**  | **Priority Level** | **Link**                                         |
|-----------------|--------------|-----------------------|------------------------|-------------|--------------------|--------------------------------------------------|
| Issue-341618078 | R8           | Inconsistant Semantic | Optimization           | Fixed       | P1                 | https://issuetracker.google.com/issues/341618078 |
| Issue-342067836 | R8           | Inconsistant Semantic | Optimization           | Fixed       | P1                 | https://issuetracker.google.com/issues/342067836 |
| Issue-344363462 | R8           | Inconsistant Semantic | Optimization           | Fixed       | P1                 | https://issuetracker.google.com/issues/344363462 |
| Issue-348499741 | R8           | Inconsistant Semantic | Parsing                | Fixed       | P1                 | https://issuetracker.google.com/issues/348499741 |
| Issue-354625681 | R8           | Inconsistant Syntax   | Optimization           | Fixed       | P1                 | https://issuetracker.google.com/issues/354625681 |
| Issue-354625682 | R8           | Inconsistant Syntax   | Optimization           | Fixed       | P1                 | https://issuetracker.google.com/issues/354625682 |
| Issue-358913905 | R8           | Crash                 | Optimization           | Fixed       | P1                 | https://issuetracker.google.com/issues/358913905 |
| Issue-369739224 | R8           | Inconsistant Semantic | Dexing                 | Fixed       | P1                 | https://issuetracker.google.com/issues/369739224 |
| Issue-371247958 | R8           | Inconsistant Semantic | Optimization           | Fixed       | P1                 | https://issuetracker.google.com/issues/371247958 |
| Issue-379347946 | R8           | Inconsistant Semantic | Optimization           | Fixed       | P1                 | https://issuetracker.google.com/issues/379347946 |
| Issue-384844007 | R8           | Inconsistant Semantic | Parsing                | Fixed       | P1                 | https://issuetracker.google.com/issues/384844007 |
| Issue-367915233 | R8           | Inconsistant Syntax   | Optimization           | Fixed       | P2                 | https://issuetracker.google.com/issues/367915233 |
| Issue-379347949 | R8           | Inconsistant Semantic | Dexing                 | Fixed       | P2                 | https://issuetracker.google.com/issues/379347949 |
| Issue-380109542 | R8           | Inconsistant Syntax   | Optimization           | Fixed       | P2                 | https://issuetracker.google.com/issues/380109542 |
| Issue-380182105 | R8           | Inconsistant Semantic | Optimization           | Fixed       | P2                 | https://issuetracker.google.com/issues/380182105 |
| Issue-359102835 | R8           | Crash                 | Optimization           | Fixed       | P3                 | https://issuetracker.google.com/issues/359102835 |
| Issue-370217723 | R8           | Inconsistant Semantic | Optimization           | Fixed       | P3                 | https://issuetracker.google.com/issues/370217723 |
| Issue-379241435 | R8           | Inconsistant Semantic | Shrinking              | In Progress | P2                 | https://issuetracker.google.com/issues/379241435 |
| Issue-353143650 | R8           | Inconsistant Semantic | Shrinking              | In Progress | P2                 | https://issuetracker.google.com/issues/353143650 |
| Issue-372806451 | R8           | Inconsistant Semantic | Optimization           | Duplicate   | P2                 | https://issuetracker.google.com/issues/372806451 |
| Issue-350540996 | ART          | Inconsistant Semantic | JIT Optimization       | Fixed       | P2                 | https://issuetracker.google.com/issues/350540996 |
| Issue-351868772 | ART          | Crash                 | JIT Optimization       | Fixed       | P2                 | https://issuetracker.google.com/issues/351868772 |
| Issue-368984521 | ART          | Inconsistant Semantic | JIT Optimization       | Fixed       | P2                 | https://issuetracker.google.com/issues/368984521 |
| Issue-341476044 | ART          | Inconsistant Semantic | JIT Optimization       | In Progress | P2                 | https://issuetracker.google.com/issues/341476044 |
| Issue-369670481 | ART          | Inconsistant Semantic | LibCore                | In Progress | P3                 | https://issuetracker.google.com/issues/369670481 |
| Issue-369739225 | ART          | Inconsistant Semantic | LibCore                | In Progress | P3                 | https://issuetracker.google.com/issues/369739225 |
| Issue-370303498 | ART          | Inconsistant Semantic | LibCore                | In Progress | P3                 | https://issuetracker.google.com/issues/370303498 |
| Issue-347706992 | ART          | Inconsistant Semantic | JIT Optimization       | Duplicate   | P2                 | https://issuetracker.google.com/issues/347706992 |
| JBS-9077400     | OpenJDK      | Crash                 | JIT C2 Compiler        | Fixed       | P2                 | https://bugs.openjdk.org/browse/JDK-9077400      |
| JBS-9077278     | OpenJDK      | Crash                 | JIT C2 Compiler        | Fixed       | P3                 | https://bugs.openjdk.org/browse/JDK-9077278      |
| JBS-9077593     | OpenJDK      | Inconsistant Semantic | JIT C2 Compiler        | In Progress | P3                 | https://bugs.openjdk.org/browse/JDK-9077593      |
| JBS-9077067     | OpenJDK      | Crash                 | Javac                  | In Progress | P4                 | https://bugs.openjdk.org/browse/JDK-9077067      |
| JBS-9077257     | OpenJDK      | Inconsistant Semantic | JIT C2 Compiler        | Duplicate   | P2                 | https://bugs.openjdk.org/browse/JDK-9077257      |
| JBS-9077247     | OpenJDK      | Inconsistant Semantic | JIT C2 Compiler        | Duplicate   | P2                 | https://bugs.openjdk.org/browse/JDK-9077247      |
| JBS-9077393     | OpenJDK      | Crash                 | JIT C2 Compiler        | Duplicate   | P2                 | https://bugs.openjdk.org/browse/JDK-9077393      |
| JBS-9077215     | OpenJDK      | Crash                 | JIT C2 Compiler        | Duplicate   | P2                 | https://bugs.openjdk.org/browse/JDK-9077215      |
| JBS-9077754     | OpenJDK      | Crash                 | JIT C2 Compiler        | Duplicate   | P4                 | https://bugs.openjdk.org/browse/JDK-9077754      |
| JBS-9077755     | OpenJDK      | Crash                 | Javac                  | Duplicate   | P4                 | https://bugs.openjdk.org/browse/JDK-9077755      |
