package cn.orangenode.forge.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis-Plus 插件装配。
 *
 * <p>分页插件是分页查询生效的前提：未装配时 {@code Page} 参数不会改写 SQL，
 * 查询会退化为全量返回。因此这里显式装配，并由配置决定分页方言。</p>
 *
 * <p>分页条数上限不在此处设置，统一由入参校验按 {@code forge.page.max-size} 拦截，
 * 避免出现“插件静默截断”的隐式行为。</p>
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    /**
     * 装配 MyBatis-Plus 分页插件。
     *
     * @param properties 分页与数据库类型配置
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(ForgeMybatisPlusProperties properties) {
        DbType dbType = DbType.valueOf(properties.getDbType());
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(dbType);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);
        log.info("MyBatis-Plus 分页插件已装配，数据库类型为 [{}]", dbType);
        return interceptor;
    }
}
