# Java 编码规范

## 1. 命名与分层

Maven groupId 为 `cn.orangenode`，Java 根包为 `cn.orangenode.forge`。包名小写，类名 UpperCamelCase，方法及字段 lowerCamelCase，常量 UPPER_SNAKE_CASE。

先按业务功能分包，再按 Controller、Entity、Request、Response、Service、Mapper、Converter 等分层。每个业务 Service 使用接口与 `impl` 实现类；技术适配器只有实际抽象需求时才增加接口。

推荐命名（业务名仅作示例，按实际业务替换）：`NoticeController`、`NoticeEntity`、`NoticeCreateRequest`、`NoticeUpdateRequest`、`NoticePageRequest`、`NoticeDetailResponse`、`NoticeService`、`NoticeServiceImpl`、`NoticeMapper`、`NoticeConverter`。

管理员与用户 Controller 放在 `controller.admin`、`controller.app`。Service 面向业务用例，不能只暴露 MyBatis-Plus 通用 CRUD。

## 2. 方法与类说明

所有手写类、接口和枚举都写中文职责说明。所有手写方法及构造器使用中文多行 Javadoc，覆盖：

- public、protected、包可见、private 方法。
- 接口声明、实现、重写和默认方法。
- 配置工厂方法、工具方法、测试方法。
- 手写构造器和访问器。

Lombok、代码生成器生成的方法不逐个补注释。Lambda 不要求伪造方法 Javadoc；复杂回调提取为具名方法。生成代码必须有清晰来源，不能靠“生成代码”标签规避业务代码规则。

~~~java
/**
 * 注册普通用户。
 */
MemberRegisterResponse register(MemberRegisterRequest request);
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

技术配置优先使用类型化配置类；环境配置必须有用途说明和必要校验，不能到处散落字符串 Key。

## 5. 数据与事务

- Entity 与业务层 ID 为 Long，自增生成；对外 ID 为 String，转换时验证正数及 Long 范围。
- 时间使用能够清楚表达 UTC 语义的类型；数据库 DATETIME 与 Instant 的映射显式按 UTC 转换并验证。
- 金额使用 BigDecimal；精度、币种和舍入规则由具体业务定义。
- `@Transactional` 放在对外业务实现方法，异常触发回滚；包含 checked exception 的写事务显式指定回滚范围。
- `@DS` 在事务开始前选择数据源；需要代理的方法通过 Spring Bean 边界调用，禁止靠同类自调用实现切换。
- MyBatis-Plus 简单查询使用类型安全条件；复杂 SQL 可使用 Mapper XML。排序列必须后端白名单映射，禁止拼接用户提供的 SQL 片段。
- 数据修改成功后才做对应缓存失效，不缓存未提交状态。

## 6. 校验、权限与异常

使用 `jakarta.validation` 注解，Controller 请求体触发 `@Valid`，方法参数校验按当前 Spring 版本的实际行为配置并测试。新增、修改、查询使用各自 Request，只有确有共同结构时才使用分组。

权限使用 Spring Security `@PreAuthorize`。Controller 声明 HTTP 用例权限，Service 仍负责业务归属和可信调用条件。后台传来的字段不能替代认证上下文。

业务失败抛出携带统一 code 的业务异常。应用可处理的接口响应统一为 HTTP 200，成功 code=0，异常在 body.code 中使用 400、401、403、500 等数值。未知异常由全局处理器记录，不在每层重复 catch 和重复打印。

ControllerAdvice、Security 的认证失败与拒绝访问处理器、框架默认错误出口复用统一错误写出组件。禁止用 sendError、非 200 的异常状态注解或默认登录重定向绕过协议；错误被转换为 HTTP 200 后，仍必须终止被拒绝的业务调用。后端验收同时断言 HTTP 状态与 body.code。

禁止返回密码摘要、令牌、存储密钥、数据库连接配置或堆栈。审计与运行日志使用身份类型、对象 ID 和 traceId 关联。

## 7. 格式与检查

Java 四空格缩进，UTF-8 无 BOM，LF 换行，禁止通配符 import，移除未使用 import。

M1 接入 Checkstyle 并用临时反例验证：private 方法、接口方法、实现方法、构造器及测试方法遗漏注释都能被检测；检测不应把 Lambda 当成手写方法。若标准规则不能完整覆盖，增加窄范围补充检查，不降低要求。

使用 ArchUnit 校验模块无循环、Controller 不访问 Mapper、基础模块不依赖业务模块。后端测试围绕业务结果和关键失败路径，不为简单访问器写镜像测试。
