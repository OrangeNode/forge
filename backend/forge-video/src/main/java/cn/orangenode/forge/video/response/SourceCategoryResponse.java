package cn.orangenode.forge.video.response;

/**
 * SourceCategoryResponse对外业务响应，主键使用字符串。
 */
public record SourceCategoryResponse(String upstreamId, String upstreamParentId, String name, String categoryId) {
}
