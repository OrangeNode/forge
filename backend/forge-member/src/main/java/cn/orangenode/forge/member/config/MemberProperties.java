package cn.orangenode.forge.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 普通用户业务配置，唯一默认来源为 application.yaml 的 {@code forge.member.register}。
 *
 * <p>令牌有效期等认证参数位于基础层的 {@code forge.member.auth}。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.member")
public class MemberProperties {

    /**
     * 注册限制。
     */
    @Valid
    private Register register = new Register();

    /**
     * 注册限制参数：用户名注册不经过邮箱验证，按来源地址限制每日注册次数防止批量注册。
     */
    @Getter
    @Setter
    public static class Register {
        /**
         * 同一来源地址每天最多成功注册的账号数。
         */
        @Min(1)
        private int ipDailyLimit = 10;
    }
}
