package cn.orangenode.forge.video.collector;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

/**
 * 定时任务使用标准五段 cron（分 时 日 月 周），按统一的调度时区计算下一次执行时刻；
 * 采集窗口与执行时间互不绑定，错过的触发点最多补触发一次。
 */
public final class TaskSchedules {
    /**
     * 标准 cron 字段数量。
     */
    private static final int FIELDS = 5;

    /**
     * 不创建工具实例。
     */
    private TaskSchedules() {
    }

    /**
     * 解析五段 cron 表达式，格式不正确时抛出参数异常，由调用方转换为业务错误。
     */
    public static CronExpression parse(String cron) {
        String value = cron == null ? "" : cron.strip().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.split(" ").length != FIELDS) {
            throw new IllegalArgumentException("cron 需要五段：分 时 日 月 周");
        }
        return CronExpression.parse("0 " + value);
    }

    /**
     * 规范化表达式中的空白，便于保存与展示。
     */
    public static String normalize(String cron) {
        return cron.strip().replaceAll("\\s+", " ");
    }

    /**
     * 基于当前 UTC 时刻计算下一次执行（UTC），跳过停机期间的历史触发点；表达式永不触发时返回空。
     */
    public static LocalDateTime next(String cron, ZoneId zone, LocalDateTime nowUtc) {
        ZonedDateTime local = nowUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone);
        ZonedDateTime next = parse(cron).next(local);
        return next == null ? null : next.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
