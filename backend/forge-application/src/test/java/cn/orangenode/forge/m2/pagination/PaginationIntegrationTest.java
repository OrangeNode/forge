package cn.orangenode.forge.m2.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.framework.page.PageRequest;

/**
 * 分页能力验证。
 *
 * <p>使用内存数据库装配真实 MyBatis-Plus 与 dynamic-datasource，验证分页参数确实改写 SQL、
 * 翻页返回的是结果集中对应的一段、超出最后一页返回空数组与真实总数。</p>
 *
 * <p>探针表刻录 5 条固定主键数据且查询不带排序，因此断言与内存库的扫描顺序绑定；
 * 业务接口应按自己的查询语义决定排序，本用例只固定分页行为本身。</p>
 *
 * <p>Redis 用替身替换，本用例不验证 Redis 行为，避免依赖外部中间件。</p>
 */
@SpringBootTest(properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_page;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2"
})
class PaginationIntegrationTest {

    /**
     * Redis 替身，避免该用例依赖真实 Redis。
     */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 分页验证用例入口。
     */
    @Autowired
    private PaginationProbeService probeService;

    /**
     * 直接执行建表与数据清理，避免为测试夹具引入生产迁移脚本。
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 每个用例前重建探针表并写入 5 条固定主键的数据。
     */
    @BeforeEach
    void prepareData() {
        jdbcTemplate.execute("drop table if exists probe_record");
        jdbcTemplate.execute("""
                create table probe_record (
                  id bigint auto_increment primary key,
                  title varchar(64) not null,
                  priority int not null,
                  deleted tinyint not null default 0
                )
                """);
        jdbcTemplate.update("insert into probe_record (id, title, priority, deleted) values (?, ?, ?, 0)", 1, "记录一",
                30);
        jdbcTemplate.update("insert into probe_record (id, title, priority, deleted) values (?, ?, ?, 0)", 2, "记录二",
                10);
        jdbcTemplate.update("insert into probe_record (id, title, priority, deleted) values (?, ?, ?, 0)", 3, "记录三",
                20);
        jdbcTemplate.update("insert into probe_record (id, title, priority, deleted) values (?, ?, ?, 0)", 4, "记录四",
                10);
        jdbcTemplate.update("insert into probe_record (id, title, priority, deleted) values (?, ?, ?, 0)", 5, "记录五",
                40);
        jdbcTemplate.execute("alter table probe_record alter column id restart with 6");
    }

    /**
     * 验证分页参数改写 SQL：第一页与第二页取到结果集中不同的两段，且总数为真实总数。
     */
    @Test
    @DisplayName("分页参数改写 SQL 并返回真实总数")
    void shouldApplyPagination() {
        PageResponse<PaginationProbeResponse> firstPage = probeService.page(pageRequest(1, 2));
        PageResponse<PaginationProbeResponse> secondPage = probeService.page(pageRequest(2, 2));

        assertThat(firstPage.pageNum()).isEqualTo(1);
        assertThat(firstPage.pageSize()).isEqualTo(2);
        assertThat(firstPage.total()).isEqualTo(5);
        assertThat(firstPage.records()).hasSize(2);

        assertThat(secondPage.pageNum()).isEqualTo(2);
        assertThat(secondPage.pageSize()).isEqualTo(2);
        assertThat(secondPage.total()).isEqualTo(5);
        assertThat(secondPage.records()).hasSize(2);

        assertThat(firstPage.records()).extracting(PaginationProbeResponse::id).containsExactly("1", "2");
        assertThat(secondPage.records()).extracting(PaginationProbeResponse::id).containsExactly("3", "4");
        assertThat(firstPage.records()).doesNotContainAnyElementsOf(secondPage.records());
    }

    /**
     * 验证每页条数为 10 时一页取回全部记录，说明分页不会丢数据。
     */
    @Test
    @DisplayName("单页取回全部记录")
    void shouldReturnAllRecordsInSinglePage() {
        PageResponse<PaginationProbeResponse> response = probeService.page(pageRequest(1, 10));

        assertThat(response.records()).hasSize(5);
        assertThat(response.total()).isEqualTo(5);
        assertThat(response.records()).extracting(PaginationProbeResponse::id)
                .containsExactly("1", "2", "3", "4", "5");
    }

    /**
     * 验证超出最后一页返回空数组与真实总数，而不是 {@code null}。
     */
    @Test
    @DisplayName("超出最后一页返回空页与真实总数")
    void shouldReturnEmptyPageBeyondLastPage() {
        PageResponse<PaginationProbeResponse> response = probeService.page(pageRequest(9, 2));

        assertThat(response.records()).isEmpty();
        assertThat(response.total()).isEqualTo(5);
        assertThat(response.pageNum()).isEqualTo(9);
    }

    /**
     * 验证对外结构不暴露实体与 MyBatis-Plus 类型。
     */
    @Test
    @DisplayName("对外分页结果只含契约字段")
    void shouldExposeOnlyContractFields() {
        PageResponse<PaginationProbeResponse> response = probeService.page(pageRequest(1, 2));

        assertThat(response.records()).allSatisfy(record -> {
            assertThat(record.id()).isInstanceOf(String.class);
            assertThat(record.title()).isNotBlank();
            assertThat(record.priority()).isNotNull();
        });
    }

    /**
     * 构造分页入参。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页入参
     */
    private PageRequest pageRequest(int pageNum, int pageSize) {
        PageRequest request = new PageRequest();
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        return request;
    }
}
