package cn.orangenode.forge.system.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 系统模块时间转换。
 *
 * <p>数据库列统一为 {@code DATETIME(3)} 且按 UTC 语义读写，实体使用 {@link LocalDateTime} 承载；
 * 接口层统一输出带时区的 {@link Instant}（序列化为 {@code 2026-09-14T02:00:00Z}）。
 * 转换只在这一处完成，避免各转换器各写一遍时区推断。</p>
 */
public final class SystemTimes {

    /**
     * 工具类不允许实例化。
     */
    private SystemTimes() {
    }

    /**
     * 把数据库时间按 UTC 语义转换为对外时间。
     *
     * @param value 数据库时间，允许为 {@code null}
     * @return 带 UTC 时区的时刻，入参为空时返回 {@code null}
     */
    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /**
     * 取得当前 UTC 时间，用于写入数据库时间列。
     *
     * @return 当前 UTC 时间
     */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
