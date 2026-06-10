package com.example.b;

/**
 * <h1>B-lib 2.0 — 字符串小写工具</h1>
 *
 * GAV: {@code com.example:b-lib:2.0}
 *
 * 被 a-lib:2.0 依赖。与 b-lib:1.0 <b>完全相同的包名+类名</b>，
 * 但行为相反：将输入字符串转为<b>小写</b>。
 *
 * <p>这个类不在主程序的编译 classpath 上，
 * 由 IsolatedClassLoader 在运行时从 b-lib-2.0.jar 中加载。
 */
public class StringUtil {

    /** V2 行为：全小写 */
    public String transform(String input) {
        return input.toLowerCase();
    }

    public String getVersion() {
        return "b-lib:2.0"
                + " (CL=" + getClass().getClassLoader().getClass().getSimpleName() + ")";
    }
}
