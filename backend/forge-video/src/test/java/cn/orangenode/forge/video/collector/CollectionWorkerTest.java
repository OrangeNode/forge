package cn.orangenode.forge.video.collector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.config.VideoProperties;
import cn.orangenode.forge.video.response.RunResponse;
import cn.orangenode.forge.video.response.SourceResponse;
import cn.orangenode.forge.video.service.CollectionService;
import cn.orangenode.forge.video.service.IngestService;
import cn.orangenode.forge.video.service.SourceService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

/**
 * 独立执行器的失败隔离与已提交末页恢复验证。
 */
class CollectionWorkerTest {
    /**
     * 请求失败只结束当前运行，随后继续领取队列，不触碰旧地址。
     */
    @Test
    void isolatesNetworkFailureAndContinuesQueue() throws Exception {
        CollectionService collections = mock(CollectionService.class);
        IngestService ingest = mock(IngestService.class);
        SourceService sources = mock(SourceService.class);
        MacCmsClient client = mock(MacCmsClient.class);
        MacCmsParser parser = mock(MacCmsParser.class);
        when(collections.claim()).thenReturn(1L, 2L, null);
        when(collections.run(1L)).thenReturn(run("1", 1, 0));
        when(collections.run(2L)).thenReturn(run("2", 3, 2));
        when(sources.list()).thenReturn(List.of(new SourceResponse("1", "来源", "https://example.com", true, "Asia/Shanghai")));
        when(client.url(any(), any(), any(), any(), eq(1))).thenReturn("https://example.com");
        when(client.get(any())).thenThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "请求失败"));
        CountDownLatch done = new CountDownLatch(2);
        doAnswer(call -> {
            done.countDown();
            return null;
        }).when(collections).finish(any(), any(), any());
        CollectionWorker worker = new CollectionWorker(collections, ingest, sources, client, parser, new VideoProperties());
        try {
            worker.ready();
            worker.tick();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            verify(collections).finish(1L, "FAILED", "请求失败");
            verify(collections).finish(2L, "COMPLETED", null);
            verify(ingest, never()).page(any(), any());
            verify(client).get("https://example.com");
        } finally {
            worker.close();
        }
    }

    /**
     * 构造持久化进度响应，不依赖线程内计数。
     */
    private RunResponse run(String id, int next, int total) {
        return new RunResponse(id, null, "1", "DAY", null, "RUNNING", next, total,
                0, 0, 0, 0, 0, null, null, null, null, null);
    }
}
