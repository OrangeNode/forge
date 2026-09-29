# Java 编码规范

## 1. 命名与分层

Maven groupId 为 `cn.orangenode`，Java 根包为 `cn.orangenode.forge`。包名小写，类名 UpperCamelCase，方法及字段 lowerCamelCase，常量 UPPER_SNAKE_CASE。

先按业务功能分包，再按 Controller、Entity、Request、Response、Service、Mapper、Converter 等分层。每个业务 Service 使用接口与 `impl` 实现类；技术适配器只有实际抽象需求时才增加接口。

推荐命名（业务名仅作示例，按实际业务替换）：`NoticeController`、`NoticeEntity`、`NoticeCreateRequest`、`NoticeUpdateRequest`、`NoticePageRequest`、`NoticeDetailResponse`、`NoticeService`、`NoticeServiceImpl`、`NoticeMapper`、`NoticeConverter`。

后台管理接口的 Controller 放在 `controller.admin`，不再区分用户端包。Service 面向业务用例，不能只暴露 MyBatis-Plus 通用 CRUD。

## 2. 方法与类说明

所有手写类、接口和枚举都写中文职责说明。所有手写方法及构造器使用中文多行 Javadoc，覆盖：

- public、protected、包可见、private 方法。
- 接口声明、实现、重写和默认方法。
- 配置工厂方法、工具方法、测试方法。
- 手写构造器和访问器。

Lombok、代码生成器生成的方法不逐个补注释。Lambda 不要求伪造方法 Javadoc；复杂回调提取为具名方法。生成代码必须有清晰来源，不能靠“生成代码”标签规避业务代码规则。

~~~java
/**
 * 启用管理员账号。
 */
AdminStatusResponse enable(AdminStatusRequest request);
~~~

简单方法一句说明即可。复杂方法按需要增加 `@param`、`@return`、`@throws`，说明调用者真正需要知道的约束。重写实现需要写职责，不能只用 `@Override` 或仅用继承注释代替。

注释说明目的，不用“处理”“执行”等空泛词，也不要求为每行代码添加重复注释。

## 3. 依赖注入与对象

- 使用构造器注入，必要依赖声明为 final，禁止字段注入。
- Lombok 允许 `@Getter`、`@Setter`、`@RequiredArgsConstructor` 等明确用途注解；禁止整类 `@Data`。
- Request、Response、Entity 分开定义。DTO 不因方便直接继承数据库 Entity。
- Converter 显式赋值，避免 BeanUtils、反射批量拷贝和无依据引入映射框架。
- 不默认让业务 Service 继承 MyBatis-Plus `IService`，不把 `BaseMapper` 暴露给 Controller。
- 不为尚未重复出现的代码预建通用基类；公共字段按实际表类型复用。

## 4. 各层边界

Controller 负责 HTTP 参数适配、Validation、权限声明及返回统一响应，不编排数据库操作。Service 负责业务授权、规则、资源归属、事务和跨模块公开服务调用。Mapper 只负责本模块数据访问。

`core` 保持无 Spring/数据库依赖。包含 Jakarta Validation 的分页入参等 Web 类型放在 `framework`，框架无关的分页结果放在 `core`。

M2 已提供的公共能力与位置（业务模块直接复用，不重复实现）：

