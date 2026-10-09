package cn.orangenode.forge.video.collector;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import cn.orangenode.forge.video.config.VideoProperties;
import cn.orangenode.forge.video.request.CollectRequest;
import cn.orangenode.forge.video.response.RunResponse;
import cn.orangenode.forge.video.response.SourceResponse;
import cn.orangenode.forge.video.service.CollectionService;
import cn.orangenode.forge.video.service.IngestService;
import cn.orangenode.forge.video.service.SettingsService;
import cn.orangenode.forge.video.service.SourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 采集执行器：不同资源站的运行并发执行，同时运行数不超过后台“最大并发采集数”；同一资源站同一时刻只有一个活动运行
 * （排队时已保证），按页顺序采集，断点照常保存。网络请求与解析并发进行，入库在全局锁内逐页提交，
 * 避免两个资源站同时写入同名影片时互相看不到对方未提交的数据而重复建片。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${forge.video.worker-enabled:false} and ${spring.flyway.enabled:true}")
public class CollectionWorker {
    private final CollectionService collections;
    private final IngestService ingest;
    private final SourceService sources;
    private final MacCmsClient client;
    private final MacCmsParser parser;
    private final VideoProperties properties;
    private final SettingsService settings;
    private final ExecutorService executor = Executors.newCachedThreadPool(Thread.ofPlatform()
            .name("video-collect-", 1).factory());
    private final AtomicInteger active = new AtomicInteger();
    private final Object ingestLock = new Object();
    private volatile boolean ready;

    /**
     * 迁移和应用启动成功后恢复运行状态。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        collections.recover();
        ready = true;
    }

    /**
     * 到期任务短事务排队，网络请求只交给独立执行器。
     */
    @Scheduled(fixedDelayString = "${forge.video.scan-interval-seconds}", timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    public void tick() {
        if (!ready) {
            return;
        }
        try {
            collections.dispatchDue();
        } catch (RuntimeException failure) {
            log.error("采集调度失败，将在下次扫描重试", failure);
        }
        fill();
    }

    /**
     * 按最大并发数从持久化队列领取运行并交给执行器；调低并发数时已在运行的不中断，只是暂不领取新的。
     */
    private synchronized void fill() {
        if (!ready) {
            return;
        }
        try {
            int limit = Math.min(maxConcurrency(), properties.getMaxConcurrencyLimit());
            while (active.get() < limit) {
                Long id = collections.claim();
                if (id == null) {
                    return;
                }
                active.incrementAndGet();
                executor.submit(() -> runAndRefill(id));
            }
        } catch (RuntimeException failure) {
            log.error("采集队列读取失败，保留数据库断点", failure);
        }
    }

    /**
     * 读取后台设置的最大并发采集数，缺失时按 1 处理。
     */
    private int maxConcurrency() {
        Integer value = settings.get().maxConcurrency();
        return value == null || value < 1 ? 1 : value;
    }

    /**
     * 执行一个运行，结束后立即补位领取下一个排队运行，不必等到下次扫描。
     */
    private void runAndRefill(Long id) {
        try {
            execute(id);
        } finally {
            active.decrementAndGet();
        }
        if (!Thread.currentThread().isInterrupted()) {
            fill();
        }
    }

    /**
     * 每次网络请求前检查停止和来源停用状态，整页提交后更新断点。
     */
    private void execute(Long id) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                RunResponse run = collections.run(id);
                if ("STOP_REQUESTED".equals(run.status())) {
                    collections.finish(id, "STOPPED", null);
                    return;
                }
                if (run.pageCount() > 0 && run.nextPage() > run.pageCount()) {
                    collections.finish(id, "COMPLETED", null);
                    return;
                }
                SourceResponse source = sources.list().stream().filter(s -> s.id().equals(run.sourceId()))
                        .findFirst().orElseThrow();
                if (!source.enabled()) {
                    collections.finish(id, "FAILED", "资源站已停用");
                    return;
                }
                String url = client.url(source.apiUrl(), "detail", run.rangeCode(), run.upstreamCategoryId(), run.nextPage());
                CollectedPage page = parser.page(client.get(url), source.sourceZone());
                if (page.page() != run.nextPage()) {
                    throw new IllegalStateException("资源站返回页码与请求不一致");
                }
                synchronized (ingestLock) {
                    ingest.page(id, page);
                }
                if (page.pageCount() == 0 || page.page() >= page.pageCount()) {
                    collections.finish(id, "COMPLETED", null);
                    return;
                }
                Thread.sleep(properties.getRequestIntervalMillis());
            }
            collections.finish(id, "INTERRUPTED", "服务关闭，运行已中断");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            collections.finish(id, "INTERRUPTED", "服务关闭，运行已中断");
        } catch (RuntimeException failure) {
            String message = failure instanceof cn.orangenode.forge.core.exception.BusinessException
                    ? failure.getMessage() : "采集页处理失败，旧地址保持不变，可重试";
            collections.finish(id, "FAILED", message);
            log.warn("采集运行 {} 失败，已保留断点", id);
        }
    }

    /**
     * 定期清理终态日志，待确认数据不被清除。
     */
    @Scheduled(fixedDelayString = "${forge.video.cleanup-interval-hours}", timeUnit = java.util.concurrent.TimeUnit.HOURS)
    public void cleanup() {
        if (ready) {
            collections.cleanup();
        }
    }

    /**
     * 只停止本模块创建的执行器。
     */
    @PreDestroy
    public void close() {
        ready = false;
        executor.shutdownNow();
    }
}
