package cn.orangenode.forge.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.orangenode.forge.system.entity.SysPermissionEntity;

/**
 * 权限数据访问。
 *
 * <p>权限解析以数据库为事实来源：每次请求按管理员的角色实时查询，
 * 因此权限调整不需要等待缓存过期，也不存在“会话里的权限快照”这种过期数据。</p>
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermissionEntity> {

    /**
     * 查询管理员通过角色获得的权限代码。
     *
     * @param adminId 管理员 ID
     * @return 去重并按代码排序的权限代码列表，没有权限时返回空列表
     */
    @Select("select distinct p.code from sys_permission p "
            + "join sys_role_permission rp on rp.permission_id = p.id "
            + "join sys_admin_role ar on ar.role_id = rp.role_id "
            + "where ar.admin_id = #{adminId} and p.deleted = 0 order by p.code")
    List<String> selectPermissionCodesByAdminId(@Param("adminId") Long adminId);

    /**
     * 查询全部有效权限代码。
     *
     * <p>供超级管理员角色使用：该角色按配置识别，拥有全部有效权限，
     * 不要求逐条建立角色与权限关系。</p>
     *
     * @return 按代码排序的权限代码列表
     */
    @Select("select code from sys_permission where deleted = 0 order by code")
    List<String> selectAllPermissionCodes();
}
