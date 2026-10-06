package cn.orangenode.forge.video.collector;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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
import cn.orangenode.forge.video.service.SourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 独立单线程采集执行器，调度线程只负责排队与唤醒。
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
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
            if (busy.compareAndSet(false, true)) {
                executor.submit(this::drain);
            }
        } catch (RuntimeException failure) {
            log.error("采集调度失败，将在下次扫描重试", failure);
        }
    }

    /**
     * 按顺序运行持久化队列，来源失败不阻塞后续来源。
     */
    private void drain() {
        try {
            Long id;
            while (!Thread.currentThread().isInterrupted() && (id = collections.claim()) != null) {
                execute(id);
            }
        } catch (RuntimeException failure) {
            log.error("采集队列读取失败，保留数据库断点", failure);
        } finally {
            busy.set(false);
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
                ingest.page(id, page);
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
