package cn.orangenode.forge.video.collector;

/**
 * 单条采集的明确入库结果。
 */
public record IngestResult(long inserted, long updated, long pending, long skipped) {
}
