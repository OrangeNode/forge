# A9-App管理合并与电视轮播图

2026-10-11 用户要求：后台“App 管理”子菜单太多，合并成少数几个页面用标签页分组，功能与数据不变；电视端首页轮播图要能单独配置（手机与电视尺寸不同）；公告 HTML 提供几套可视化样例，带预览和“使用此模板”。App 界面在 `orange-app` 仓库，本文件记录后端、管理端、App 与验证结果。

## 1. 改动

| 项 | 实现 |
| --- | --- |
| 菜单合并 | 9 个页面合成 4 个入口：首页与开屏（开屏设置、首页轮播、首页推荐位、热搜设置）、公告与提示（公告管理、播放页滚动文字）、功能与版本（功能开关、版本更新）、反馈处理。合并只在前端：`menu-routes.ts` 的 `MENU_GROUPS` 把同组菜单标识渲染成一个侧边栏入口，`AppGroupView.vue` 用标签页切换原页面，当前标签记在地址参数 `tab`。后端菜单、权限与角色授权不变，角色管理里仍按原页面勾选；没有授权的页面在合并页里不显示对应标签 |
| 旧地址 | `app/splash`、`app/banners`、`app/sections`、`app/hot-search`、`app/announcements`、`app/marquee`、`app/features`、`app/release` 重定向到合并页对应标签；顶部标签切回合并页时回到上次停留的子标签 |
| 数据 | 迁移 `V20261011120000`：`app_banner` 增加可空 `tv_image_file_id`；调整 App 管理下菜单排序，使合并后入口依次为首页与开屏、公告与提示、功能与版本、反馈处理 |
| 电视轮播图 | 后台轮播请求与响应增加 `tvImageFileId`（响应另有 `tvImageUrl`）；App 首页 `banners[].tvImageUrl` 为电视图，没有单独配置时返回手机图地址。图片公开读取接口同时接受被电视图引用的文件 |
| 管理端轮播 | 表单并排上传手机图（建议 1280×600）与电视图（建议 1200×540，可不传），可清除电视图；列表增加“电视图片”列，未配置时显示“同手机图片” |
| 公告样例 | `announcement-templates.ts` 内置 6 套：版本更新、节日祝福、维护通知、新片上线、活动福利、防骗提醒。只用白名单内且 App 渲染器支持的标签（标题、段落、加粗、小字、列表、引用、分隔线、font 颜色），颜色取 App 深色弹窗上清晰的值。表单“从样例模板选择”打开预览网格，“使用此模板”把内容切为 HTML 并填入（已有内容时先确认），标题为空时一并填入；编辑预览改为与 App 弹窗一致的深色配色 |
| App | 1.0.6+7：`HomeBanner` 增加可空 `tvImageUrl`，电视首页轮播优先用它，旧服务端不返回时回退手机图 |

## 2. 验证记录（2026-10-11，Windows 本机）

| 命令或操作 | 结果 |
| --- | --- |
| `backend`：`.\mvnw.cmd -B clean verify "-Dsurefire.rerunFailingTestsCount=2"`（`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=E:\tmp\jdk-uds`，见下方说明） | BUILD SUCCESS，forge-application 185 个用例（3 个按设计跳过），Checkstyle、中文注释与模块规则通过 |
| `MemberAppIntegrationTest`、`MemberPersonalIntegrationTest` | 通过；首页用例新增：轮播配置电视图时返回电视图地址、未配置时回退手机图、电视图可经公开图片接口读取 |
| `frontend`：`pnpm run type-check`、`pnpm run lint`、`admin` 下 `pnpm run test` 与 `pnpm run build` | 通过（22 个用例，新增合并入口、标签参数与菜单标识收集），保留既有产物体积警告 |
| OpenAPI 类型 | 本机 Docker 未运行，无法启动后端导出；按后端新增字段更新本地规范文件后用同版本 `openapi-typescript` 离线生成，`admin.ts`、`app.ts` 只多出 `tvImageFileId`、`tvImageUrl` 三处字段 |
| 公告样例 | 用与预览相同的深色样式在浏览器中渲染 6 套样例并截图检查 |
| `orange-app`：`flutter analyze`、`flutter test` | 无问题；38 个用例通过 |

说明：本机会话的 `TEMP` 为 8.3 短路径时，JDK 的 Unix 域套接字建不起来，Tomcat 报 `Unable to establish loopback connection`，内嵌服务的集成测试全部启动失败；把 `jdk.net.unixdomain.tmpdir` 指向普通目录即可。

未执行：

- 管理端页面未在浏览器登录操作（登录需要图形验证码）。
- App 未在电视模拟器上连后端看电视轮播图实际效果。

## 3. 部署（2026-10-11）

