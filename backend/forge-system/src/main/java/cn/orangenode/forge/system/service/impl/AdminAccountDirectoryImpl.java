package cn.orangenode.forge.system.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import cn.orangenode.forge.framework.security.AdminAccountDirectory;
import cn.orangenode.forge.framework.security.AdminAccountView;
import cn.orangenode.forge.system.entity.SysAdminEntity;
import cn.orangenode.forge.system.enums.AdminAccountStatus;
import cn.orangenode.forge.system.mapper.SysAdminMapper;
import cn.orangenode.forge.system.service.AdminAuthorityService;

/**
 * 管理员账号目录实现。
 *
 * <p>实现 framework 定义的安全端口，让认证过滤链只依赖接口：
 * 账号表由本模块拥有，framework 不接触密码编码结果与账号 Mapper。</p>
 *
 * <p>每次调用都读取数据库当前状态，不缓存账号与权限，
 * 因此停用、改密与权限调整会在下一次请求立即生效。</p>
 */
@Service
public class AdminAccountDirectoryImpl implements AdminAccountDirectory {

    /**
     * 管理员账号数据访问。
     */
    private final SysAdminMapper adminMapper;

    /**
     * 权限与角色解析。
     */
    private final AdminAuthorityService authorityService;

    /**
     * 构造管理员账号目录实现。
     *
     * @param adminMapper      管理员账号数据访问
     * @param authorityService 权限与角色解析
     */
    public AdminAccountDirectoryImpl(SysAdminMapper adminMapper, AdminAuthorityService authorityService) {
        this.adminMapper = adminMapper;
        this.authorityService = authorityService;
    }

    /**
     * 按管理员 ID 查询账号视图。
     *
     * @param adminId 管理员 ID
     * @return 账号视图，账号不存在时为空
     */
    @Override
    public Optional<AdminAccountView> findById(Long adminId) {
        if (adminId == null) {
            return Optional.empty();
        }
        SysAdminEntity entity = adminMapper.selectById(adminId);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(new AdminAccountView(entity.getId(), entity.getUsername(), entity.getDisplayName(),
                AdminAccountStatus.isEnabled(entity.getStatus())));
    }

    /**
     * 查询管理员当前拥有的权限代码。
     *
     * @param adminId 管理员 ID
     * @return 权限代码列表
     */
    @Override
    public List<String> loadAuthorityCodes(Long adminId) {
        return authorityService.loadPermissionCodes(adminId);
    }
}
