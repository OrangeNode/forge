package cn.orangenode.forge.video.response;

import java.util.List;

/**
 * PlayLineResponse对外业务响应，主键使用字符串。
 */
public record PlayLineResponse(String id, String originId, String sourceName, String code, String name, int sortNo, boolean enabled, int availableEpisodeCount, List<EpisodeResponse> episodes) {
}
