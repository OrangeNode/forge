package cn.orangenode.forge.framework.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import cn.orangenode.forge.framework.config.ForgeMemberAuthProperties;
import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.framework.member.MemberAccountDirectory;
import cn.orangenode.forge.framework.member.MemberBearerAuthenticationFilter;
import cn.orangenode.forge.framework.member.MemberTokenService;
import cn.orangenode.forge.framework.web.error.WebErrorWriter;

import lombok.extern.slf4j.Slf4j;

/**
 * 安全过滤链装配。
 *
 * <p>不使用表单登录、HTTP Basic、默认登录页、会话 Cookie 或 CSRF Token，
 * 身份由 Bearer 令牌承载，失败响应由 {@link SecurityErrorHandler} 写出 HTTP 200 + body.code。</p>
 *
 * <p>授权规则逐条收紧：只有配置中列出的匿名路径与 CORS 预检请求放行，
 * 其余路径（包括未知路径）一律要求认证，未知路径不会因为“默认放行”而被访问到；
 * 方法级权限由 {@code @PreAuthorize} 与账号当前权限共同决定，前端菜单不替代后端校验。</p>
 *
 * <p>管理员与普通用户两种身份按路径前缀分开识别：普通用户接口前缀下只识别普通用户令牌，
 * 其余路径只识别管理员令牌，因此任一令牌都不能访问另一身份的受保护接口。</p>
 */
@Slf4j
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 配置应用安全过滤链。
     *
     * @param http             安全构建器
     * @param errorWriter      统一错误响应写出组件
     * @param properties       认证与权限配置，提供匿名放行路径
     * @param tokenService     令牌会话校验入口
     * @param accountDirectory 账号与权限查询端口
     * @param memberProperties 普通用户认证配置，提供普通用户接口前缀
     * @param memberTokens     普通用户令牌会话
     * @param memberDirectory  普通用户账号目录端口，未装配普通用户模块时为空
     * @return 安全过滤链
     * @throws Exception 过滤链配置失败时透传
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, WebErrorWriter errorWriter,
            ForgeSecurityProperties properties, AdminTokenService tokenService,
            AdminAccountDirectory accountDirectory, ForgeMemberAuthProperties memberProperties,
            MemberTokenService memberTokens, ObjectProvider<MemberAccountDirectory> memberDirectory)
            throws Exception {
        SecurityErrorHandler errorHandler = new SecurityErrorHandler(errorWriter);
        BearerTokenAuthenticationFilter tokenFilter = new BearerTokenAuthenticationFilter(tokenService,
                accountDirectory, errorWriter, memberProperties.getPathPrefix());
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
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    for (String path : properties.getPermitAllPaths()) {
                        requests.requestMatchers(path).permitAll();
                    }
                    for (String path : properties.getPublicGetPaths()) {
                        requests.requestMatchers(HttpMethod.GET, path).permitAll();
                    }
                    requests.anyRequest().authenticated();
                })
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);
        MemberAccountDirectory directory = memberDirectory.getIfAvailable();
        if (directory != null) {
            http.addFilterAfter(new MemberBearerAuthenticationFilter(memberProperties.getPathPrefix(), memberTokens,
                    directory, errorWriter), BearerTokenAuthenticationFilter.class);
        }
        log.info("安全过滤链已装配：无状态会话、Bearer 令牌认证、匿名放行路径 {}，其余路径要求认证",
                properties.getPermitAllPaths());
        return http.build();
    }

    /**
     * 密码编码器。
     *
     * <p>账号表只保存编码结果，不保存或返回明文；编码强度由 Spring Security 的默认实现决定，
     * 不在业务代码中自行实现散列。</p>
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
