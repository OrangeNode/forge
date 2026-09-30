package cn.orangenode.forge.system.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
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
 *
 * <p>角色与菜单关系表为物理删除：授权是全量替换语义，先按角色删除旧关系再逐条插入新关系，
 * 避免主键重复插入。权限标识不再有独立关系表，它由角色可见菜单节点上的配置推导。</p>
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

    /**
     * 删除角色的全部菜单关系。
     *
     * <p>用于授权全量替换：关系表物理删除，删除后由调用方写入新的关系集合。</p>
     *
     * @param roleId 角色 ID
     * @return 受影响行数
     */
    @Delete("delete from sys_role_menu where role_id = #{roleId}")
    int deleteRoleMenus(@Param("roleId") Long roleId);

    /**
     * 建立角色与菜单的关系。
     *
     * @param roleId    角色 ID
     * @param menuId    菜单 ID
     * @param createdAt 关联创建时间（UTC）
     * @param createdBy 操作者管理员 ID
     * @return 受影响行数
     */
    @Insert("insert into sys_role_menu (role_id, menu_id, created_at, created_by) "
            + "values (#{roleId}, #{menuId}, #{createdAt}, #{createdBy})")
    int insertRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId,
            @Param("createdAt") LocalDateTime createdAt, @Param("createdBy") Long createdBy);

    /**
     * 查询角色已授予的菜单 ID。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表，没有授权时返回空列表
     */
    @Select("select menu_id from sys_role_menu where role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色已授予菜单声明的权限标识列内容。
     *
     * <p>用于角色详情展示该角色实际获得的接口权限：权限来自菜单节点的配置，
     * 因此这里只取出列内容，切分去重由 {@code MenuPermissionCodes} 完成。</p>
     *
     * @param roleId 角色 ID
     * @return 权限标识列内容列表，没有授权时返回空列表
     */
    @Select("select distinct m.perm_codes from sys_menu m "
            + "join sys_role_menu rm on rm.menu_id = m.id "
            + "where rm.role_id = #{roleId} and m.deleted = 0 and m.perm_codes is not null")
    List<String> selectPermissionCodeTextsByRoleId(@Param("roleId") Long roleId);
}
