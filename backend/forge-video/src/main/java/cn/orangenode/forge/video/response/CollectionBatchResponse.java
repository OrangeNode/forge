package cn.orangenode.forge.video.response;

import java.util.List;

/**
 * 批量操作的实际成功数与逐条失败原因。
 */
public record CollectionBatchResponse(int total, int succeeded, List<Failure> failures) {
    /**
     * 某条记录未执行成功的业务原因。
     */
    public record Failure(String id, String message) {
    }
}
