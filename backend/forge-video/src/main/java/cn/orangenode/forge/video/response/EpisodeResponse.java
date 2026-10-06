package cn.orangenode.forge.video.response;

/**
 * EpisodeResponse对外业务响应，主键使用字符串。
 */
public record EpisodeResponse(String id, String name, int sortNo, String url, String urlType) {
}
