package cn.orangenode.forge.video.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.orangenode.forge.video.entity.SourceEntity;

/**
 * Source数据访问，仅由视频模块服务调用。
 */
@Mapper
public interface SourceMapper extends BaseMapper<SourceEntity> {
    /**
     * 初始化时连同删除记录一起检查，避免重启复活已删除来源。
     */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM video_source WHERE name = #{name} OR api_url = #{apiUrl}")
    long countSeed(@org.apache.ibatis.annotations.Param("name") String name,
            @org.apache.ibatis.annotations.Param("apiUrl") String apiUrl);
    /**
     * 在提交运行的事务中锁定来源，保证同站活动运行检查与排队原子化。
     */
    @org.apache.ibatis.annotations.Select("SELECT * FROM video_source WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SourceEntity lock(@org.apache.ibatis.annotations.Param("id") Long id);
}
