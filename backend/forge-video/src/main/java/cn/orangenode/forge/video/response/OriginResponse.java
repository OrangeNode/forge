package cn.orangenode.forge.video.response;

/**
 * OriginResponse对外业务响应，主键使用字符串。
 */
public record OriginResponse(String id, String sourceId, String sourceName, String upstreamId, String sourceUpdatedAt, String collectedAt) {
}
