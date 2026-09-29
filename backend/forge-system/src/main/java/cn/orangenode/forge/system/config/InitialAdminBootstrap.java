package cn.orangenode.forge.system.config;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.system.credential.AdminCredentialPolicy;
import cn.orangenode.forge.system.entity.SysAdminEntity;
import cn.orangenode.forge.system.entity.SysRoleEntity;
import cn.orangenode.forge.system.enums.AdminAccountStatus;
import cn.orangenode.forge.system.mapper.SysAdminMapper;
import cn.orangenode.forge.system.mapper.SysAdminRoleMapper;
import cn.orangenode.forge.system.mapper.SysRoleMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 初始管理员引导。
 *
 * <p>账号表为空时按环境提供的凭据创建第一个管理员，并关联配置指定的超级管理员角色；
 * 已有管理员时不做任何修改，因此重启不会重置密码，也不会把生产账号覆盖成初始口令。</p>
 *
 * <p>没有账号又没有凭据时直接让应用启动失败：继续运行只会得到一个无法登录的后台，
 * 明确失败比静默启动一个不可用实例更容易发现。</p>
 */
@Slf4j
@Component
public class InitialAdminBootstrap implements ApplicationRunner {

    /**
     * 初始管理员引导配置。
     */
    private final ForgeSystemBootstrapProperties properties;

    /**
     * 认证与权限配置，提供超级管理员角色代码。
     */
    private final ForgeSecurityProperties securityProperties;

    /**
     * 管理员账号数据访问。
     */
    private final SysAdminMapper adminMapper;

    /**
     * 角色数据访问。
     */
    private final SysRoleMapper roleMapper;

    /**
     * 管理员与角色关系数据访问。
     */
    private final SysAdminRoleMapper adminRoleMapper;

    /**
     * 密码编码器。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造初始管理员引导。
     *
     * @param properties         初始管理员引导配置
     * @param securityProperties 认证与权限配置
     * @param adminMapper        管理员账号数据访问
     * @param roleMapper         角色数据访问
     * @param adminRoleMapper    管理员与角色关系数据访问
     * @param passwordEncoder    密码编码器
     */
    public InitialAdminBootstrap(ForgeSystemBootstrapProperties properties,
            ForgeSecurityProperties securityProperties, SysAdminMapper adminMapper, SysRoleMapper roleMapper,
            SysAdminRoleMapper adminRoleMapper, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.securityProperties = securityProperties;
        this.adminMapper = adminMapper;
        this.roleMapper = roleMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 应用启动后按需创建初始管理员。
     *
     * @param args 启动参数，本引导不读取
     * @throws IllegalStateException 缺少必需凭据或缺少内置角色时抛出，阻止应用带病启动
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("初始管理员引导已关闭（forge.system.bootstrap.enabled=false）");
            return;
        }
        Long adminCount = adminMapper.selectCount(null);
        if (adminCount != null && adminCount > 0) {
            log.info("已存在 {} 个管理员账号，跳过初始管理员引导", adminCount);
            return;
        }
        String username = AdminCredentialPolicy.normalizeUsername(properties.getUsername());
        String password = properties.getPassword();
        if (!AdminCredentialPolicy.isValidUsername(username)
                || !AdminCredentialPolicy.isValidNewPassword(password)) {
            throw new IllegalStateException("数据库中没有任何管理员账号，且未提供合法的初始管理员凭据："
                    + "请通过环境变量 FORGE_INIT_ADMIN_USERNAME（4—32 个字符）与 FORGE_INIT_ADMIN_PASSWORD"
                    + "（" + AdminCredentialPolicy.PASSWORD_MIN_LENGTH + "—"
                    + AdminCredentialPolicy.PASSWORD_MAX_LENGTH + " 个字符）提供后重启应用");
        }
        SysRoleEntity superRole = findSuperRole();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        SysAdminEntity admin = new SysAdminEntity();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDisplayName(resolveDisplayName());
        admin.setStatus(AdminAccountStatus.enabled.name());
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
        adminRoleMapper.insertRelation(admin.getId(), superRole.getId(), now, null);
        log.info("已创建初始管理员 [{}] 并授予角色 [{}]，请登录后及时修改初始密码", username, superRole.getCode());
    }

    /**
     * 查询配置指定的超级管理员角色。
     *
     * @return 角色实体
     * @throws IllegalStateException 未找到角色时抛出，通常意味着数据库迁移未执行
     */
    private SysRoleEntity findSuperRole() {
        String roleCode = securityProperties.getSuperRoleCode().trim().toLowerCase(Locale.ROOT);
        SysRoleEntity role = roleMapper.selectOne(Wrappers.<SysRoleEntity>lambdaQuery()
                .eq(SysRoleEntity::getCode, roleCode));
        if (role == null) {
            throw new IllegalStateException("未找到超级管理员角色 [" + roleCode
                    + "]；请确认数据库迁移已执行，或检查 forge.security.super-role-code 配置");
        }
        return role;
    }

    /**
     * 解析初始管理员显示名称，未配置时使用默认值。
     *
     * @return 显示名称
     */
    private String resolveDisplayName() {
        String displayName = properties.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            return properties.getUsername();
        }
        return displayName.trim();
    }
}
