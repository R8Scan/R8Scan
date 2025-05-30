# R8Scan

R8Scan is a tool for automated testing of the R8, utilizing optimization-triggering functions that extract from
real-world Java projects.

## Prompt Templates of R8Scan
<details>
<summary><b>The Prompt Templates of R8Scan</b></summary>
 
```
**[TASK]** Analyze the following Java function and construct up to three sets of argument expression using typed variables(e.g., int_var) to cover as many code lines as possible. Assume the typed variables can hold any non-null value, and ensure the arguments are valid without exception.

**[Java Function #1]**
int fun(int a, String b) {
    int ret = 0;
    if (a>10) {
        ret += a;    
    } else if (b.length() < 20) {
        ret += b.length();
    } else {
        ret += a + b.length();
    }
    return ret;
}

**[Function Argument Expression Sets #1]**
Argument Expression Set 1:
    a = int_var1*int_var1 + 10;
    b = int_arr_var1;
Argument Expression Set 2:
    a = -Math.abs(int_var1);
    b = String_var1.substring(0, Math.min(String_var1.length(), 19));
Argument Expression Set 3:
    a = -Math.abs(int_var1);
    b = text = String_var1 + "aaaaaaaaaaaaaaaaaaaa";

**[Java Function #2]**
int method(StringBuilder sb, String field, String value, String otherValue, int wrap) throws Exception {
    sb.append("<tr><td style='margin-left: 5pt; margin-right: 25pt;").append("white-space: nowrap' valign='top'>").append(field).append("</td><td><span");
    if (otherValue != null) {
        sb.append('>').append(value).append("</span><br/><span");
        value = otherValue;
    }
    if (wrap > 0) {
        sb.append(" style='font-family:monospace'>");
        for (int i = 0; i < value.length(); i++) {
            if (((i % wrap) == 0) && (i > 0)) {
                sb.append("<br/>");
            }
            sb.append(value.charAt(i));
        }
    } else {
        sb.append(">");
        sb.append(value);
    }
    sb.append("</span></td></tr>");
    return wrap;
}

**[Function Argument Expression Sets #2]**
Argument Expression Set 1:
    sb = StringBuilder_var1
    field = String_var1
    value = String_var2
    otherValue = String_var2.concat("abcde")
    wrap = 2
Argument Expression Set 2:
    sb = StringBuilder_var1
    field = String_var1
    value = "s"
    otherValue = null
    wrap = Math.abs(int_var1) + 2
Argument Expression Set 3:
    sb = StringBuilder_var1
    field = String_var1
    value = String_var2
    otherValue = null
    wrap = 0 - Math.abs(int_var1)


**[Java Function #3]**
int method(int index, boolean selected) throws Exception {
    if (true) {
        if (selected) { ... }
        if (index == (-1)) { ... } else { ... }
    } else {
        if (selected) { ... }
        if (index == (-1)) { ... } else { ... }
    }
    return index;
}

**[Function Argument Expression Sets #3]**
Argument Expression Set 1:
    index = -1
    selected = true
Argument Expression Set 2:
    index = int_var1
    selected = true
Argument Expression Set 3:
    index = int_var1
    selected = false

**[Java Function #4]**
int method() throws java.lang.Exception {
    int ret = 0;
    if (true) {
        ret |= 1;
    }
    if (true) {
        ret |= 2;
    }
    if (true) {
        ret |= 4;
    }
    if (true) {
        ret |= 8;
    }
    return ret;
}

**[Function Argument Expression Sets #4]**
No param need to construct.

**[Java Function #5]**
int method(Map<String, Object> configuration) throws Exception {
    String refreshIntervalString = ((String) (configuration.get("refresh")));
    int port = 80;
    String host = ((String) (configuration.get("host")));
    String username = ((String) (configuration.get("username")));
    String password = ((String) (configuration.get("password")));
    if (true) {
        ;
    }
    String portString = ((String) (configuration.get("port")));
    if (true) {
        port = Integer.parseInt(portString);
    }
    String prefix = "";
    if (username != null) {
        prefix = ((username + ":") + password) + "@";
    }
    return port;
}

**[Function Argument Expression Sets #5]**
Argument Expression Set 1:
configuration = new java.util.HashMap<java.lang.String, java.lang.Object>() {{ put("refresh", "string_var1"); put("host", "string_var2"); put("username", "string_var3"); put("password", "string_var4"); put("port", "int_var1"); }}
Argument Expression Set 2:
configuration = new java.util.HashMap<java.lang.String, java.lang.Object>() {{ put("refresh", "string_var1"); put("host", "string_var2"); put("username", null); put("password", "string_var3"); put("port", "int_var1"); }}
Argument Expression Set 3:
configuration = new java.util.HashMap<java.lang.String, java.lang.Object>() {{ put("refresh", "string_var1"); put("host", "string_var2"); put("username", "string_var3"); put("password", "string_var4"); }}

**[Java Function #6]**
int method(int[] nums, int target) throws Exception {
    int left = 0;
    int right = nums.length - 1;
    while (left <= right) {
        int mid = left + ((right - left) / 2);
        if (nums[mid] < target) {
            left = mid + 1;
        } else if (nums[mid] > target) {
            right = mid - 1;
        } else if (nums[mid] == target) {
            right = mid - 1;
        }
    }
    System.out.print(("left ->" + left) + " ");
    if ((left != nums.length) && (nums[left] == target)) {
        return left;
    }
    return -1;
}

**[Function Argument Expression Sets #6]**
Argument Expression Set 1:
    nums = new int[]{1, 3, 5, 7, 9}
    target = int_var1 % 5
Argument Expression Set 2:
    nums = new int[]{1, 3, 5, 7, 9}
    target = Math.abs(int_var1) + 9
Argument Expression Set 3:
    nums = new int[]{}
    target = int_var1

**[Java Function #7]**
int method(double priority, double multiplier) throws Exception {
    int result = switch(Double.compare(priority, 0.5)) {
        case -1 -> {
            String message = "Priority is less than 0.5";
            System.out.println(message);
            int temp = (int) (priority * 2);
            yield (int) ((temp * multiplier) + 1);
        }
        case 0 -> {
            String message = "Priority is exactly 0.5";
            System.out.println(message);
            int temp = 5;
            yield (int) ((temp * multiplier) + 2);
        }
        case 1 -> {
            String message = "Priority is greater than 0.5";
            System.out.println(message);
            int temp = (int) (priority * 3);
            yield (int) ((temp * multiplier) + 3);
        }
        default ->
            throw new Exception("Invalid priority value");
    };
    return result;
}
    
**[Function Argument Expression Sets #7]**
Argument Expression Set 1:
    priority = 0.4
    multiplier = double_var1
Argument Expression Set 2:
    priority = 0.5
    multiplier = double_var1
Argument Expression Set 3:
    priority = 0.6
    multiplier = double_var1

**[Java Function #8]**
int method(int left, int top, int right, int bottom) throws Exception {
    int width = right - left;
    int height = bottom - top;
    if (true) {
        int ox = 47;
        int oy = 72;
        float ratioWidth = (1.0F * width) / 83;
        float ratioHeight = (1.0F * height) / 36;
    } else {
    }
    return top + left + bottom + width + right + height;
}

**[Function Argument Expression Sets #8]**
Argument Expression Set 1:
    left = int_var1
    top = int_var2
    right = int_var3
    bottom = int_var4

**[Java Function #9]**
int method(int V, int E) throws Exception { 
     if (E > ((((long) (V)) * (V - 1)) / 2)) { 
         throw new IllegalArgumentException("Too many edges"); 
     } 
     if (E < (V - 1)) { 
         throw new IllegalArgumentException("Too few edges"); 
     } 
     // fix a topological order 
     int[] vertices = new int[V]; 
     for (int i = 0; i < V; i++) { 
         vertices[i] = i; 
     } 
     return E + V; 
 }
     
**[Function Argument Expression Sets #9]**
Argument Expression Set 1:
    V = int_var1
    E = (int) ((((long) (int_var1)) * (int_var1 - 1)) / 2) + 1
Argument Expression Set 2:
    V = int_var1
    E = int_var1 - 2
Argument Expression Set 3:
    V = int_var1
    E = int_var1 - 1

**[Java Function #10]**
int method(String dirName, List<String> excludes, int maxDepth) { 
     Path dir = Paths.get("src/test/resources/roms", dirName); 
     int depth = switch(maxDepth) { 
         case 1, 2, 3, 4 -> 
             maxDepth; 
         default -> 
             { 
                 if (maxDepth > 10) { 
                     yield 10; 
                 } else { 
                     yield maxDepth; 
                 } 
             } 
     }; 
     StringBuilder resultBuilder = new StringBuilder(); 
     for (String exclude : excludes) { 
         if (exclude.length() > 3) { 
             resultBuilder.append(exclude).append("Filtered "); 
         } 
     } 
     char[] resultCharArray = new char[resultBuilder.length()]; 
     resultBuilder.getChars(0, resultBuilder.length(), resultCharArray, 0); 
     for (char c : resultCharArray) { 
         System.out.print(c); 
     } 
     System.out.println(); 
     return depth; 
 }

**[Function Argument Expression Sets #10]**
Argument Expression Set 1:
    dirName = string_var1
    excludes = java.util.Arrays.asList(string_var2.substring(0, Math.min(string_var2.length(), 3)), string_var3 + "AAAA")
    maxDepth = int_var1 % 4 + 1
Argument Expression Set 2:
    dirName = string_var1
    excludes = java.util.Arrays.asList(string_var2 + "", string_var3 + "")
    maxDepth = Math.abs(int_var2) + 11
Argument Expression Set 3:
    dirName = string_var71   
    excludes = new java.util.ArrayList<java.lang.String>()
    maxDepth = (Math.abs(int_var1) % 6) + 5
```

