package cn.orangenode.forge.system.mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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
 *
 * <p>管理员角色分配是全量替换语义：先按管理员删除旧关系，再逐条插入新关系，
 * 因此不会出现重复插入导致的主键冲突。</p>
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

    /**
     * 删除管理员的全部角色关联。
     *
     * <p>用于角色分配的全量替换，删除后由调用方写入新的角色集合。</p>
     *
     * @param adminId 管理员 ID
     * @return 受影响行数
     */
    @Delete("delete from sys_admin_role where admin_id = #{adminId}")
    int deleteByAdminId(@Param("adminId") Long adminId);

    /**
     * 统计角色被管理员引用的数量。
     *
     * <p>角色被任一管理员引用时不允许删除：先由调用方解除分配，不做静默级联删除。</p>
     *
     * @param roleId 角色 ID
     * @return 管理员与角色关系数量
     */
    @Select("select count(*) from sys_admin_role where role_id = #{roleId}")
    int countByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询管理员当前关联的角色 ID。
     *
     * @param adminId 管理员 ID
     * @return 角色 ID 列表，没有角色时返回空列表
     */
    @Select("select role_id from sys_admin_role where admin_id = #{adminId} order by role_id")
    List<Long> selectRoleIdsByAdminId(@Param("adminId") Long adminId);

    /**
     * 批量查询多个管理员的角色关联。
     *
     * <p>供列表接口使用：管理员分页结果一次取回全部角色关联，避免逐行查询造成 N+1。
     * 关系表物理删除且只有组合主键，因此直接用映射结果承载，不引入实体。</p>
     *
     * @param adminIds 管理员 ID 集合，必须非空
     * @return 管理员与角色关联列表
     */
    @Select("<script>select admin_id, role_id from sys_admin_role where admin_id in "
            + "<foreach item='adminId' collection='adminIds' open='(' separator=',' close=')'>#{adminId}</foreach>"
            + "</script>")
    List<AdminRoleRelation> selectRelationsByAdminIds(@Param("adminIds") Collection<Long> adminIds);

    /**
     * 管理员与角色的关联行。
     *
     * <p>只承载关系表的两列，供列表接口成批读取角色关联。</p>
     *
     * @param adminId 管理员 ID
     * @param roleId  角色 ID
     */
    record AdminRoleRelation(Long adminId, Long roleId) {
    }
}
