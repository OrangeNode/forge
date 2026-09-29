package cn.orangenode.forge.m2.datasource;

import java.util.List;

import com.baomidou.dynamic.datasource.annotation.DS;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 多数据源路由验证用例入口。
 *
 * <p>属于测试夹具：分别向主库与第二库执行读写，用于验证 {@code @DS} 在事务开始前选库、
 * 数据源上下文在调用结束后清空，以及单库事务的原子性。</p>
 *
 * <p>跨库原子事务不在支持范围内：两个数据库各自独立提交或回滚。</p>
 */
@Service
public class DataSourceProbeService {

    /**
     * 使用当前路由数据源执行 SQL 的模板。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造多数据源路由验证用例入口。
     *
     * @param jdbcTemplate 路由数据源上的 JDBC 模板
     */
    public DataSourceProbeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 在主库上创建探针表。
     */
    @DS("master")
    public void createPrimaryTable() {
        jdbcTemplate.execute("create table if not exists route_probe (id bigint auto_increment primary key, "
                + "marker varchar(32) not null)");
    }

    /**
     * 在第二库上创建同名探针表，用于验证两库确实相互独立。
     */
    @DS("secondary")
    public void createSecondaryTable() {
        jdbcTemplate.execute("create table if not exists route_probe (id bigint auto_increment primary key, "
                + "marker varchar(32) not null)");
    }

    /**
     * 清空主库探针表，保证各用例数据互不影响。
     */
    @DS("master")
    public void clearPrimary() {
        jdbcTemplate.update("delete from route_probe");
    }

    /**
     * 清空第二库探针表，保证各用例数据互不影响。
     */
    @DS("secondary")
    public void clearSecondary() {
        jdbcTemplate.update("delete from route_probe");
    }

    /**
     * 在主库写入一行标记。
     *
     * @param marker 标记内容
     */
    @DS("master")
    public void insertPrimary(String marker) {
        jdbcTemplate.update("insert into route_probe (marker) values (?)", marker);
    }

    /**
     * 在第二库写入一行标记。
     *
     * @param marker 标记内容
     */
    @DS("secondary")
    public void insertSecondary(String marker) {
        jdbcTemplate.update("insert into route_probe (marker) values (?)", marker);
    }

    /**
     * 读取主库中的全部标记。
     *
     * @return 标记列表
     */
    @DS("master")
    public List<String> selectPrimary() {
        return jdbcTemplate.queryForList("select marker from route_probe order by id", String.class);
    }

    /**
     * 读取第二库中的全部标记。
     *
     * @return 标记列表
     */
    @DS("secondary")
    public List<String> selectSecondary() {
        return jdbcTemplate.queryForList("select marker from route_probe order by id", String.class);
    }

    /**
     * 在单个数据库内写入两行后抛出异常，用于验证单库事务回滚。
     *
     * @param marker 回滚用例的标记内容
     */
    @DS("master")
    @Transactional
    public void insertThenFail(String marker) {
        jdbcTemplate.update("insert into route_probe (marker) values (?)", marker);
        jdbcTemplate.update("insert into route_probe (marker) values (?)", marker + "-second");
        throw new IllegalStateException("rollback-probe");
    }

    /**
     * 在第二库执行查询，用于验证上下文清理后再次路由仍然正确。
     *
     * @return 第二库中的标记数量
     */
    @DS("secondary")
    public int countSecondary() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from route_probe", Integer.class);
        return count == null ? 0 : count;
    }
}