</details>

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
```bash
$ unzip benchmark.zip
```

Unzip database.zip, which stores optimization-triggering functions.
```bash
$ unzip database.zip
```

#### Build OpenJDK

```bash
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

## Show Cases of Detected Bugs
<details>
<summary><b>R8 bug: IssueTracker-358913905</b></summary>


[IssueTracker-358913905: java.lang.NullPointerException](https://issuetracker.google.com/issues/358913905)

```java
class Test {
  public static void main(String[] args) {
    Object object = new Object();
    if (object instanceof int[]) {
      int[] object2 = (int[])object;
    }
    System.out.println("over");
  }
}
```

</details>

The cause of this bug is improper handling of type checks and casts in the R8 optimization phase. The bug involves the `TrivialCheckCastAndInstanceOfRemover` optimization step, where R8 incorrectly analyzes and removes trivial `instanceof` and cast operations.

R8 attempts to eliminate unnecessary type checks by focusing only on the upper bound of the type hierarchy (e.g., `Object`), but it fails to consider the actual runtime type of the object. In the provided code, the `instanceof` check against `int[]` is always false, and the cast operation is never executed. However, R8 incorrectly assumes the cast is trivial and removes it, leading to crashes in the optimized code.

<details>
<summary><b>R8 bug: IssueTracker-371247958</b></summary>


[IssueTracker-371247958: Inconsistent Result in R8+ART When Handling EOFException](https://issuetracker.google.com/issues/371247958)

```java
class Test {
  int a;
  long b;
  static int c(java.io.EOFException d, boolean f) throws Exception {
    if (f)
      return 1;
    throw d;
  }

