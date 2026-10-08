package cn.orangenode.forge.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * App 管理可调参数，唯一默认来源为 application.yaml 的 {@code forge.app}。
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.app")
public class AppProperties {

    /**
     * 首页推荐行单行最多影片数，后台填写的条数不能超过该值。
     */
    @Min(1)
    @Max(100)
    private int sectionMaxItems = 30;
}
