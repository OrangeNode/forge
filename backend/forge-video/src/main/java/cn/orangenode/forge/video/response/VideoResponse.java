package cn.orangenode.forge.video.response;

/**
 * VideoResponse对外业务响应，主键使用字符串。
 */
public record VideoResponse(String id, String name, String categoryId, Integer year, String area, String language, String director, String actors, String posterUrl, String description, String remarks, Integer declaredTotal, Boolean ended, boolean published, boolean metadataLocked, String primaryOriginId, String createdAt, String updatedAt) {
}
