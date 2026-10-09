package cn.orangenode.forge.video.collector;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.config.VideoProperties;
import lombok.RequiredArgsConstructor;

/**
 * 受超时及体积限制的公开资源站请求，不访问或下载播放地址。
 */
@Component
@RequiredArgsConstructor
public class MacCmsClient {
    /**
     * 允许代取的海报类型；SVG 可携带脚本，同源返回给后台不安全，因此不在其中。
     */
    private static final List<String> POSTER_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private final VideoProperties properties;

    /**
     * 校验资源站地址，禁止凭据、非 HTTP 协议与内网目标。
     */
    public URI validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || (address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc)) {
                    throw new IllegalArgumentException();
                }
            }
            return uri;
        } catch (Exception failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资源站必须使用可解析的公网 HTTP 或 HTTPS 地址");
        }
    }

    /**
     * 构造规范 URL，替换采集保留参数而保留站点其他查询参数。
     */
    public String url(String base, String action, String range, String category, int page) {
        List<String> params = siteParams(base);
        params.add("ac=" + action);
        params.add("pg=" + page);
        if ("DAY".equals(range)) {
            params.add("h=24");
        } else if ("WEEK".equals(range)) {
            params.add("h=168");
        } else if (range != null && !"ALL".equals(range)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持日、周或全部采集");
        }
        if (category != null && !category.isBlank()) {
            params.add("t=" + URLEncoder.encode(category, StandardCharsets.UTF_8));
        }
        return join(base, params);
    }

    /**
     * 构造按源站影片编号请求详情的 URL，用于勾选采集；编号以逗号分隔，按页返回。
     */
    public String detailUrl(String base, String upstreamIds, int page) {
        List<String> params = siteParams(base);
        params.add("ac=detail");
        params.add("pg=" + page);
        params.add("ids=" + URLEncoder.encode(upstreamIds, StandardCharsets.UTF_8).replace("%2C", ","));
        return join(base, params);
    }

    /**
     * 构造按关键词搜索详情的 URL，详情接口才带年份、分类名与播放地址。
     */
    public String searchUrl(String base, String keyword, int page) {
        List<String> params = siteParams(base);
        params.add("ac=detail");
        params.add("pg=" + page);
        params.add("wd=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8));
        return join(base, params);
    }

    /**
     * 保留站点地址自带的查询参数，去掉本程序会重新设置的采集参数。
     */
    private List<String> siteParams(String base) {
        URI uri = URI.create(base);
        List<String> params = new ArrayList<>();
        if (uri.getRawQuery() != null) {
            for (String part : uri.getRawQuery().split("&")) {
                String key = java.net.URLDecoder.decode(part.split("=", 2)[0], StandardCharsets.UTF_8);
                if (!List.of("ac", "h", "t", "pg", "ids", "at", "wd").contains(key)) {
                    params.add(part);
                }
            }
        }
        return params;
    }

    /**
     * 把参数拼回去掉查询与锚点的站点地址。
     */
    private String join(String base, List<String> params) {
        return base.split("[?#]", 2)[0] + "?" + String.join("&", params);
    }

    /**
     * 请求 JSON，完整正文和重试次数均受配置限制，取消超时请求。
     */
    public String get(String url) {
        HttpClient client = newClient();
        try {
            for (int attempt = 0; attempt <= properties.getRetryCount(); attempt++) {
                CompletableFuture<HttpResponse<byte[]>> future = null;
                try {
                    URI uri = validateUrl(url);
                    HttpRequest request = HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                            .header("Accept", "application/json").header("User-Agent", properties.getUserAgent()).GET().build();
                    future = client.sendAsync(request, info -> new BoundedBody(properties.getMaxResponseBytes()));
                    HttpResponse<byte[]> response = future.get(properties.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
                    if (response.statusCode() != 200) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "资源站 HTTP 状态异常（" + response.statusCode() + "），未修改已有播放地址");
                    }
                    return new String(response.body(), StandardCharsets.UTF_8);
                } catch (InterruptedException failure) {
                    if (future != null) {
                        future.cancel(true);
                    }
                    Thread.currentThread().interrupt();
                    throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "采集请求已中断");
                } catch (Exception failure) {
                    if (future != null) {
                        future.cancel(true);
                    }
                    if (attempt == properties.getRetryCount()) {
                        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "资源站请求失败：" + safeReason(failure));
                    }
                }
            }
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "资源站暂不可用");
        } finally {
            client.shutdownNow();
        }
    }

    /**
     * 代取公开海报图片，地址校验、超时、代理与采集请求一致，正文受海报大小上限约束。
     * 只接受 HTTP 200 且声明为 JPEG、PNG、WebP 或 GIF 的响应（排除 SVG 等可执行脚本的类型），不跟随重定向、不重试。
     */
    public RemoteImage image(String url) {
        URI uri = validateUrl(url);
        HttpClient client = newClient();
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .header("Accept", "image/*").header("User-Agent", properties.getUserAgent()).GET().build();
            future = client.sendAsync(request, info -> new BoundedBody(properties.getMaxPosterBytes()));
            HttpResponse<byte[]> response = future.get(properties.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
            String contentType = response.headers().firstValue("Content-Type").orElse("")
                    .split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
            if (response.statusCode() != 200 || !POSTER_TYPES.contains(contentType)) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "海报地址没有返回图片（HTTP " + response.statusCode() + "）");
            }
            return new RemoteImage(response.body(), contentType);
        } catch (InterruptedException failure) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "海报请求已中断");
        } catch (BusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            if (future != null) {
                future.cancel(true);
            }
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "海报获取失败：" + safeReason(failure));
        } finally {
            client.shutdownNow();
        }
    }

    /**
     * 创建不跟随重定向的 HTTP 客户端，配置了代理时只接受无凭据的 HTTP 代理。
     */
    private HttpClient newClient() {
        HttpClient.Builder builder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER);
        if (properties.getProxyUrl() != null && !properties.getProxyUrl().isBlank()) {
            URI proxy = URI.create(properties.getProxyUrl());
            if (!"http".equalsIgnoreCase(proxy.getScheme()) || proxy.getHost() == null || proxy.getPort() < 1
                    || proxy.getUserInfo() != null) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "采集代理配置不合法，需要无凭据 HTTP 代理地址和端口");
            }
            builder.proxy(java.net.ProxySelector.of(new java.net.InetSocketAddress(proxy.getHost(), proxy.getPort())));
        }
        return builder.build();
    }

    /**
     * 仅保留错误种类或受控业务说明，避免泄漏 URL、凭据与响应正文。
     */
    private String safeReason(Exception failure) {
        Throwable cause = failure instanceof java.util.concurrent.ExecutionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause instanceof BusinessException business) {
            return business.getMessage();
        }
        return switch (cause.getClass().getSimpleName()) {
            case "SSLHandshakeException" -> "TLS 握手失败，请检查部署网络与证书";
            case "HttpConnectTimeoutException", "HttpTimeoutException", "TimeoutException" -> "连接或读取超时";
            case "IllegalStateException" -> "响应接收失败或超过大小上限";
            default -> "网络连接或响应接收异常（" + cause.getClass().getSimpleName() + "）";
        };
    }

    /**
     * 接收时限制正文大小，避免先完整下载再检查。
     */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        /**
         * 设置本次正文接收上限。
         */
        private BoundedBody(int maximum) {
            this.maximum = maximum;
        }

        /**
         * 返回完整正文的异步结果。
         */
        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        /**
         * 开始按批接收字节。
         */
        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        /**
         * 在复制每个字节块前检查大小。
         */
        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                if ((long) buffer.size() + item.remaining() > maximum) {
                    subscription.cancel();
                    result.completeExceptionally(new IllegalStateException("响应超过配置上限"));
                    return;
                }
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                buffer.writeBytes(bytes);
            }
            subscription.request(1);
        }

        /**
         * 将接收失败交给请求用例。
         */
        @Override
        public void onError(Throwable failure) {
            result.completeExceptionally(failure);
        }

        /**
         * 完整接收后提交正文。
         */
        @Override
        public void onComplete() {
            result.complete(buffer.toByteArray());
        }
    }
}
