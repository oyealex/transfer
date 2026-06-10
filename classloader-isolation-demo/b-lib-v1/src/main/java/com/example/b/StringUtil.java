package com.example.b;

/**
 * <h1>B-lib 1.0 — 字符串大写工具</h1>
 *
 * GAV: {@code com.example:b-lib:1.0}
 *
 * 被 a-lib:1.0 依赖。将输入字符串转为<b>大写</b>。
 * 在实验主程序中由 AppClassLoader 加载（与 a-lib:1.0 同在一个 ClassLoader）。
 */
public class StringUtil {

    /** V1 行为：全大写 */
    public String transform(String input) {
        return input.toUpperCase();
    }

    public String getVersion() {
        return "b-lib:1.0"
                + " (CL=" + getClass().getClassLoader().getClass().getSimpleName() + ")";
    }
}
