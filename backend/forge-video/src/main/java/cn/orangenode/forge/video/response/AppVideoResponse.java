package cn.orangenode.forge.video.response;

/**
 * AppVideoResponse对外业务响应，主键使用字符串。
 */
public record AppVideoResponse(String id, String name, String categoryId, Integer year, String area, String language, String director, String actors, String posterUrl, String description, String remarks, Integer declaredTotal, Boolean ended, String updatedAt) {
}
