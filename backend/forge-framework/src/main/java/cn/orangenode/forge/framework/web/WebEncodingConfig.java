package cn.orangenode.forge.framework.web;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层编码配置。
 *
 * <p>项目所有对外响应都是 JSON，且提示信息、字段说明均为中文，因此显式把 JSON 与纯文本
 * 消息转换器的默认字符集固定为 UTF-8。这样响应头即使未声明 charset，客户端也不会按平台默认
 * 单字节编码解析，避免中文提示乱码。</p>
 *
 * <p>本配置不改变接口的 HTTP 状态与响应体结构，只统一编码声明。</p>
 */
@Configuration
public class WebEncodingConfig implements WebMvcConfigurer {

    /**
     * 覆盖消息转换器的默认字符集为 UTF-8。
     *
     * @param converters 已装配的消息转换器列表
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if ((converter instanceof JacksonJsonHttpMessageConverter
                    || converter instanceof StringHttpMessageConverter)
                    && converter instanceof AbstractHttpMessageConverter<?> configurable) {
                configurable.setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
    }
}
