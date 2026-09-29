package cn.orangenode.forge.example.response;

import java.time.Instant;

/**
 * 示例接口响应。
 *
 * @param application 应用名称，只用于确认装配结果
 * @param serverTime  服务器 UTC 时间
 */
public record HelloResponse(String application, Instant serverTime) {
}
