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
 * <p>菜单可见性来自管理员的角色：只有角色关联到的菜单才会下发给前端，
 * 前端再用本地路由白名单校验一次，两边都不执行远程传来的组件代码。</p>
 *
 * <p>角色与菜单关系表物理删除，因此统计引用与父子关系的语句直接读取关系表，
 * 不附加逻辑删除过滤；菜单主表自身仍过滤 {@code deleted}。</p>
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenuEntity> {

    /**
     * 查询管理员通过角色可见的菜单。
     *
     * @param adminId 管理员 ID
     * @return 去重并按排序号、主键排序的菜单列表，没有可见菜单时返回空列表
     */
    @Select("select distinct m.id, m.parent_id, m.name, m.route_key, m.sort_no from sys_menu m "
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
    @Select("select id, parent_id, name, route_key, sort_no from sys_menu where deleted = 0 order by sort_no, id")
    List<SysMenuEntity> selectAllMenus();

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
