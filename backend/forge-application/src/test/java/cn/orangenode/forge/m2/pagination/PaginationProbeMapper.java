package cn.orangenode.forge.m2.pagination;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 分页验证用的持久化接口。
 *
 * <p>属于测试夹具：继承 MyBatis-Plus 的 {@code BaseMapper}，分页查询直接用其内置的
 * {@code selectPage}，不额外自定义分页方法；不进入生产模块。</p>
 */
@Mapper
public interface PaginationProbeMapper extends BaseMapper<PaginationProbeEntity> {

    /**
     * 插入一条探针记录，主键由内存数据库自增生成。
     *
     * @param title    标题
     * @param priority 优先级
     * @return 受影响行数
     */
    @Insert("insert into probe_record (title, priority, deleted) values (#{title}, #{priority}, 0)")
    int insertRecord(@Param("title") String title, @Param("priority") int priority);

    /**
     * 删除全部探针记录，保证各用例互不影响。
     *
     * @return 受影响行数
     */
    @Delete("delete from probe_record")
    int deleteAllRecords();
}
