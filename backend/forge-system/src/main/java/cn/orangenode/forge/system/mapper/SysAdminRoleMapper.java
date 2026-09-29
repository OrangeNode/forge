package cn.orangenode.forge.system.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 管理员与角色关系数据访问。
 *
 * <p>关系表物理删除，没有逻辑删除字段：解除关系即真正移除授权。
 * 关系表只有组合主键，不建立实体类，直接声明所需语句。</p>
 */
@Mapper
public interface SysAdminRoleMapper {

    /**
     * 建立管理员与角色的关联。
     *
     * @param adminId   管理员 ID
     * @param roleId    角色 ID
     * @param createdAt 关联创建时间（UTC）
     * @param createdBy 操作者管理员 ID，初始化流程为 {@code null}
     * @return 受影响行数
     */
    @Insert("insert into sys_admin_role (admin_id, role_id, created_at, created_by) "
            + "values (#{adminId}, #{roleId}, #{createdAt}, #{createdBy})")
    int insertRelation(@Param("adminId") Long adminId, @Param("roleId") Long roleId,
            @Param("createdAt") LocalDateTime createdAt, @Param("createdBy") Long createdBy);

    /**
     * 统计管理员已有的角色关联数量。
     *
     * @param adminId 管理员 ID
     * @return 关联数量
     */
    @Select("select count(*) from sys_admin_role where admin_id = #{adminId}")
    int countByAdminId(@Param("adminId") Long adminId);

    /**
     * 查询管理员是否已关联指定角色。
     *
     * @param adminId 管理员 ID
     * @param roleId  角色 ID
     * @return 已关联返回 {@code 1}，否则返回 {@code 0}
     */
    @Select("select count(*) from sys_admin_role where admin_id = #{adminId} and role_id = #{roleId}")
    int countByAdminIdAndRoleId(@Param("adminId") Long adminId, @Param("roleId") Long roleId);

    /**
     * 删除管理员与角色的关联。
     *
     * @param adminId 管理员 ID
     * @param roleId  角色 ID
     * @return 受影响行数
     */
    @Delete("delete from sys_admin_role where admin_id = #{adminId} and role_id = #{roleId}")
    int deleteRelation(@Param("adminId") Long adminId, @Param("roleId") Long roleId);
}
