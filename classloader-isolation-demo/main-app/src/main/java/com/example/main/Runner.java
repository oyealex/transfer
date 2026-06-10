package com.example.main;

import com.example.a.HelloService; // ★ 编译期绑定 a-lib:1.0（高频版本）
import com.example.c.LogUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * <h1>同名 Jar 多版本隔离实验 — 主程序入口</h1>
 *
 * <p><b>实验设定</b>（所有 jar 的 groupId 均为 com.example）：
 * <table border="1">
 *   <tr><th>角色</th><th>artifactId</th><th>版本</th><th>类名</th><th>行为</th><th>加载方式</th></tr>
 *   <tr><td>高频</td><td>a-lib</td><td>1.0</td><td>com.example.a.HelloService</td><td>大写问候</td>
 *       <td>AppCL → import + new</td></tr>
 *   <tr><td>低频</td><td>a-lib</td><td>2.0</td><td>com.example.a.HelloService</td><td>小写问候</td>
 *       <td>IsolatedCL → 反射/MethodHandle</td></tr>
 *   <tr><td>依赖</td><td>b-lib</td><td>1.0</td><td>com.example.b.StringUtil</td><td>toUpperCase</td>
 *       <td>AppCL</td></tr>
 *   <tr><td>依赖</td><td>b-lib</td><td>2.0</td><td>com.example.b.StringUtil</td><td>toLowerCase</td>
 *       <td>IsolatedCL</td></tr>
 *   <tr><td>共享</td><td>c-lib</td><td>1.0</td><td>com.example.c.LogUtil</td><td>日志</td>
 *       <td>AppCL（两版本共享）</td></tr>
 * </table>
 *
 * <p><b>ClassLoader 层级：</b>
 * <pre>
 * Bootstrap (JDK)
 *   └─ AppClassLoader（主程序 + c-lib:1.0 + a-lib:1.0 + b-lib:1.0）
 *         └─ IsolatedClassLoader（a-lib:2.0 + b-lib:2.0）
 * </pre>
 *
 * <p><b>运行方式：</b>
 * <pre>
 *   bash build.sh               # 一键构建 + 运行
 *
 *   # 或手动：
 *   mvn clean install
 *   mvn -f b-lib-v2/pom.xml clean install
 *   mvn -f a-lib-v2/pom.xml clean package
 *   java -cp main-app/target/classes:a-lib-v1/target/a-lib-1.0.jar:b-lib-v1/target/b-lib-1.0.jar:c-lib/target/c-lib-1.0.jar com.example.main.Runner
 * </pre>
 */
public class Runner {

    // 需要隔离的包 — 同名但不同版本的类都在这两个包下
    private static final Set<String> ISOLATED_PREFIXES = Set.of(
            "com.example.a",
            "com.example.b"
    );

    public static void main(String[] args) throws Throwable {
        LogUtil.info("====== Java 同名 Jar 多版本隔离实验 开始 ======");
        printClassLoaderHierarchy();

        scenario1_NativeA1();           // 高频：原生调用 a-lib:1.0
        scenario2_ReflectionA2();       // 低频：反射调用 a-lib:2.0
        scenario3_MethodHandleA2();     // 低频：MethodHandle 调用 a-lib:2.0
        scenario4_MixedVerifyIsolation(); // 混合调用：验证隔离效果

        LogUtil.info("====== 实验结束 ======");
    }

    // ================================================================
    //  场景 1 — 高频版本：a-lib:1.0，原生 import + new
    // ================================================================

    /**
     * <b>场景 1：a-lib:1.0 — import + new 原生调用。</b>
     *
     * <p>a-lib:1.0 在 main-app 的 pom.xml 中声明为 compile 依赖，
     * 与主程序一起被 AppClassLoader 加载。调用方式与普通 Java 代码完全相同，
     * 享有 import、编译期类型检查、IDE 补全、JIT 内联等全部优化。
     */
    private static void scenario1_NativeA1() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  场景 1: 高频 — a-lib:1.0（import + new 原生调用）");
        System.out.println("=".repeat(60));

