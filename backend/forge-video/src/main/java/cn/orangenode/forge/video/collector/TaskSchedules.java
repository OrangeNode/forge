package cn.orangenode.forge.video.collector;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import cn.orangenode.forge.video.entity.TaskEntity;

/**
 * 采集窗口和执行频率分别管理，错过时最多补触发一次。
 */
public final class TaskSchedules {
    /**
     * 不创建工具实例。
     */
    private TaskSchedules() {
    }
    /**
     * 基于当前时刻计算下一次执行，跳过停机期间的历史触发点。
     */
    public static LocalDateTime next(TaskEntity task, LocalDateTime now) {
        if ("INTERVAL".equals(task.getFrequency())) {
            return now.plusHours(task.getIntervalHours());
        }
        ZonedDateTime local = now.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of(task.getZoneId()));
        ZonedDateTime candidate = local.toLocalDate().atTime(LocalTime.parse(task.getTimeOfDay()))
                .atZone(local.getZone());
        if ("WEEKLY".equals(task.getFrequency())) {
            int days = Math.floorMod(task.getWeekday() - local.getDayOfWeek().getValue(), 7);
            candidate = candidate.plusDays(days);
            if (!candidate.isAfter(local)) {
                candidate = candidate.plusWeeks(1);
            }
        } else if (!candidate.isAfter(local)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
