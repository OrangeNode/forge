# A1-App后台与普通用户

对应 App 计划的 T1「身份与后台基础」。用户 2026-10-07 确认开始；本阶段只改后台仓库，Flutter 客户端仓库（`OrangeNode/orange-app`）属于 T2，不在本阶段。

> 2026-10-08 修订（用户名登录）：普通用户改为“用户名 + 密码”注册与登录，用户名为 4—20 位字母、数字或下划线、不区分大小写唯一；不再使用邮箱，删除邮箱验证码、找回密码与发信配置，忘记密码由管理员在后台重置；同一来源地址每日注册数受 `forge.member.register.ip-daily-limit` 限制。迁移 `V20261008150000`、`V20261008150100` 用原邮箱 @ 前部分生成用户名。以下为 2026-10-07 邮箱方案的原始记录。

## 1. 目标与已定决策

- 恢复普通用户体系，推翻 2026-09-29“只有管理员一种身份”的决定（见[架构设计](../架构设计.md)第 8 节变更记录）。
- 开放注册，只用邮箱 + 验证码，不用手机号；支持邮箱找回密码。验证码存 Redis，限制发送频率与尝试次数。SMTP 配置走 `application.yaml` + 环境变量；未配置时发送接口返回明确错误；测试中用替身发送器，日志不打印验证码。
- 未登录不能看；VIP 开关开启时非 VIP 也不能取播放线路；播放线路只返回 M3U8/MP4。
- VIP 第一版只做后台手动开通/延长；兑换码在 T4。
- 多设备：后台设置同时登录台数 N；App 登录上传设备编号（首次安装生成）与设备名，记录 IP；超出踢最早登录的设备；后台可看登录设备并强制下线。
- 普通用户令牌与管理员令牌、Redis 键、接口前缀完全分开；令牌 30 天滑动续期，可配置。
- 不在本阶段：版本管理、反馈处理、兑换码、扫码登录、历史与收藏、App 客户端。

## 2. 交付内容

### 后端

| 范围 | 内容 |
| --- | --- |
| framework | `member` 包：普通用户令牌（`MemberTokenService`，键 `member:session:{摘要}`）、过滤器（只在 `/api/app/` 下识别）、`CurrentMember`、账号目录端口、功能开关端口与 `AppFeatureResolver`、`PlaybackAccessGuard`；`catalog.VideoCatalogPort`；`storage.StoredFileReader`；`web.ClientAddresses`；OpenAPI app 分组移到这里并匹配 `/api/app/**`；管理员过滤器跳过普通用户前缀 |
| forge-member | 表 `member_user`、`member_device`、`member_vip_log`；App 接口 `/api/app/v1/member/auth/*`（验证码、注册、登录、找回密码、退出）与 `/profile`（资料、修改资料、修改密码）；后台接口 `/api/admin/v1/member/users`（查询、详情、启停、重置密码、延长/设置/取消 VIP、VIP 记录）与 `/devices`（查询、强制下线、用户全部下线）；SMTP 发信 `SmtpMemberMailSender` |
| forge-app | 表 `app_splash`、`app_banner`、`app_section`、`app_settings`；后台接口 `/api/admin/v1/app/splashes|banners|sections|features|hot-search`；App 接口 `/api/app/v1/app/launch`（匿名）、`/images/{id}`（匿名，只开放开屏与轮播引用的图片）、`/home`、`/hot-search`；功能开关端口实现 |
| forge-video | `/api/app/v1/video/**` 改为必须普通用户登录；`play-lines` 先调用观看授权，只返回 M3U8/MP4 分集，没有可播分集的线路不返回；实现 `VideoCatalogPort` |
| forge-file | 实现 `StoredFileReader`（复用文件用例，不暴露对象键） |
| 迁移 | `member/V20261007120000`、`V20261007120100`，`app/V20261007121000`、`V20261007121100`；菜单与权限由迁移写入 |
| 配置 | `forge.member.auth.*`、`forge.member.code.*`、`forge.member.mail.*`（`FORGE_MAIL_*` 环境变量）、`forge.app.section-max-items`；匿名路径收紧见 `forge.security.*` |

### 管理端

- 菜单“会员管理”：用户列表（筛选邮箱/状态/VIP，详情抽屉含登录设备与 VIP 记录，启停、重置密码、开通/延长/设置/取消 VIP、全部设备下线）、登录设备（筛选邮箱/在线，强制下线）。
- 菜单“App 管理”：开屏设置、首页轮播（跳转影片按片名搜索或选分类）、首页推荐位（按分类自动或手动挑片）、热搜设置（手动词、屏蔽词）、功能开关（VIP、注册、同时登录台数、客服/公告）。
- 图片通过 `ImageFilePicker` 调用现有文件上传接口，配置只保存文件 ID；预览使用 App 图片接口。
- OpenAPI admin/app 两个分组按最终接口重新生成 `admin.ts`、`app.ts`。

## 3. 实现取舍

- **令牌撤销用数据库版本**：滑动续期的令牌可以长期存活，若沿用管理员“Redis 版本键 + TTL”，版本键过期后会把撤销后新签发的令牌误判失效；因此普通用户使用 `member_user.session_version`，过滤器每次请求读取账号时一并比较。
- **台数限制串行化**：登录时 `FOR UPDATE` 锁定用户行，先清理 Redis 中已过期的设备会话（标记 EXPIRED），再按登录时间踢出最早的设备（LIMIT）。台数调小不会立即踢人，在下一次登录时生效。
- **业务模块互不依赖**：member/app/video/file 之间经 framework 端口读取；app 模块装配需要 file 与 video 模块提供端口实现（当前 application 全部装配）。
- **启动配置匿名**：开屏、注册开关、客服公告需要在登录前展示，因此 `launch` 与被引用的图片匿名；首页与热搜跟随“未登录不能看”要求登录。
- **普通用户登录不写入管理员登录日志**：审计登录日志页面面向管理员，普通用户的登录留痕由登录设备表承担（设备、IP、登录/活跃/离线时间与原因）。
- **找回密码不暴露邮箱是否注册**：未注册邮箱同样返回成功但不发信；注册场景必须告知“已注册”。
- **热搜第一版只有手动词与屏蔽词**；自动统计搜索词未在 T1 范围。

