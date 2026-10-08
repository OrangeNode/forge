package cn.orangenode.forge.video.config;

import java.time.Duration;
import java.time.Instant;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * App 视频接口限流装配；身份校验由基础过滤链完成，App 文档分组由基础层统一装配。
 */
@Configuration
@RequiredArgsConstructor
public class VideoWebConfig implements WebMvcConfigurer {
    private final ForgeRedisTemplate redis;
    private final VideoProperties properties;

    /**
     * 公开读取按真实连接地址与固定时间窗口计数，Redis 失败不跳过限制。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /**
             * 查询前检查请求频率，失败统一返回业务响应。
             */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                long window = Instant.now().getEpochSecond() / properties.getAppRateWindowSeconds();
                String key = "video:public:" + request.getRemoteAddr() + ":" + window;
                long count = redis.increment(key, Duration.ofSeconds(properties.getAppRateWindowSeconds()));
                if (count > properties.getAppRateLimit()) {
                    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");
                }
                return true;
            }
        }).addPathPatterns("/api/app/v1/video/**");
    }
}
