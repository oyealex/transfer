package com.example.c;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * <h1>Jar C — 共享日志工具</h1>
 *
 * GAV: {@code com.example:c-lib:1.0}（唯一版本，无版本分歧）。
 * 被 a-lib 1.0 和 a-lib 2.0 共同依赖。
 *
 * <p>整个 JVM 中只存在唯一一份 LogUtil.class（由 AppClassLoader 加载），
 * 因此其 static 字段在 v1/v2 之间共享，可用于验证隔离性。
 */
public class LogUtil {

    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** 全局调用计数器 —— 验证 v1 和 v2 共享同一个 static 域 */
    public static int callCount = 0;

    /**
     * 打印带时间戳的日志，同时打印当前类的 ClassLoader。
     * 通过观察 ClassLoader 名称可以区分隔离/共享状态。
     */
    public static void info(String msg) {
        callCount++;
        String time = LocalTime.now().format(fmt);
        String selfLoader = LogUtil.class.getClassLoader().getClass().getSimpleName();
        System.out.printf("[%s] [#%d] [LogUtil@%s] %s%n",
                time, callCount, selfLoader, msg);
    }
}