| 能力 | 位置 | 使用要点 |
| --- | --- | --- |
| 统一响应与业务异常 | `core` 的 `ApiResponse`、`ErrorCode`、`BusinessException`、`DependencyUnavailableException`、`FieldError` | 成功响应传当前请求追踪编号；业务失败抛 `BusinessException`，依赖不可用抛 `DependencyUnavailableException` |
| 错误写出与全局处理 | `framework` 的 `web.error` | 不回显内部细节；新增失败语义先在本层映射，不散落 try-catch |
| 参数校验扩展 | `framework` 的 `validation`（`@ForgeEnum`） | 枚举参数按稳定代码校验，不自行 `valueOf` |
| 分页 | `framework` 的 `page`（`PageRequest`、`PageResponses`） | 入参用 `PageRequest`，查询用其页码条数构造 MyBatis-Plus `Page`，返回用 `PageResponses.from(...)`；不向接口层暴露 `IPage`。排序由具体业务查询的 SQL 决定，白名单等机制等真实列表接口出现时再设计 |
| 多数据源 | `framework` 的 `datasource` 与 `@DS` | 只在非事务入口方法上声明；名称来自配置与代码，不接受请求参数选择连接 |
| Redis | `framework` 的 `redis`（`ForgeRedisTemplate`） | 逻辑键名交给封装拼前缀，写入必须带 TTL，不用通配扫描删除；计数用 `increment(key, ttl)` |
| 令牌会话 | `framework` 的 `security`（`AdminTokenService`、`BearerTokens`、`AuthenticatedAdmin`） | 业务模块只负责登录用例：校验凭据后调用令牌会话签发；撤销当前令牌用 `revoke`，停用或改密用 `revokeAllForAdmin`。不自行生成或解析令牌 |
| 账号目录端口 | `framework` 的 `security`（`AdminAccountDirectory`、`AdminAccountView`） | 认证过滤链只依赖端口；由拥有账号表的业务模块实现，基础模块不接触密码编码结果与账号 Mapper |
| 登录失败限流 | `framework` 的 `security`（`LoginAttemptGuard`） | 登录前 `assertAllowed`、失败 `recordFailure`、成功 `clear`；不要在各业务里另写计数 |
| 方法级权限 | `Spring Security` 的 `@PreAuthorize` 与 `framework` 的 `GlobalExceptionHandler` | 权限码为 `模块:资源:动作`；方法级拒绝与过滤链拒绝都由统一出口写成 403，不要在业务里 catch 后返回自定义结构 |

M3 已落地的安全装配：`SecurityConfig` 使用无状态过滤链，只放行配置中的匿名路径与 CORS 预检，其余路径（含未知路径）一律要求认证；`PasswordEncoder` 使用 Spring Security 的 BCrypt 实现，业务模块不自建散列。

M4 与 M5 已提供的公共能力（业务模块直接复用）：

| 能力 | 位置 | 使用要点 |
| --- | --- | --- |
| 存储适配端口 | `framework` 的 `storage`（`StorageProvider`） | 只做对象读写与连接检测；实现由文件模块按配置版本创建，业务不直接调用 |
| 凭据加解密 | `framework` 的 `crypto`（`CredentialCipher`、`AesGcmCredentialCipher`） | 每次加密使用新 nonce；主密钥来自 `forge.file.encryption-key`，缺失时明确失败，不打印明文 |
| 操作审计 | `framework` 的 `audit`（`@AuditOperation`、`@AuditResourceId`、`AuditOperationAspect`） | 写接口加注解即可，业务不写审计逻辑；结果码映射与响应侧保持一致，审计写入失败只记日志 |
| 登录日志 | `framework` 的 `audit`（`LoginLogRecorder`） | 登录用例在成功与各失败路径调用；身份与追踪编号取自安全上下文与请求 |
| 公开文件服务 | `file` 的 `FileService` | 业务模块通过该接口上传、按 ID 读取与删除，不直接依赖存储适配或文件 Mapper |
| 权限解析与缓存 | `system` 的 `AdminAuthorityService` | 读权限走缓存（键含全局版本）；任何角色、权限或管理员角色变更在提交后调用 `evictAllPermissions()` 失效，不使用通配扫描 |
| 分页入参构造 | `framework` 的 `ForgePageProperties` | 查询接口按请求构造分页入参并校验，不把分页入参装配成单例 Bean |

M5 起权限解析带版本化缓存（`auth:perm:v{版本}:{管理员ID}`）：读取缓存失败按 Redis 不可用处理并返回 503，缓存内容损坏按未命中重新解析数据库。分页上限、默认页码与条数统一来自 `forge.page.*`，Controller 不再各自写死。

