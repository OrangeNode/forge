package cn.orangenode.forge.video.response;

/**
 * 人工确认所需的来源资料，分集地址仅在确认入库后由影片详情读取。
 */
public record PendingVideoResponse(String name, String upstreamCategoryId, Integer year, String area,
        String language, String director, String actors, String posterUrl, String description, String remarks,
        Integer declaredTotal, Boolean ended, Integer lineCount, Integer episodeCount) {
}
