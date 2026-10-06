package cn.orangenode.forge.video.response;

import java.util.List;

/**
 * PendingResponse对外业务响应，主键使用字符串。
 */
public record PendingResponse(String id, String runId, String sourceId, String upstreamId, String name, String reason, String status, List<VideoResponse> candidates, PendingVideoResponse incoming) {
}
