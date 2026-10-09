package cn.orangenode.forge.video.response;

/**
 * RunResponse对外业务响应，主键使用字符串。
 */
public record RunResponse(String id, String taskId, String sourceId, String rangeCode, String upstreamCategoryId, String status, int nextPage, int pageCount, long processed, long inserted, long updated, long pending, long skipped, String errorMessage, String warning, String createdAt, String startedAt, String finishedAt, String upstreamIds) {
}
