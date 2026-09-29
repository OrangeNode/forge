package cn.orangenode.forge.m3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.system.config.ForgeSystemBootstrapProperties;
import cn.orangenode.forge.system.config.InitialAdminBootstrap;
import cn.orangenode.forge.system.entity.SysAdminEntity;
import cn.orangenode.forge.system.entity.SysRoleEntity;
import cn.orangenode.forge.system.enums.AdminAccountStatus;
import cn.orangenode.forge.system.mapper.SysAdminMapper;
import cn.orangenode.forge.system.mapper.SysAdminRoleMapper;
import cn.orangenode.forge.system.mapper.SysRoleMapper;

/**
 * 初始管理员引导的行为验证。
 *
 * <p>使用替身 Mapper 覆盖引导的判定分支：关闭时不查询、已有账号时不覆盖、
 * 无凭据时明确失败、凭据合法时按编码结果创建并关联超级管理员角色。</p>
 *
 * <p>引导在真实数据库上的完整链路（创建后可以登录）由认证集成用例验证。</p>
 */
@ExtendWith(MockitoExtension.class)
class InitialAdminBootstrapTest {

    /**
     * 管理员账号数据访问替身。
     */
    @Mock
    private SysAdminMapper adminMapper;

    /**
     * 角色数据访问替身。
     */
    @Mock
    private SysRoleMapper roleMapper;

    /**
     * 管理员与角色关系数据访问替身。
     */
    @Mock
    private SysAdminRoleMapper adminRoleMapper;

    /**
     * 认证与权限配置，使用默认超级管理员角色代码。
     */
    private final ForgeSecurityProperties securityProperties = new ForgeSecurityProperties();

    /**
     * 真实密码编码器，用于验证落库的是编码结果而不是明文。
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 引导配置，每个用例按需要设置。
     */
    private ForgeSystemBootstrapProperties bootstrapProperties;

    /**
     * 被验证的引导实例。
     */
    private InitialAdminBootstrap bootstrap;

    /**
     * 每个用例前重建引导配置与实例。
     */
    @BeforeEach
    void prepareBootstrap() {
        bootstrapProperties = new ForgeSystemBootstrapProperties();
        bootstrapProperties.setUsername("bootstrap-admin");
        bootstrapProperties.setPassword("bootstrap-pass");
        bootstrap = new InitialAdminBootstrap(bootstrapProperties, securityProperties, adminMapper, roleMapper,
                adminRoleMapper, passwordEncoder);
    }

    /**
     * 验证关闭引导时不访问任何数据。
     */
    @Test
    @DisplayName("关闭引导时不访问数据")
    void shouldDoNothingWhenDisabled() {
        bootstrapProperties.setEnabled(false);

        bootstrap.run(null);

        verifyNoInteractions(adminMapper, roleMapper, adminRoleMapper);
    }

    /**
     * 验证已经存在管理员时不创建账号、不修改现有数据。
     */
    @Test
    @DisplayName("已有管理员时跳过创建")
    void shouldSkipWhenAdminExists() {
        when(adminMapper.selectCount(any())).thenReturn(2L);

        bootstrap.run(null);

        verify(adminMapper, never()).insert(any(SysAdminEntity.class));
        verifyNoInteractions(roleMapper, adminRoleMapper);
    }

    /**
     * 验证没有账号又没有凭据时明确失败，避免装出无法登录的实例。
     */
    @Test
    @DisplayName("缺少凭据且没有账号时明确失败")
    void shouldFailWithoutCredentials() {
        bootstrapProperties.setUsername(null);
        bootstrapProperties.setPassword(null);
        when(adminMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> bootstrap.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FORGE_INIT_ADMIN_USERNAME");
        verify(adminMapper, never()).insert(any(SysAdminEntity.class));
    }

    /**
     * 验证密码过短时拒绝启动，同样不创建账号。
     */
    @Test
    @DisplayName("初始密码过短时拒绝启动")
    void shouldFailWhenPasswordTooShort() {
        bootstrapProperties.setPassword("1234");
        when(adminMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalStateException.class);
        verify(adminMapper, never()).insert(any(SysAdminEntity.class));
    }

    /**
     * 验证凭据合法时创建管理员并关联超级管理员角色，密码按编码结果落库。
     */
    @Test
    @DisplayName("凭据合法时创建管理员并关联超级管理员角色")
    void shouldCreateAdminAndGrantSuperRole() {
        SysRoleEntity superRole = new SysRoleEntity();
        superRole.setId(7L);
        superRole.setCode("super_admin");
        when(adminMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectOne(any())).thenReturn(superRole);
        when(adminMapper.insert(any(SysAdminEntity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysAdminEntity.class).setId(42L);
            return 1;
        });

        bootstrap.run(null);

        ArgumentCaptor<SysAdminEntity> captor = ArgumentCaptor.forClass(SysAdminEntity.class);
        verify(adminMapper).insert(captor.capture());
        SysAdminEntity created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo("bootstrap-admin");
        assertThat(created.getStatus()).isEqualTo(AdminAccountStatus.enabled.name());
        assertThat(created.getPasswordHash()).startsWith("$2");
        assertThat(created.getPasswordHash()).isNotEqualTo("bootstrap-pass");
        assertThat(passwordEncoder.matches("bootstrap-pass", created.getPasswordHash())).isTrue();
        verify(adminRoleMapper).insertRelation(eq(42L), eq(7L), any(), isNull());
    }
}