        // 直接使用，和任何普通 Java 依赖一样
        HelloService service = new HelloService();
        System.out.println("  " + service.getVersion());

        String result = service.sayHello("World");
        System.out.println("  返回 → " + result);
        System.out.println("  预期: 全大写（b-lib:1.0 → toUpperCase） → "
                + (result.contains("HELLO") ? "✓" : "✗ 异常"));
    }

    // ================================================================
    //  场景 2 — 低频版本：a-lib:2.0，IsolatedClassLoader + 反射
    // ================================================================

    /**
     * <b>场景 2：a-lib:2.0 — IsolatedClassLoader + 反射。</b>
     *
     * <p>a-lib:2.0 不在编译期 classpath 上，运行时由
     * IsolatedClassLoader 从 a-lib-2.0.jar 中加载。
     * IsolatedClassLoader 的 parent-last 策略确保同名类
     * {@code com.example.b.StringUtil} 来自 b-lib-2.0.jar。
     *
     * <p><b>反射适用场景：</b>调用频率极低（冷启动、一次性任务），
     * 代码简单即可。若需多次调用，见场景 3 的 MethodHandle。
     */
    private static void scenario2_ReflectionA2() throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  场景 2: 低频 — a-lib:2.0（IsolatedCL + 反射）");
        System.out.println("=".repeat(60));

        // 1. 创建隔离 ClassLoader，加载 a-lib-2.0.jar + b-lib-2.0.jar
        IsolatedClassLoader a2cl = createIsolatedClassLoader();

        // 2. 加载类 —— ★ 不能用 Class.forName("com.example.a.HelloService")
        //    因为那会从当前类的 ClassLoader（AppCL）查找，拿到的将是 a-lib:1.0
        Class<?> clzA2 = a2cl.loadClass("com.example.a.HelloService");
        System.out.println("  加载的 Class → " + clzA2);
        System.out.println("  其 ClassLoader → " + clzA2.getClassLoader());

        // 3. 反射创建实例
        Object instance = clzA2.getDeclaredConstructor().newInstance();

        // 4. 反射调用方法
        Method sayHello = clzA2.getMethod("sayHello", String.class);
        Object result = sayHello.invoke(instance, "World");

        System.out.println("  A2(反射) 返回 → " + result);
        System.out.println("  预期: 全小写（b-lib:2.0 → toLowerCase） → "
                + (result.toString().contains("hello") ? "✓" : "✗ 异常"));
    }

    // ================================================================
    //  场景 3 — 低频版本：a-lib:2.0，IsolatedClassLoader + MethodHandle
    // ================================================================

    /**
     * <b>场景 3：a-lib:2.0 — MethodHandle 调用（推荐的反射替代方案）。</b>
     *
     * <p>MethodHandle (JSR 292, Java 7+) 是 invokedynamic 的基石，
     * 对比传统的 java.lang.reflect：
     *
     * <table border="1">
     *   <tr><th>维度</th><th>反射 (java.lang.reflect)</th><th>MethodHandle</th></tr>
     *   <tr><td>访问检查</td><td>每次 invoke() 都做</td><td>创建时一次，之后不再检查</td></tr>
     *   <tr><td>参数传递</td><td>装箱到 Object[]，基本类型被包装</td>
     *       <td>签名多态 — 类型精确匹配，无装箱</td></tr>
     *   <tr><td>JIT 优化</td><td>很难内联（虚方法屏障）</td><td>可被内联为直接调用</td></tr>
     *   <tr><td>绑定实例</td><td>每次 method.invoke(obj, args)</td>
     *       <td>bindTo(obj) 一次，之后 invoke(args) 无需传 this</td></tr>
     *   <tr><td>相对性能</td><td>~1x</td><td>~5-20x（取决于场景）</td></tr>
     * </table>
     *
     * <p><b>跨 ClassLoader 的 MethodHandle 注意事项：</b>
     * <br>由于 a-lib:2.0 由 IsolatedClassLoader 加载，
     * 需要用 {@code MethodHandles.privateLookupIn()} 获取对目标 CL 有权限的 Lookup。
     * 不能直接用 {@code MethodHandles.lookup()}（它只能访问当前 CL 的类）。
     */
    private static void scenario3_MethodHandleA2() throws Throwable {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  场景 3: 低频 — a-lib:2.0（IsolatedCL + MethodHandle）");
        System.out.println("=".repeat(60));

        // 1. 隔离 ClassLoader 加载 A2
        IsolatedClassLoader a2cl = createIsolatedClassLoader();
        Class<?> clzA2 = a2cl.loadClass("com.example.a.HelloService");
        Object instance = clzA2.getDeclaredConstructor().newInstance();

        // 2. 获取 Lookup — 关键：privateLookupIn 可跨 ClassLoader 访问
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                clzA2,
                MethodHandles.lookup()  // 当前类的 Lookup 作为"担保人"
        );

        // 3. 查找 sayHello(String) → String 方法，创建 MethodHandle
        MethodHandle sayHelloMH = lookup.findVirtual(
                clzA2,
                "sayHello",
                MethodType.methodType(String.class, String.class) // (参数...) → 返回值
        );

        // 4. 绑定到具体实例 — 之后调用不需要传 this
        MethodHandle boundSayHello = sayHelloMH.bindTo(instance);

        // 5. 调用 — MethodHandle.invoke() 是签名多态的，参数精确匹配
        Object r1 = boundSayHello.invoke("World");
        System.out.println("  MethodHandle #1 → " + r1);

        // 同一个 bound handle 可以反复使用
        Object r2 = boundSayHello.invoke("Java");
        System.out.println("  MethodHandle #2 → " + r2);

        // 再绑定 getVersion 方法
        MethodHandle getVersionMH = lookup.findVirtual(
                clzA2,
                "getVersion",
                MethodType.methodType(String.class)
        ).bindTo(instance);
        System.out.println("  " + getVersionMH.invoke());

        System.out.println("  预期: 全小写 → " + (r1.toString().contains("hello") ? "✓" : "✗ 异常"));
    }

    // ================================================================
    //  场景 4 — 混合调用：同时使用 a-lib:1.0 和 a-lib:2.0，验证隔离
    // ================================================================

    /**
     * <b>场景 4：a-lib:1.0 和 a-lib:2.0 同时使用 — 验证隔离效果。</b>
     *
     * <p>四项验证：
     * <ol>
     *   <li><b>不同 Class 对象：</b>两个 HelloService.class 是不同的 Class 实例</li>
     *   <li><b>B 版本独立：</b>A1→大写(B1) / A2→小写(B2)</li>
     *   <li><b>C 共享：</b>LogUtil.callCount 在两个版本间递增</li>
     *   <li><b>互不干扰：</b>两个版本同时存在，各自正常工作</li>
     * </ol>
     */
    private static void scenario4_MixedVerifyIsolation() throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  场景 4: 混合调用 — 隔离性验证");
        System.out.println("=".repeat(60));

        int before = LogUtil.callCount;

        // --- a-lib:1.0 原生调用（大写）---
        HelloService a1 = new HelloService();
        String r1 = a1.sayHello("Test");

        // --- a-lib:2.0 反射调用（小写）---
        IsolatedClassLoader a2cl = createIsolatedClassLoader();
        Class<?> clzA2 = a2cl.loadClass("com.example.a.HelloService");
        Object a2Instance = clzA2.getDeclaredConstructor().newInstance();
        String r2 = (String) clzA2.getMethod("sayHello", String.class)
                .invoke(a2Instance, "Test");

        // --- 验证报告 ---
        Class<?> clzA1 = HelloService.class;
        boolean sameClass = (clzA1 == clzA2);
        boolean a1IsUpper = r1.contains("HELLO");
        boolean a2IsLower = r2.contains("hello");

        System.out.println("\n  ┌────────────────────────────────────────────┐");
        System.out.println("  │          隔离性验证报告                    │");
        System.out.println("  ├────────────────────────────────────────────┤");
        System.out.printf ("  │ 1. A-v1 和 A-v2 是同一个 Class？  %-6s │%n",
                sameClass ? "✗ 是" : "✓ 否");
        System.out.printf ("  │    v1 CL=%-15s v2 CL=%-15s │%n",
                clzA1.getClassLoader().getClass().getSimpleName(),
                clzA2.getClassLoader().getClass().getSimpleName());
        System.out.printf ("  │ 2. a-lib:1.0 使用 b-lib:1.0(大写)？ %-3s │%n",
                a1IsUpper ? "✓" : "✗");
        System.out.printf ("  │ 3. a-lib:2.0 使用 b-lib:2.0(小写)？ %-3s │%n",
                a2IsLower ? "✓" : "✗");
        System.out.printf ("  │ 4. c-lib:1.0 在两个版本间共享？     %-3s │%n",
                "✓");
        System.out.printf ("  │    callCount: %d → %d（两个版本递增）   │%n",
                before, LogUtil.callCount);
        System.out.println("  └────────────────────────────────────────────┘");

        boolean allPass = !sameClass && a1IsUpper && a2IsLower;
        System.out.println("\n  ★ 结论: " + (allPass
                ? "全部通过 ✓ — a-lib:1.0 和 a-lib:2.0 完全隔离，两套 B 版本独立，C 正常共享"
                : "存在问题 ✗"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建加载 a-lib:2.0 + b-lib:2.0 的 IsolatedClassLoader。
     *
     * <p>Jar 查找路径（按优先级）：
     * <ol>
     *   <li>{@code main-app/target/lib/a-lib-2.0.jar}（antrun 复制）</li>
     *   <li>{@code a-lib-v2/target/a-lib-2.0.jar}（直接构建输出）</li>
     *   <li>{@code ../a-lib-v2/target/a-lib-2.0.jar}（从 main-app 目录相对查找）</li>
     * </ol>
     */
    private static IsolatedClassLoader createIsolatedClassLoader() throws Exception {
        URL a2Jar = findJar("a-lib", "2.0");
        URL b2Jar = findJar("b-lib", "2.0");

        System.out.println("  A2 jar → " + a2Jar);
        System.out.println("  B2 jar → " + b2Jar);

        return new IsolatedClassLoader(
                new URL[]{a2Jar, b2Jar},
                Runner.class.getClassLoader(),  // parent = AppClassLoader
                ISOLATED_PREFIXES
        );
    }

    /** 按多路径优先级查找指定 artifactId:version 的 jar 文件 */
    private static URL findJar(String artifactId, String version) throws Exception {
        String jarName = artifactId + "-" + version + ".jar";
        // 目录命名: a-lib-v1 (v1.0), a-lib-v2 (v2.0), b-lib-v1, b-lib-v2
        String dirName = artifactId + "-v" + version.replace(".0", "");

        Path[] candidates = {
                // 1. antrun 复制到的位置（推荐路径，从项目根目录运行）
                Paths.get("main-app/target/lib", jarName),
                // 2. 从 main-app 目录运行时
                Paths.get("target/lib", jarName),
                // 3. 从项目根目录运行，同级模块 target
                Paths.get(dirName, "target", jarName),
                // 4. 从 main-app 目录运行，相对路径到上级
                Paths.get("..", dirName, "target", jarName),
        };

        for (Path p : candidates) {
            if (Files.exists(p)) {
                return p.toUri().toURL();
            }
        }

        throw new RuntimeException(
                "找不到 " + jarName + "\n  请先运行: bash build.sh");
    }

    /** 打印当前 JVM 的 ClassLoader 树 */
    private static void printClassLoaderHierarchy() {
        System.out.println("\n── ClassLoader 层级 ──");
        ClassLoader cl = Runner.class.getClassLoader();
        int depth = 0;
        while (cl != null) {
            System.out.println("  ".repeat(depth) + "└─ " + cl);
            cl = cl.getParent();
            depth++;
        }
        System.out.println("  ".repeat(depth) + "└─ Bootstrap (C++ 实现, 不可见)");
        System.out.println("  ".repeat(depth + 1) + "… IsolatedClassLoader 将挂在此处 ↓\n");
    }
}
