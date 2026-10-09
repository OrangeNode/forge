package cn.orangenode.forge.video.collector;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部影片的精简采集快照，仅保留实际业务字段；problems 记录解析时发现的源站数据异常，非空时整条进入待确认。
 */
public record CollectedVideo(String upstreamId, String upstreamCategoryId, String name, Integer year,
        String area, String language, String director, String actors, String posterUrl, String description,
        String remarks, Integer declaredTotal, Boolean ended, LocalDateTime sourceUpdatedAt,
        List<CollectedLine> lines, List<String> problems) {

    /**
     * 旧快照缺少异常列表时视为无异常，并固定为不可变列表。
     */
    public CollectedVideo {
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    /**
     * 构造无解析异常的采集快照。
     */
    public CollectedVideo(String upstreamId, String upstreamCategoryId, String name, Integer year,
            String area, String language, String director, String actors, String posterUrl, String description,
            String remarks, Integer declaredTotal, Boolean ended, LocalDateTime sourceUpdatedAt,
            List<CollectedLine> lines) {
        this(upstreamId, upstreamCategoryId, name, year, area, language, director, actors, posterUrl,
                description, remarks, declaredTotal, ended, sourceUpdatedAt, lines, List.of());
    }
}