## 4. 验证记录（2026-10-07，Windows 本机）

区分“自动化测试”“项目运行已验证（真实 MySQL 8.4 + Redis）”。

| 命令（工作目录） | 结果 |
| --- | --- |
| `.\mvnw.cmd -B clean verify`（`backend`，`TMP/TEMP` 指向短路径，见第 5 节） | BUILD SUCCESS；全部 203 个用例，199 通过、4 个按设计跳过（真实探针 2、MinIO 1、源站在线 1）；Checkstyle、中文多行注释（16 例）与 ArchUnit（新增 member、app 模块进入“业务模块互不依赖”规则）通过 |
| 其中 `MemberAppIntegrationTest`（6 例） | 替身发送器邮箱验证码注册、重复发送 429、错误码 400、已注册 409、关闭注册 403；发信未配置 503、找回密码未注册邮箱不发信；非 VIP 403 → 开通后 200 且只有 M3U8 分集、关闭 VIP 开关后登录即可看；台数 2 时踢出最早设备、同设备重登不占名额、后台强制下线；普通用户令牌访问管理接口 401、退出/找回密码/停用后旧令牌失效；首页配置后 launch/home/hot-search/images 返回配置内容，未引用图片 404 |
| 其中 `VideoIntegrationTest`（26 例） | 原匿名读取用例改为断言四个视频 GET 未登录均为 401 |
| `pnpm run type-check`、`pnpm run lint`、`pnpm run test`、`pnpm run build`（`frontend`） | 全部通过；用例 31 个（api-client 12 + 管理端 19）；构建有既有的大分包体积提示 |
| `node scripts/generate-api-types.mjs`（`frontend/packages/api-client`，后端运行中） | 重新生成 admin、app 两个分组类型 |
| 启动 `forge-application-1.0.0.jar`（本机 Docker MySQL 8.4 + Redis） | Flyway 依次执行 4 个新迁移，`flyway_schema_history` 当前版本 20261007121100；未配置 `FORGE_MAIL_HOST` 时日志提示“验证码发信服务未配置”，发送验证码返回 body.code=503 |
| 本机联调脚本：后端配置 `FORGE_MAIL_HOST=127.0.0.1`、端口 2525 指向临时 SMTP 收件替身 | 46 项全部通过：管理员菜单含会员管理与 App 管理；真实 SMTP 会话发出验证码并注册，令牌 `expiresIn=2592000`；两种令牌互访 401；非 VIP 播放线路 403，后台开通 30 天后 200 且分集只有 M3U8/MP4，VIP 记录 1 条；第 3 台登录踢出第 1 台、后台设备列表 2 台在线 1 台 LIMIT、强制下线后令牌 401；上传图片后配置开屏、轮播、两种推荐位、热搜，App 匿名读到开屏与 image/png 图片，登录后读到轮播、两行推荐与排除屏蔽词的热搜；VIP 开关关闭后非 VIP 可看；找回密码后旧令牌失效、新密码可登录 |
| 日志核查 | 应用日志只出现遮蔽邮箱（如 `t***@example.com`），本次发出的 4 个验证码在日志中出现 0 次 |
| 浏览器（管理端开发服务器 + 内置浏览器） | 用户列表、登录设备、首页推荐位、首页轮播、功能开关页面加载真实数据，侧边栏出现“会员管理”，控制台无错误 |

联调数据清理：探针普通用户、设备、VIP 记录、开屏、轮播、推荐位已删除，热搜与客服公告恢复为空，探针图片经文件接口删除。为验证图片上传在本机库新建了本地存储方案（代码 `default`、目录 `uploads`）并设为默认，保留供后续使用。联调期间以 `FORGE_VIDEO_WORKER_ENABLED=false` 启动，未触发采集。

## 5. 环境说明与未完成项

- 本机受限环境下运行带内嵌 Tomcat 的测试或应用时，若 `TMP/TEMP` 路径过长，JDK 的 NIO 管道（Unix 域套接字）会报 `Unable to establish loopback connection`；把 `TMP`、`TEMP` 指向短路径（如 `E:\tmp\jt`）后正常。普通开发终端一般不受影响。
- 验收中 Docker Desktop 曾处于停止状态，联调前重新启动了 Docker Desktop，`orange-forge-mysql-1`、`orange-forge-redis-1` 恢复 healthy，数据保留。
- **发信邮箱尚未提供**：真实外部邮箱（QQ/163/企业邮箱）的 SMTP 未验证；当前只验证了本机 SMTP 收件替身。用户提供后设置 `FORGE_MAIL_HOST`、`FORGE_MAIL_PORT`、`FORGE_MAIL_USERNAME`、`FORGE_MAIL_PASSWORD`（授权码）、`FORGE_MAIL_FROM`、`FORGE_MAIL_SSL` 并实发一封验证。
- 未执行：管理端各新页面的窄屏视觉检查与 Playwright 用例；开屏/轮播图片的对象存储（S3）场景；手机号与第三方登录不在范围。
- 下一步：T2（Flutter 仓库）基于本阶段的 App 接口与 `app.ts` 类型实现电视端；未经用户确认不自动开展。