| 操作 | 结果 |
| --- | --- |
| 部署测试服务器（HEAD `d5f80d8`，用户确认后上传 `app.jar` 与 `web/dist`，旧包留作 `app.jar.prev`、`dist.prev`；`docker compose up -d --build --wait backend web`，未改 compose.yaml、nginx 配置与 .env） | 四个容器健康；Flyway 执行 `V20261011120000`，`app_banner.tv_image_file_id` 已存在，App 管理菜单排序为 1—9；39280 上 `launch` 返回 code=0、`home` 未登录返回 code=401；47120 后台页面可打开 |
| App 1.0.6(7) 正式签名包（`config/test.json`，SHA-256 `AC827093EF8B83275792136B37983959EAD507095C0A7F932B3FF747B9C1C209`） | 用户登录后台并选择安装包后，以其会话在合并后的“功能与版本 → 版本更新”上传发布，强制更新（最低可用版本 7）；启动配置返回版本 7；经 39280 公开外链完整下载，SHA-256 一致。打包时 Gradle 同样需要 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=E:\tmp\jdk-uds` |

## 4. 更正：电视开屏图（2026-10-11）

用户说明原意是“电视的首页开屏图”单独配置，此前误做成了首页轮播电视图。轮播电视图保留（可不传，不影响现有轮播），另按同样方式补做开屏：

| 项 | 实现 |
| --- | --- |
| 数据 | 迁移 `V20261011150000`：`app_splash` 增加可空 `tv_image_file_id` |
| 接口 | 后台开屏请求与响应增加 `tvImageFileId`（响应另有 `tvImageUrl`）；`/api/app/v1/app/launch` 的 `splash.tvImageUrl` 为电视封面图，未配置时返回手机封面图地址；公开图片接口同时接受被电视封面图引用的文件 |
| 管理端 | 开屏表单并排上传手机封面图（竖版，建议 1080×1920）与电视封面图（横版，建议 1920×1080，可不传、可清除）；列表增加“电视封面”列 |
| App | 1.0.7+8：`SplashConfig` 增加可空 `tvImageUrl`，电视开屏预加载与显示都优先用它，旧服务端不返回时回退手机图 |

验证（本机）：`backend` `clean verify` BUILD SUCCESS（185 个用例，3 个按设计跳过；首页用例新增启动配置返回电视封面图地址、电视封面图可经公开图片接口读取）；`frontend` type-check、lint、22 个用例与 `admin` 构建通过；OpenAPI 类型按同样方式离线生成，只多出 4 处字段；`orange-app` `flutter analyze` 无问题、38 个用例通过。

部署与发布（2026-10-11）：

| 操作 | 结果 |
| --- | --- |
| 部署测试服务器（HEAD `afda363`，同前流程，旧包留作 `.prev`） | 四个容器健康；Flyway 执行 `V20261011150000`；启动配置 `splash.tvImageUrl` 未配置时回退手机图 |
| 正式开屏图 | 按用户要求在原测试开屏样式基础上重新生成，去掉“测试广告”字样：手机 1080×1920、电视横版 1920×1080（浏览器渲染 HTML 后截图）。用户在后台选图保存，开屏名称改为“正式开屏”；线上图片 28、29 与生成文件 SHA-256 一致 |
| 发布 1.0.7(8)（SHA-256 `548941BF7CBE67CA11166D2F25C71F2057A4844F0C750763CB67033654042215`） | 用户选择安装包后以其会话发布，强制更新（最低可用版本 8）；启动配置返回版本 8；公开外链完整下载 SHA-256 一致 |

未执行：未在电视上看到横版开屏的实际效果。

## 域名入口（2026-10-12）

用户把泛域名解析到测试服务器，要求只用 HTTP：`admin.orangenode.cn` 指向后台，`api.orangenode.cn` 指向 App 接口。

| 项 | 结果 |
| --- | --- |
| nginx | 80 端口按 Host 分流（`nginx.conf` 改为模板，域名来自 `.env` 的 `ADMIN_HOST`、`API_HOST`）；后台与 App 两类 location 抽到 `snippets/admin-site.conf`、`snippets/app-site.conf`，高位端口与域名入口共用；其他 Host 返回 444。本机 docker 验证各 Host 分流结果 |
| 部署（HEAD `ddcb1f1`） | 服务器配置备份到 `/opt/orange-forge/backup-202610120043/`；`.env` 删除 `LEGACY_HTTP_*`，增加两个域名与 `HTTP_BIND=0.0.0.0`；`ufw allow 80/tcp`；只重建 web 容器 |
| 公网验证 | `http://admin.orangenode.cn/` 200；`http://api.orangenode.cn/api/app/v1/app/launch` code=0，安装包外链经该域名分段下载 206；后台域名不提供 App 接口（404）；直接访问 IP 的 80 端口无响应；旧地址 `:47120`、`:39280` 仍为 200 |
| App 1.0.8(9) | `config/test.json` 改为 `http://api.orangenode.cn`（Android 已允许明文 HTTP）；正式签名 release 包 SHA-256 `2EF487BEA732E2E0150EC846B503EEE055AF1ED2501BD31626C9EFA856F7A2E3`，包内不含旧端口 |

未执行：1.0.8 尚未在后台发布（需用户登录后台选择安装包）；未在真机上验证 1.0.8。
