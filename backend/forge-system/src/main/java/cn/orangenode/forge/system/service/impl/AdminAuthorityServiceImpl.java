package cn.orangenode.forge.system.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;
import cn.orangenode.forge.system.mapper.SysMenuMapper;
import cn.orangenode.forge.system.mapper.SysRoleMapper;
import cn.orangenode.forge.system.service.AdminAuthorityService;
import cn.orangenode.forge.system.support.MenuPermissionCodes;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 管理员权限与角色解析实现。
 *
 * <p>权限解析结果按管理员缓存：缓存键由全局权限版本与管理员 ID 组成，值是该管理员的权限代码列表，
 * 存活时间来自 {@code forge.security.permission-cache-ttl-seconds}。任何角色、菜单权限标识
 * 或管理员角色关系变更后，写入方递增全局权限版本，使全部既有缓存立即失效，
 * 不需要通配扫描删除，也不会出现“改了权限还要等缓存过期”。</p>
 *
 * <p>权限来源是菜单节点：角色授予的每个菜单节点在 {@code sys_menu.perm_codes} 上声明自己的接口权限，
 * 解析即“并集去重”，因此不再需要角色与权限的关系表。超级管理员角色按配置识别，拥有全部有效权限。</p>
 *
 * <p>缓存读写失败的处理分两类：Redis 不可用（连接失败）异常向上抛出，由统一错误出口返回
 * HTTP 200 + body.code=503，认证与授权不降级放行；缓存内容损坏只按未命中重新解析数据库，
 * 因为此时数据库仍是事实来源，跳过缓存不会引入不一致。</p>
 *
 * <p>角色代码与超级管理员判断每次查询数据库：它们数据量极小，且会话撤销、停用等场景
 * 依赖实时的角色读结果，不为它们增加第二套缓存失效通道。</p>
 */
@Slf4j
@Service
public class AdminAuthorityServiceImpl implements AdminAuthorityService {

    /**
     * 全局权限版本键的逻辑键名，完整键名由框架拼接项目与环境前缀。
     */
    private static final String PERMISSION_VERSION_KEY = "auth:perm-version";

    /**
     * 单个管理员权限缓存的逻辑键名前缀，格式为 {@code auth:perm:v{版本}:{管理员ID}}。
     */
    private static final String PERMISSION_CACHE_KEY_PREFIX = "auth:perm:v";

    /**
     * 角色数据访问。
     */
    private final SysRoleMapper roleMapper;

    /**
     * 菜单数据访问：权限标识从菜单节点声明解析。
     */
    private final SysMenuMapper menuMapper;

    /**
     * 认证与权限配置，提供超级管理员角色代码与权限缓存存活时间。
     */
    private final ForgeSecurityProperties securityProperties;

    /**
     * Redis 操作入口，负责键前缀与存活时间。
     */
    private final ForgeRedisTemplate redis;

    /**
     * JSON 映射器，用于序列化与反序列化权限代码列表。
     */
    private final JsonMapper jsonMapper;

