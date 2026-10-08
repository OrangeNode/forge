package cn.orangenode.forge.video.response;

/**
 * TaskResponse对外业务响应，主键使用字符串。
 */
public record TaskResponse(String id, String name, String sourceId, String rangeCode, String upstreamCategoryId, String cronExpression, boolean enabled, String nextRunAt) {
}
