package cn.orangenode.forge.video.collector;

import java.util.List;

/**
 * 已校验的采集分页快照。
 */
public record CollectedPage(int page, int pageCount, List<CollectedVideo> videos) {
}
