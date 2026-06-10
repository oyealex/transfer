package com.example.main;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

/**
 * <h1>隔离 ClassLoader — parent-last 策略实现同名 Jar 多版本共存</h1>
 *
 * <p><b>GAV 关系一览：</b>
 * <table border="1">
 *   <tr><th>概念</th><th>groupId</th><th>artifactId</th><th>version</th><th>加载方式</th></tr>
 *   <tr><td>Jar A v1</td><td>com.example</td><td>a-lib</td><td>1.0</td><td>AppClassLoader</td></tr>
 *   <tr><td>Jar A v2</td><td>com.example</td><td>a-lib</td><td>2.0</td><td>IsolatedClassLoader</td></tr>
 *   <tr><td>Jar B v1</td><td>com.example</td><td>b-lib</td><td>1.0</td><td>AppClassLoader</td></tr>
 *   <tr><td>Jar B v2</td><td>com.example</td><td>b-lib</td><td>2.0</td><td>IsolatedClassLoader</td></tr>
 *   <tr><td>Jar C</td><td>com.example</td><td>c-lib</td><td>1.0</td><td>AppClassLoader（共享）</td></tr>
 * </table>
 *
 * <h2>核心原理：parent-last 加载策略</h2>
 *
 * <p>标准 Java 双亲委派模型是 <b>parent-first</b>：
 * <pre>
 *   1. findLoadedClass(name)       — 自己是否已加载过？
 *   2. parent.loadClass(name)       — 优先委托父 ClassLoader
 *   3. findClass(name)              — 父找不到，自己找
 * </pre>
 *
 * <p>本 ClassLoader 对<b>隔离包</b>采用 <b>parent-last</b>：
 * <pre>
 *   1. findLoadedClass(name)       — 自己是否已加载过？
 *   2. 判断是否为隔离包？
 *       是 → findClass(name)      — 先自己找（parent-last）
 *       否 → parent.loadClass(name) — 正常委派（parent-first）
 *   3. fallback 到另一条路径
 * </pre>
 *
 * <p><b>为什么要 parent-last？</b>
 * <br>因为 parent（AppClassLoader）中已经存在 b-lib:1.0 的
 * {@code com.example.b.StringUtil}。如果走 parent-first，
 * a-lib:2.0 请求 B 的类时会优先匹配到 b-lib:1.0，导致版本错乱。
 *
 * <p><b>示例：a-lib:2.0 调用 sayHello() 时的类加载过程</b>
 * <pre>
 * com.example.a.HelloService    → 隔离包 → findClass → a-lib-2.0.jar ✓
 *     └─ 引用 com.example.b.StringUtil → 隔离包 → findClass → b-lib-2.0.jar ✓
 *     └─ 引用 com.example.c.LogUtil    → 非隔离 → parent.loadClass → AppCL ✓（共享）
 *     └─ 引用 java.lang.String         → 非隔离 → parent...→ Bootstrap ✓
 * </pre>
 */
public class IsolatedClassLoader extends URLClassLoader {

    /** 需要 parent-last 隔离的包名前缀 */
    private final Set<String> isolatedPrefixes;

    /**
     * @param jars             jar 文件 URL 数组
     * @param parent           父 ClassLoader（通常是 AppClassLoader）
     * @param isolatedPrefixes 需要隔离的包名前缀，如 "com.example.a", "com.example.b"
     */
    public IsolatedClassLoader(URL[] jars, ClassLoader parent, Set<String> isolatedPrefixes) {
        super(jars, parent);
        this.isolatedPrefixes = isolatedPrefixes;
    }

    /**
     * 重写 loadClass — 实现 parent-last 隔离策略。
     *
     * <p>JVM 在以下时机调用 loadClass：
     * <ul>
     *   <li>遇到 new、方法调用、字段访问等字节码引用时</li>
     *   <li>Class.forName() 显式加载时</li>
     *   <li>反射 API 触发时</li>
     * </ul>
     *
     * <p>loadClass 是类加载的<b>唯一入口</b>，重写它即可控制整个加载策略。
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // getClassLoadingLock(name) 返回此 ClassLoader 注册的锁对象
        // 保证同一类名的并发加载是线程安全的
        synchronized (getClassLoadingLock(name)) {

            // 第 1 步：检查是否已加载（findLoadedClass 是 native 方法，极快）
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }

            // 第 2 步：判断是否属于隔离包
            // 例: name="com.example.a.HelloService", prefixes=["com.example.a", "com.example.b"]
            //     → "com.example.a.HelloService".startsWith("com.example.a") → true
            boolean shouldIsolate = isolatedPrefixes.stream()
                    .anyMatch(name::startsWith);

            if (shouldIsolate) {
                // ===== 隔离包：先自己的 jar，再 parent（parent-last）=====
                // findClass 由 URLClassLoader 实现，遍历构造时传入的 URL，
                // 读取 jar 中的 .class 文件并用 defineClass 转为 Class 对象
                try {
                    c = findClass(name);
                } catch (ClassNotFoundException e) {
                    // 自己的 jar 中找不到才 fallback 到 parent
                    c = getParent().loadClass(name);
                }
            } else {
                // ===== 非隔离包：正常双亲委派（parent-first）=====
                // c-lib 的类 → AppClassLoader → 找到共享的 LogUtil
                // java.*  → AppClassLoader → PlatformClassLoader → Bootstrap
                c = getParent().loadClass(name);
            }

            // 第 3 步：链接（验证、准备、解析）
            if (resolve) {
                resolveClass(c);
            }

            return c;
        }
    }

    @Override
    public String toString() {
        return "IsolatedClassLoader" + isolatedPrefixes;
    }
}
