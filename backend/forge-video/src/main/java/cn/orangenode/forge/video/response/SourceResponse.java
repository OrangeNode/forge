package cn.orangenode.forge.video.response;

/**
 * SourceResponse对外业务响应，主键使用字符串。
 */
public record SourceResponse(String id, String name, String apiUrl, boolean enabled, String sourceZone) {
}
