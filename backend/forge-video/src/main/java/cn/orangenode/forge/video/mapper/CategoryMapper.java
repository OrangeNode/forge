package cn.orangenode.forge.video.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.orangenode.forge.video.entity.CategoryEntity;

/**
 * Category数据访问，仅由视频模块服务调用。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
}
