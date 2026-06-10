package com.example.a;

import com.example.b.StringUtil;
import com.example.c.LogUtil;

/**
 * <h1>A-lib 2.0 — 小写问候服务（低频版本）</h1>
 *
 * GAV: {@code com.example:a-lib:2.0}
 *
 * 与 a-lib:1.0 <b>完全相同的包名+类名</b>，但依赖的是 {@code b-lib:2.0}（小写工具）。
 * 行为差异：sayHello() 输出<b>小写</b>字符串。
 *
 * <p>此类<b>不在</b>主程序的编译 classpath 上，
 * 由 IsolatedClassLoader 在运行时从 a-lib-2.0.jar 中加载。
 * 加载时 IsolatedClassLoader 的 parent-last 策略确保
 * 其依赖的 {@code com.example.b.StringUtil} 来自 b-lib-2.0.jar，
 * 而非 AppClassLoader 中的 b-lib:1.0。
 */
public class HelloService {

    // ★ 关键：此处的 com.example.b.StringUtil 由 IsolatedClassLoader 解析，
    // 指向的是 b-lib:2.0（小写），而非 AppClassLoader 中的 b-lib:1.0（大写）
    private final StringUtil util = new StringUtil();

    /**
     * 生成问候语。由于依赖 {@code b-lib:2.0} 的 {@code toLowerCase()}，
     * 结果一定是<b>小写</b>字符串。
     */
    public String sayHello(String name) {
        LogUtil.info("[a-lib:2.0] sayHello 被调用, name=" + name);

        String result = util.transform(
                "Hello, " + name + "! [a-lib:2.0 + " + util.getVersion() + "]");

        LogUtil.info("[a-lib:2.0] 返回: " + result);
        return result;
    }

    public String getVersion() {
        return "a-lib:2.0"
                + " (CL=" + getClass().getClassLoader().getClass().getSimpleName() + ")";
    }
}
