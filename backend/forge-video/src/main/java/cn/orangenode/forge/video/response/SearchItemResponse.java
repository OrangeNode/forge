package cn.orangenode.forge.video.response;

import java.util.List;

/**
 * 资源站搜索命中的一部影片；libraryStatus 为 COLLECTED（该站这部已入库）、SAME_NAME（库里有同名影片）或 NONE。
 */
public record SearchItemResponse(String upstreamId, String name, Integer year, String typeName, String remarks,
        int lineCount, int episodeCount, String sourceUpdatedAt, String libraryStatus, String videoId,
        String videoName, List<String> problems) {
}
