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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.config.VideoProperties;
import cn.orangenode.forge.video.response.RunResponse;
import cn.orangenode.forge.video.response.SourceResponse;
import cn.orangenode.forge.video.service.CollectionService;
import cn.orangenode.forge.video.response.SettingsResponse;
import cn.orangenode.forge.video.service.IngestService;
import cn.orangenode.forge.video.service.SettingsService;
import cn.orangenode.forge.video.service.SourceService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

/**
 * 执行器的失败隔离、已提交末页恢复与按最大并发数并行采集验证。
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
        CollectionWorker worker = new CollectionWorker(collections, ingest, sources, client, parser, properties(),
                settings(1));
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
     * 不同资源站的运行并行执行，同时运行数不超过后台设置的最大并发数；一个结束后立即补位，全部运行都能完成。
     */
    @Test
    void runsDifferentSourcesConcurrentlyUpToLimit() throws Exception {
        CollectionService collections = mock(CollectionService.class);
        SourceService sources = mock(SourceService.class);
        MacCmsClient client = mock(MacCmsClient.class);
        when(collections.claim()).thenReturn(1L, 2L, 3L, null);
        for (long id = 1; id <= 3; id++) {
            when(collections.run(id)).thenReturn(run(String.valueOf(id), 1, 0, String.valueOf(id)));
        }
        when(sources.list()).thenReturn(List.of(
                new SourceResponse("1", "来源一", "https://one.example.com", true, "Asia/Shanghai"),
                new SourceResponse("2", "来源二", "https://two.example.com", true, "Asia/Shanghai"),
                new SourceResponse("3", "来源三", "https://three.example.com", true, "Asia/Shanghai")));
        when(client.url(any(), any(), any(), any(), eq(1))).thenAnswer(call -> call.getArgument(0));
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(client.get(any())).thenAnswer(call -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(300);
            running.decrementAndGet();
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "请求失败");
        });
        CountDownLatch done = new CountDownLatch(3);
        doAnswer(call -> {
            done.countDown();
            return null;
        }).when(collections).finish(any(), any(), any());
        CollectionWorker worker = new CollectionWorker(collections, mock(IngestService.class), sources, client,
                mock(MacCmsParser.class), properties(), settings(2));
        try {
            worker.ready();
            worker.tick();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(peak.get()).isEqualTo(2);
            verify(client).get("https://one.example.com");
            verify(client).get("https://two.example.com");
            verify(client).get("https://three.example.com");
        } finally {
            worker.close();
        }
    }

    /**
     * 构造并发上限为 8、请求间隔为 0 的配置。
     */
    private VideoProperties properties() {
        VideoProperties properties = new VideoProperties();
        properties.setMaxConcurrencyLimit(8);
        return properties;
    }

    /**
     * 构造返回指定最大并发数的设置服务。
     */
    private SettingsService settings(int maxConcurrency) {
        SettingsService settings = mock(SettingsService.class);
        when(settings.get()).thenReturn(new SettingsResponse(List.of("name"), List.of(), true, java.util.Map.of(),
                maxConcurrency, 8));
        return settings;
    }

    /**
     * 构造指定资源站的持久化进度响应。
     */
    private RunResponse run(String id, int next, int total, String sourceId) {
        return new RunResponse(id, null, sourceId, "DAY", null, "RUNNING", next, total,
                0, 0, 0, 0, 0, null, null, null, null, null, null);
    }

    /**
     * 构造持久化进度响应，不依赖线程内计数。
     */
    private RunResponse run(String id, int next, int total) {
        return new RunResponse(id, null, "1", "DAY", null, "RUNNING", next, total,
                0, 0, 0, 0, 0, null, null, null, null, null, null);
    }
}
