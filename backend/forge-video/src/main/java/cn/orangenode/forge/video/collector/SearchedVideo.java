package cn.orangenode.forge.video.collector;

/**
 * 一条搜索命中：沿用采集快照的字段解析，另带源站分类名称供后台展示。
 */
public record SearchedVideo(CollectedVideo video, String typeName) {
}
