package cn.orangenode.forge.framework.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import cn.orangenode.forge.framework.web.error.WebErrorWriter;

import lombok.extern.slf4j.Slf4j;

/**
 * 安全过滤链装配。
 *
 * <p>M2 只建立过滤链骨架与统一错误出口：不使用表单登录、HTTP Basic、默认登录页、
 * 会话 Cookie 或 CSRF Token，保证失败响应仍由 {@link SecurityErrorHandler} 写出 HTTP 200 + body.code。
 * 令牌认证、身份隔离与权限注解在 M3 按账号模块实现后接入，本阶段不提前放行也鉴权。</p>
 *
 * <p>本阶段所有请求都显式放行，但放行规则集中在此：M3 增加受保护路径时逐个收紧，
 * 未声明的路径不会因为“默认放行”而绕过后续鉴权。</p>
 */
@Slf4j
@Configuration
public class SecurityConfig {

    /**
     * 配置应用安全过滤链。
     *
     * @param http          安全构建器
     * @param errorWriter   统一错误响应写出组件
     * @return 安全过滤链
     * @throws Exception 过滤链配置失败时透传
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, WebErrorWriter errorWriter) throws Exception {
        SecurityErrorHandler errorHandler = new SecurityErrorHandler(errorWriter);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        log.info("安全过滤链已装配：无状态会话、统一 401/403 错误出口，M2 阶段全部请求放行");
        return http.build();
    }
}