  public static void main(String[] p) throws Exception {
    int j = 41;
    java.io.EOFException k = new java.io.EOFException();
    boolean l = false;
    j = c(k, l);
    System.out.println(j);
  }
}
```

</details>

The cause of this bug is improper handling of exception throwing and inlining during the R8 optimization process. The bug involves the inlining of methods that contain exception handling code, specifically the `EOFException`.

R8 attempts to inline methods for optimization, but during the inlining process, it incorrectly eliminates parts of the code that are responsible for throwing the exception. This happens because R8's inlining analysis does not correctly identify the importance of exception handling in certain scenarios. As a result, the exception is not thrown as expected during runtime, leading to a failure in exception reporting and inconsistent program behavior.

<details>
<summary><b>ART bug: IssueTracker-350540996</b></summary>


[IssueTracker-350540996: Incorrect Output of Program Using ART Possibly Due to JIT](https://issuetracker.google.com/issues/350540996)

```java
class Test {
  long a; byte b; int u; long c; int v;
  void e(int f, int g) { c = f; }
  void h(long j, int k, int l) { e(k, k); }
  public static void main(String[] m) {
    try {
      Test n = new Test();
      for (int i = 0; i < 10; i++)
        n.o(m);
    } catch (Exception ex) {
    }
  }
  void p(int w) {
    int q, r = 24;
    h(a, w, w);
    b -= v;
    for (q = 1; q < 12; q++)
      v = r;
  }
  void o(String[] s) {
    double d = 118.89497;
    p(u);
    u = b;
    for (int t = 0; t < 20000; ++t)
      u -= d;
    System.out.println("" + c);
  }
}
```

</details>

The bug arises from improper handling of the `is_min` value in the `UseFullTripCount` case. In ART, this information is propagated and used across multiple functions, and overwriting `is_min` in this case disrupts the consistency of that information. Since `is_min` is a critical value shared between several functions, it must retain its current value to ensure correctness in the optimization process.

Specifically, overwriting `is_min` in the `UseFullTripCount` scenario leads to incorrect optimization states, affecting subsequent logical decisions and potentially causing crashes or unintended behaviors. Therefore, it is essential that `is_min` is not overridden in this context, preserving the original value to maintain proper information flow and ensure the integrity of the optimization process.


## Confirmed Bugs

### R8 / ART / JVM bugs

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