技术配置优先使用类型化配置类；环境配置必须有用途说明和必要校验，不能到处散落字符串 Key。

## 5. 数据与事务

- Entity 与业务层 ID 为 Long，自增生成；对外 ID 为 String，转换时验证正数及 Long 范围。
- 时间使用能够清楚表达 UTC 语义的类型；数据库 DATETIME 与 Instant 的映射显式按 UTC 转换并验证。
- 金额使用 BigDecimal；精度、币种和舍入规则由具体业务定义。
- `@Transactional` 放在对外业务实现方法，异常触发回滚；包含 checked exception 的写事务显式指定回滚范围。
- `@DS` 在事务开始前选择数据源；需要代理的方法通过 Spring Bean 边界调用，禁止靠同类自调用实现切换。`@DS` 依赖 Spring AOP 代理，缺少 AOP 自动配置时注解会被静默忽略并全部走主库，M2 已在 framework 引入 `spring-boot-starter-aspectj` 并有集成用例守护。
- MyBatis-Plus 简单查询使用类型安全条件；复杂 SQL 可使用 Mapper XML。排序列必须后端白名单映射，禁止拼接用户提供的 SQL 片段。
- 数据修改成功后才做对应缓存失效，不缓存未提交状态。

## 6. 校验、权限与异常

使用 `jakarta.validation` 注解，Controller 请求体触发 `@Valid`，方法参数校验按当前 Spring 版本的实际行为配置并测试。新增、修改、查询使用各自 Request，只有确有共同结构时才使用分组。

权限使用 Spring Security `@PreAuthorize`。Controller 声明 HTTP 用例权限，Service 仍负责业务归属和可信调用条件。后台传来的字段不能替代认证上下文。

业务失败抛出携带统一 code 的业务异常。应用可处理的接口响应统一为 HTTP 200，成功 code=0，异常在 body.code 中使用 400、401、403、500 等数值。未知异常由全局处理器记录，不在每层重复 catch 和重复打印。

ControllerAdvice、Security 的认证失败与拒绝访问处理器、框架默认错误出口复用统一错误写出组件。禁止用 sendError、非 200 的异常状态注解或默认登录重定向绕过协议；错误被转换为 HTTP 200 后，仍必须终止被拒绝的业务调用。后端验收同时断言 HTTP 状态与 body.code。

M2 的异常处理契约：可预期失败抛 `BusinessException`（或 `DependencyUnavailableException`），由 `GlobalExceptionHandler` 统一写出；校验失败由框架抛出并转换为 `data.fieldErrors`；未预期异常只在全局处理器记录一次，各层不重复捕获与打印；`@RestControllerAdvice` 中更具体的异常类型必须保留独立处理器，避免被 `DataAccessException` 等父类型吞掉语义。

禁止返回密码摘要、令牌、存储密钥、数据库连接配置或堆栈。审计与运行日志使用身份类型、对象 ID 和 traceId 关联。

## 7. 格式与检查

Java 四空格缩进，UTF-8 无 BOM，LF 换行，禁止通配符 import，移除未使用 import。

M1 已接入 Checkstyle：`backend/checkstyle.xml` 在 Maven validate 阶段检查全部模块的生产与测试源码，包含 private、接口、重写、构造器、紧凑构造器和访问器，不豁免短方法或 `@Override`。

`DocumentationRulesTest` 在 test 阶段加载同一份配置，按 Java 语法节点补充中文职责正文与方法多行 Javadoc 检查。检查范围由父 POM 的模块清单确定，不扫描 target 生成代码；Lambda 不作为方法声明检查。16 个用例覆盖真实源码、临时反例及正常对照，临时 Java 文件由 JUnit 自动清理。完整执行使用 `mvnw.cmd -B verify`，不能用跳过测试的构建声称中文多行规则已经通过。

使用 ArchUnit 校验模块无循环、Controller 不访问 Mapper、基础模块不依赖业务模块。后端测试围绕业务结果和关键失败路径，不为简单访问器写镜像测试。
