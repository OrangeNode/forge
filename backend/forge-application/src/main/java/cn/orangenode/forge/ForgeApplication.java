package cn.orangenode.forge;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Orange Forge 应用启动入口。
 *
 * <p>本模块是唯一可执行模块，负责装配已纳入 reactor 的各个模块。
 * 扫描根包为 {@code cn.orangenode.forge}，覆盖全部已装配模块。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan(basePackages = "cn.orangenode.forge", annotationClass = Mapper.class)
public class ForgeApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ForgeApplication.class, args);
    }
}
