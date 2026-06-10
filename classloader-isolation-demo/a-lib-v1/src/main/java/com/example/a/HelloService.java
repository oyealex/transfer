package com.example.a;

import com.example.b.StringUtil;
import com.example.c.LogUtil;

/**
 * <h1>A-lib 1.0 — 大写问候服务（高频版本）</h1>
 *
 * GAV: {@code com.example:a-lib:1.0}
 *
 * 依赖：{@code b-lib:1.0}（大写工具）, {@code c-lib:1.0}（日志工具）。
 *
 * <p>作为实验中的"高频版本"，此类由 AppClassLoader 加载，
 * 主程序通过 {@code import} + {@code new} 直接调用，享有完整的编译期类型安全。
 */
public class HelloService {

    // 这个字段的类型是 com.example.b.StringUtil，
    // 在当前 ClassLoader (AppClassLoader) 中解析为 b-lib:1.0 的版本
    private final StringUtil util = new StringUtil();

    /**
     * 生成问候语。由于依赖 {@code b-lib:1.0} 的 {@code toUpperCase()}，
     * 结果一定是<b>大写</b>字符串。这直观地证明了调用链正确。
     */
    public String sayHello(String name) {
        LogUtil.info("[a-lib:1.0] sayHello 被调用, name=" + name);

        String result = util.transform(
                "Hello, " + name + "! [a-lib:1.0 + " + util.getVersion() + "]");

        LogUtil.info("[a-lib:1.0] 返回: " + result);
        return result;
    }

    public String getVersion() {
        return "a-lib:1.0"
                + " (CL=" + getClass().getClassLoader().getClass().getSimpleName() + ")";
    }
}
