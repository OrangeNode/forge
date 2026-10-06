package cn.orangenode.forge.video.collector;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部影片的精简采集快照，仅保留实际业务字段。
 */
public record CollectedVideo(String upstreamId, String upstreamCategoryId, String name, Integer year,
        String area, String language, String director, String actors, String posterUrl, String description,
        String remarks, Integer declaredTotal, Boolean ended, LocalDateTime sourceUpdatedAt,
        List<CollectedLine> lines) {
}
