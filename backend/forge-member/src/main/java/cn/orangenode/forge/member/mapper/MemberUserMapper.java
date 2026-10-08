package cn.orangenode.forge.member.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.orangenode.forge.member.entity.MemberUserEntity;

/**
 * MemberUserEntity 数据访问，只在普通用户模块内部使用。
 */
@Mapper
public interface MemberUserMapper extends BaseMapper<MemberUserEntity> {
}
