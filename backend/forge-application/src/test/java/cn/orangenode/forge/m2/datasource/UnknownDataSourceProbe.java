package cn.orangenode.forge.m2.datasource;

import com.baomidou.dynamic.datasource.annotation.DS;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 未注册数据源名称的验证入口。
 *
 * <p>属于测试夹具：数据源名称来自后端代码而非请求参数，用于验证严格匹配模式下
 * 名称不存在时明确失败，不会静默回退主库。</p>
 */
@Service
public class UnknownDataSourceProbe {

    /**
     * 使用当前路由数据源执行 SQL 的模板。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造未注册数据源名称的验证入口。
     *
     * @param jdbcTemplate 路由数据源上的 JDBC 模板
     */
    public UnknownDataSourceProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 在未注册的数据源上执行查询，预期抛出数据源不存在异常。
     */
    @DS("not-exists")
    public void selectFromUnknown() {
        jdbcTemplate.queryForList("select 1");
    }
}
