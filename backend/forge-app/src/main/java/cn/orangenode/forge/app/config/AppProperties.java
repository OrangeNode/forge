package cn.orangenode.forge.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    /**
     * 同一普通用户每天最多提交的意见反馈与播放报错条数。
     */
    @Min(1)
    @Max(1000)
    private int feedbackDailyLimit = 20;

    /**
     * 版本更新只接受该包名的 APK。
     */
    @NotBlank
    private String packageName = "cn.orangenode.orange_app";
}
