package cn.orangenode.forge.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 第二数据源配置。
 *
 * <p>对应配置前缀 {@code forge.secondary-datasource}。第二库是可选能力：
 * {@code enabled=false}（默认）时不注册路由，{@code @DS} 使用该名称会明确报错，
 * 不会静默回退主库；{@code enabled=true} 时要求连接信息完整，缺失即启动失败。</p>
 *
 * <p>数据源名称只来自配置与后端代码，不接受请求参数选择连接；
 * 凭据同样只从环境变量或未提交的环境文件读取。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.secondary-datasource")
public class ForgeSecondaryDataSourceProperties {

    /**
     * 是否注册第二数据源。
     */
    private boolean enabled = false;

    /**
     * 第二数据源在 dynamic-datasource 中注册的名称，供 {@code @DS} 使用。
     */
    @NotBlank(message = "第二数据源名称不能为空")
    private String name = "secondary";

    /**
     * 第二数据源连接地址，启用时必填。
     */
    private String url = "";

    /**
     * 第二数据源用户名，启用时必填。
     */
    private String username = "";

    /**
     * 第二数据源密码，只从环境变量读取，不写入源码与日志。
     */
    private String password = "";

    /**
     * 第二数据源驱动类名，H2、MySQL 等按实际部署填写。
     */
    private String driverClassName = "com.mysql.cj.jdbc.Driver";

    /**
     * 第二数据源连接池名称，便于在日志中区分连接来源。
     */
    @NotBlank(message = "第二数据源连接池名称不能为空")
    private String poolName = "forge-secondary-pool";

    /**
     * 第二数据源连接池上限。
     */
    @Min(value = 1, message = "第二数据源连接池上限不能小于 1")
    private int maximumPoolSize = 5;

    /**
     * 第二数据源连接池最小空闲连接数。
     */
    @Min(value = 0, message = "第二数据源连接池最小空闲数不能为负")
    private int minimumIdle = 1;

    /**
     * 判断第二数据源配置是否完整。
     *
     * <p>只在启用时校验：未启用时不要求填写连接信息，也不注册数据源。</p>
     *
     * @return 启用且连接地址与用户名都不为空时返回 {@code true}
     */
    public boolean isConfigured() {
        return enabled && !url.isBlank() && !username.isBlank();
    }
}
