package cn.orangenode.forge.system.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.orangenode.forge.system.entity.SysAdminEntity;

/**
 * 管理员账号数据访问。
 *
 * <p>只由账号模块内部使用，其他模块通过公开服务或安全端口获取身份信息，
 * 不直接注入本 Mapper。</p>
 */
@Mapper
public interface SysAdminMapper extends BaseMapper<SysAdminEntity> {
}
