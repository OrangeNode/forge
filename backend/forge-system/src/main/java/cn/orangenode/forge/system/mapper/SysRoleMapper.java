package cn.orangenode.forge.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.orangenode.forge.system.entity.SysRoleEntity;

/**
 * 角色数据访问。
 *
 * <p>包含管理员与角色的关联查询：关联表为物理删除，因此查询直接使用连接条件，
 * 不再附加逻辑删除过滤，角色主表自身仍过滤 {@code deleted}。</p>
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRoleEntity> {

    /**
     * 查询管理员当前拥有的角色代码。
     *
     * @param adminId 管理员 ID
     * @return 角色代码列表，没有角色时返回空列表
     */
    @Select("select r.code from sys_role r "
            + "join sys_admin_role ar on ar.role_id = r.id "
            + "where ar.admin_id = #{adminId} and r.deleted = 0")
    List<String> selectRoleCodesByAdminId(@Param("adminId") Long adminId);
}
