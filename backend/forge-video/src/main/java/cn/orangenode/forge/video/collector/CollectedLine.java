package cn.orangenode.forge.video.collector;

import java.util.List;

/**
 * 完整解析成功的一条来源线路。
 */
public record CollectedLine(String code, List<CollectedEpisode> episodes) {
}
