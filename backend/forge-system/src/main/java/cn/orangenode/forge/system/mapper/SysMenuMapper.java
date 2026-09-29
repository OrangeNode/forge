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
}