    /**
     * 构造权限解析实现。
     *
     * @param roleMapper         角色数据访问
     * @param menuMapper         菜单数据访问
     * @param securityProperties 认证与权限配置
     * @param redis              Redis 操作入口
     * @param jsonMapper         JSON 映射器
     */
    public AdminAuthorityServiceImpl(SysRoleMapper roleMapper, SysMenuMapper menuMapper,
            ForgeSecurityProperties securityProperties, ForgeRedisTemplate redis, JsonMapper jsonMapper) {
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.securityProperties = securityProperties;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 解析管理员当前拥有的权限代码，命中缓存时直接返回缓存内容。
     *
     * <p>拥有超级管理员角色的管理员获得全部有效权限：初始化引导创建的第一个管理员
     * 必须能够管理工作台自身，否则空库安装后没有任何途径授权。超级管理员判断本身不缓存，
     * 因此为账号增加或移除该角色会在下一个请求生效。</p>
     *
     * @param adminId 管理员 ID
     * @return 权限代码列表
     */
    @Override
    public List<String> loadPermissionCodes(Long adminId) {
        long version = currentPermissionVersion();
        String cacheKey = cacheKey(version, adminId);
        String cached = redis.get(cacheKey);
        if (cached != null) {
            List<String> cachedCodes = readCachedCodes(cacheKey, cached);
            if (cachedCodes != null) {
                return cachedCodes;
            }
        }
        List<String> codes = isSuperAdmin(adminId)
                ? collectPermissionCodes(menuMapper.selectAllPermissionCodeTexts())
                : collectPermissionCodes(menuMapper.selectPermissionCodeTextsByAdminId(adminId));
        List<String> result = List.copyOf(codes);
        writeCachedCodes(cacheKey, result);
        return result;
    }

    /**
     * 合并菜单节点声明的权限标识列内容为去重后的权限代码列表。
     *
     * <p>一个节点可以声明多个权限，多个节点之间可能有重叠，因此先逐段解析再整体去重；
     * 排序固定按代码字典序，便于比较缓存内容与接口响应。</p>
     *
     * @param codeTexts 权限标识列内容列表
     * @return 去重并按代码排序的权限代码列表
     */
    private List<String> collectPermissionCodes(List<String> codeTexts) {
        List<String> codes = new ArrayList<>();
        for (String text : codeTexts) {
            for (String code : MenuPermissionCodes.parse(text)) {
                if (!codes.contains(code)) {
                    codes.add(code);
                }
            }
        }
        codes.sort(String::compareTo);
        return codes;
    }

    /**
     * 解析管理员当前拥有的角色代码。
     *
     * @param adminId 管理员 ID
     * @return 角色代码列表
     */
    @Override
    public List<String> loadRoleCodes(Long adminId) {
        return roleMapper.selectRoleCodesByAdminId(adminId);
    }

    /**
     * 判断管理员是否拥有超级管理员角色。
     *
     * <p>角色代码比较忽略大小写，避免配置大小写差异导致超级管理员失去权限。</p>
     *
     * @param adminId 管理员 ID
     * @return 拥有超级管理员角色时返回 {@code true}
     */
    @Override
    public boolean isSuperAdmin(Long adminId) {
        String superRoleCode = securityProperties.getSuperRoleCode();
        return roleMapper.selectRoleCodesByAdminId(adminId).stream()
                .anyMatch(code -> code != null && code.equalsIgnoreCase(superRoleCode));
    }

    /**
     * 读取当前全局权限版本。
     *
     * <p>版本键不存在时返回初始版本 {@code 0}；内容不是数字时同样按初始版本处理并记录告警，
     * 下一次权限变更会重新把它递增为合法数字。</p>
     *
     * @return 当前全局权限版本
     */
    @Override
    public long currentPermissionVersion() {
        String raw = redis.get(PERMISSION_VERSION_KEY);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException failure) {
            log.warn("全局权限版本内容非法，按初始版本处理");
            return 0L;
        }
    }

    /**
     * 递增全局权限版本，使全部权限缓存立即失效。
     *
     * <p>存活时间取权限缓存存活时间与令牌有效期中的较大值：只要还有可能被读取的权限缓存，
     * 其版本键就必须存在，否则版本键先过期会让旧缓存重新变成“当前版本”。</p>
     */
    @Override
    public void evictAllPermissions() {
        long permissionTtl = securityProperties.getPermissionCacheTtlSeconds();
        long tokenTtl = securityProperties.getTokenTtlSeconds();
        long version = redis.increment(PERMISSION_VERSION_KEY, Duration.ofSeconds(Math.max(permissionTtl, tokenTtl)));
        log.info("权限缓存已全部失效，当前全局权限版本为 {}", version);
    }

    /**
     * 拼接单个管理员的权限缓存逻辑键名。
     *
     * @param version 全局权限版本
     * @param adminId 管理员 ID
     * @return 逻辑键名
     */
    private String cacheKey(long version, Long adminId) {
        return PERMISSION_CACHE_KEY_PREFIX + version + ":" + adminId;
    }

    /**
     * 反序列化缓存的权限代码列表。
     *
     * <p>缓存内容损坏、结构不符或元素类型异常时返回 {@code null}，由调用方按未命中重新解析数据库，
     * 不因单条缓存损坏让请求失败。</p>
     *
     * @param cacheKey 缓存键名，用于日志关联
     * @param payload  缓存内容
     * @return 权限代码列表，内容不可用时返回 {@code null}
     */
    private List<String> readCachedCodes(String cacheKey, String payload) {
        try {
            List<?> values = jsonMapper.readValue(payload, List.class);
            if (values == null) {
                return null;
            }
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        } catch (JacksonException failure) {
            log.warn("权限缓存内容无法解析，按未命中处理并重新解析数据库，key={}", cacheKey);
            return null;
        }
    }

    /**
     * 写入权限缓存，写入失败只记录错误日志。
     *
     * <p>权限已经解析成功，缓存属于加速能力：Redis 瞬时写入失败不应让一个本可完成的请求失败，
     * 下一次请求会重新解析。读取路径仍然保持严格，Redis 不可用时明确失败而不是绕过缓存。</p>
     *
     * @param cacheKey 缓存键名
     * @param codes    权限代码列表
     */
    private void writeCachedCodes(String cacheKey, List<String> codes) {
        try {
            redis.set(cacheKey, jsonMapper.writeValueAsString(codes),
                    Duration.ofSeconds(securityProperties.getPermissionCacheTtlSeconds()));
        } catch (JacksonException failure) {
            log.error("权限缓存序列化失败，本次请求跳过缓存写入，key={}", cacheKey, failure);
        }
    }
}
