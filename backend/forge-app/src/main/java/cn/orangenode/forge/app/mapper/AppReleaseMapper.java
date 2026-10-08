package cn.orangenode.forge.app.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.orangenode.forge.app.entity.AppReleaseEntity;

/**
 * AppReleaseEntity 数据访问，只在 App 管理模块内部使用。
 */
@Mapper
public interface AppReleaseMapper extends BaseMapper<AppReleaseEntity> {
}
