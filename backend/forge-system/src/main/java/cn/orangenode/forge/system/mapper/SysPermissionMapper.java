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
 * <p>权限以数据库为事实来源：解析结果按全局权限版本缓存，写入方在数据变更提交后递增版本，
 * 因此权限调整在下一次请求立即生效，不存在“会话里的权限快照”这种过期数据。</p>
 *
 * <p>角色与权限关系表物理删除，统计引用时直接读取关系表，不附加逻辑删除过滤；
 * 权限主表自身仍过滤 {@code deleted}。</p>
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

    /**
     * 统计权限被角色引用的数量。
     *
     * <p>权限被任一角色引用时不允许删除：先由调用方解除授权，不做静默级联删除。</p>
     *
     * @param permissionId 权限 ID
     * @return 角色与权限关系数量
     */
    @Select("select count(*) from sys_role_permission where permission_id = #{permissionId}")
    int countRoleReferences(@Param("permissionId") Long permissionId);
}
