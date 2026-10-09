package cn.orangenode.forge.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 普通用户业务配置，唯一默认来源为 application.yaml 的 {@code forge.member.register}、{@code forge.member.activation} 与 {@code forge.member.history-keep}。
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
     * 每个用户保留并展示的最近观看记录条数，超出的旧记录在上报进度时删除。
     */
    @Min(1)
    private int historyKeep = 10;

    /**
     * 注册激活码参数。
     */
    @Valid
    private Activation activation = new Activation();

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

    /**
     * 注册激活码参数：开放注册时每个新账号必须使用一个激活码，一码只能注册一次。
     */
    @Getter
    @Setter
    public static class Activation {
        /**
         * 激活码长度。
         */
        @Min(8)
        @Max(32)
        private int codeLength = 12;

        /**
         * 后台一次最多生成的数量，也是一次批量作废或删除的上限。
         */
        @Min(1)
        private int batchMax = 500;

        /**
         * 按筛选条件一次最多导出的数量。
         */
        @Min(1)
        private int exportMax = 10000;

        /**
         * 同一来源地址每天最多输错激活码的次数。
         */
        @Min(1)
        private int ipDailyFailLimit = 20;
    }
}
