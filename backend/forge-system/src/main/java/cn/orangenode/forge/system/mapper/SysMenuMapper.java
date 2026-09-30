package cn.orangenode.forge.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.orangenode.forge.system.entity.SysMenuEntity;

/**
 * 菜单数据访问。
 *
 * <p>菜单既描述前端可见性，也承载接口权限标识：角色关联到的菜单节点既会下发给前端，
 * 也会把该节点声明的权限标识计入管理员的权限集合，因此不再需要单独的权限表。</p>
 *
 * <p>角色与菜单关系表物理删除，因此统计引用与父子关系的语句直接读取关系表，
 * 不附加逻辑删除过滤；菜单主表自身仍过滤 {@code deleted}。</p>
 *
 * <p>权限标识在库中以逗号分隔的列保存，解析与去重由 {@code MenuPermissionCodes} 完成：
 * SQL 只负责按管理员取出相关行，不在 SQL 里做字符串切分。</p>
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenuEntity> {

    /**
     * 查询管理员通过角色可见的菜单。
     *
     * <p>列名显式起别名：这条语句由 MyBatis 映射到实体，不经过 MyBatis-Plus 的字段填充，
     * 别名保证下划线列名能按属性名映射。</p>
     *
     * @param adminId 管理员 ID
     * @return 去重并按排序号、主键排序的菜单列表，没有可见菜单时返回空列表
     */
    @Select("select distinct m.id as id, m.parent_id as parentId, m.name as name, m.menu_type as menuType, "
            + "m.icon as icon, m.route_key as routeKey, m.perm_codes as permCodes, "
            + "m.permissions_json as permissionsJson, m.sort_no as sortNo from sys_menu m "
            + "join sys_role_menu rm on rm.menu_id = m.id "
            + "join sys_admin_role ar on ar.role_id = rm.role_id "
            + "where ar.admin_id = #{adminId} and m.deleted = 0 order by m.sort_no, m.id")
    List<SysMenuEntity> selectMenusByAdminId(@Param("adminId") Long adminId);

    /**
     * 查询全部有效菜单。
     *
     * <p>供超级管理员角色使用：该角色按配置识别，可见全部菜单，
     * 不要求为每个新增菜单逐条建立角色与菜单关系。</p>
     *
     * @return 按排序号、主键排序的菜单列表
     */
    @Select("select id as id, parent_id as parentId, name as name, menu_type as menuType, icon as icon, "
            + "route_key as routeKey, perm_codes as permCodes, permissions_json as permissionsJson, "
            + "sort_no as sortNo from sys_menu where deleted = 0 order by sort_no, id")
    List<SysMenuEntity> selectAllMenus();

    /**
     * 查询管理员通过角色获得的菜单节点上声明的权限标识列内容。
     *
     * <p>权限标识是菜单节点上的配置，因此权限解析就是“取出该管理员可见菜单节点声明的标识”合并去重；
     * 只返回非空列，调用方逐个解析。</p>
     *
     * @param adminId 管理员 ID
     * @return 权限标识列内容列表，没有授权时返回空列表
     */
    @Select("select distinct m.perm_codes from sys_menu m "
            + "join sys_role_menu rm on rm.menu_id = m.id "
            + "join sys_admin_role ar on ar.role_id = rm.role_id "
            + "where ar.admin_id = #{adminId} and m.deleted = 0 and m.perm_codes is not null")
    List<String> selectPermissionCodeTextsByAdminId(@Param("adminId") Long adminId);

    /**
     * 查询全部有效菜单节点上声明的权限标识列内容。
     *
     * <p>供超级管理员角色使用：该角色拥有全部有效权限，不要求逐条建立角色与菜单关系。</p>
     *
     * @return 权限标识列内容列表，没有配置时返回空列表
     */
    @Select("select perm_codes from sys_menu where deleted = 0 and perm_codes is not null")
    List<String> selectAllPermissionCodeTexts();

    /**
     * 查询除指定菜单外，其他有效菜单声明的权限标识列内容。
     *
     * <p>权限标识在全局范围内唯一：同一个代码如果被两个菜单声明，角色授予哪一个节点都能拿到该权限，
     * 权限来源会变得无法解释。新增与修改前用它做占用检查，查询不过滤逻辑删除，
     * 已删除节点仍占用标识，与路由标识的处理一致。</p>
     *
     * @param excludeMenuId 需要排除的菜单 ID，允许为 {@code null}
     * @return 其他菜单声明的权限标识列内容列表
     */
    @Select("<script>select perm_codes from sys_menu where perm_codes is not null "
            + "<if test='excludeMenuId != null'> and id != #{excludeMenuId}</if></script>")
    List<String> selectPermissionCodeTextsExcept(@Param("excludeMenuId") Long excludeMenuId);

    /**
     * 统计使用指定路由标识的菜单数量，包含已逻辑删除的菜单。
     *
     * <p>数据库唯一键在逻辑删除后仍占用标识，因此这里的统计不能用带逻辑删除过滤的通用查询：
     * 只看有效行会漏掉“已删除菜单仍占用标识”的情况，最终只能由唯一约束报冲突。</p>
     *
     * @param routeKey      路由标识，调用方保证非空
     * @param excludeMenuId 需要排除的菜单 ID，允许为 {@code null}
     * @return 使用该标识的菜单数量
     */
    @Select("<script>select count(*) from sys_menu where route_key = #{routeKey} "
            + "<if test='excludeMenuId != null'> and id != #{excludeMenuId}</if></script>")
    int countByRouteKey(@Param("routeKey") String routeKey, @Param("excludeMenuId") Long excludeMenuId);

    /**
     * 统计直接子菜单数量。
     *
     * <p>删除菜单前用于阻止留下无法到达的孤立节点；只统计未逻辑删除的子菜单。</p>
     *
     * @param parentId 父菜单 ID
     * @return 子菜单数量
     */
    @Select("select count(*) from sys_menu where parent_id = #{parentId} and deleted = 0")
    int countChildren(@Param("parentId") Long parentId);

    /**
     * 统计菜单被角色引用的数量。
     *
     * <p>菜单被任一角色的可见性关系引用时不允许删除：先由调用方解除授权，
     * 不做静默级联删除。</p>
     *
     * @param menuId 菜单 ID
     * @return 角色与菜单关系数量
     */
    @Select("select count(*) from sys_role_menu where menu_id = #{menuId}")
    int countRoleReferences(@Param("menuId") Long menuId);
}
