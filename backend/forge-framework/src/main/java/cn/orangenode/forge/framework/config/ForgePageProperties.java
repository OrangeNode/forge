package cn.orangenode.forge.framework.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页配置。
 *
 * <p>对应配置前缀 {@code forge.page}。默认页码、默认条数与每页条数上限都取自
 * application.yaml，便于检查与调整；上限同时用于分页请求参数的校验注解。</p>
 *
 * <p>三个取值使用字段级 {@code @Value} 注入。实测该配置类在 setter 绑定与构造器绑定
 * （含 {@code @DefaultValue}）下都无法读到 {@code forge.page.max-size} 的配置文件与命令行取值，
 * 字段级注入可以稳定读到；约束注解仍然生效，越界配置在启动阶段直接失败。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.page")
public class ForgePageProperties {

    /**
     * 未传递分页参数时使用的页码，来自 {@code forge.page.default-page-num}。
     */
    @Min(value = 1, message = "默认页码不能小于 1")
    @Value("${forge.page.default-page-num:1}")
    private int defaultPageNum = 1;

    /**
     * 未传递分页参数时使用的每页条数，来自 {@code forge.page.default-page-size}。
     */
    @Min(value = 1, message = "默认每页条数不能小于 1")
    @Value("${forge.page.default-page-size:20}")
    private int defaultPageSize = 20;

    /**
     * 每页条数上限，单次查询不得超过该值，来自 {@code forge.page.max-size}。
     */
    @Min(value = 1, message = "每页条数上限不能小于 1")
    @Max(value = 1000, message = "每页条数上限过大，会放大单次查询成本")
    @Value("${forge.page.max-size:100}")
    private int maxPageSize = 100;
}
