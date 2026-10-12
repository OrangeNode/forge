# 域名与 nginx 配置教程

说明后台域名、App 接口域名是怎么接到服务器上的，以及换域名、关闭旧入口、将来启用 HTTPS 的步骤。部署整体步骤见《[服务器部署教程](服务器部署教程.md)》，入口与安全措施的详细记录见《[部署指南](部署指南.md)》第 1、6 节。

## 1. 现状（2026-10-12 起）

| 域名 | 用途 | 协议 |
| --- | --- | --- |
| `admin.orangenode.cn` | 后台管理页面与后台接口 | HTTP（80） |
| `api.orangenode.cn` | App 接口与安装包下载（App 1.0.8 起使用） | HTTP（80） |

两个域名都解析到 `103.242.13.62`。按用户决定暂不启用 HTTPS（第 6 节说明风险与启用方法）。

另外保留两个高位端口入口，供 1.0.7 及更早的 App 继续使用：后台 `47120`、App `39280`。用 IP 直接访问 80 端口不返回任何内容。

## 2. 请求是怎么走的

~~~text
浏览器 / App
   │  http://admin.orangenode.cn/...   或   http://api.orangenode.cn/api/app/...
   ▼
DNS 解析到 103.242.13.62
   ▼
web 容器（nginx）80 端口，按 Host 分流
   ├─ Host = ADMIN_HOST → 后台站点：管理端页面、/api/admin/、/api/public/
   ├─ Host = API_HOST   → App 站点：/api/app/、/api/public/
   └─ 其他 Host / 直接用 IP → 断开连接（444）
   ▼
backend 容器 8080（只在 Docker 内网可达）
~~~

相关文件（都在仓库 `deploy/web/`）：

| 文件 | 作用 |
| --- | --- |
| `nginx.conf` | 模板：限流、反向代理公共设置，以及 8081、8082、80 三组 `server`。`server_name ${FORGE_ADMIN_HOST}` 这类变量在容器启动时替换 |
| `snippets/admin-site.conf` | 后台站点的具体规则（页面、后台接口、登录限流、上传上限 210 MB） |
| `snippets/app-site.conf` | App 站点的具体规则（App 接口、登录注册限流、下载并发限制） |
| `snippets/security-headers.conf`、`rate-limited.conf` | 安全响应头、限流超限时的统一 JSON 响应 |
| `Dockerfile` | 把 `nginx.conf` 放到 `/etc/nginx/templates/`，官方镜像启动时只替换 `FORGE_` 开头的变量 |

域名值不写在代码里，只在服务器 `/opt/orange-forge/.env`：

~~~ini
ADMIN_HOST=admin.orangenode.cn
API_HOST=api.orangenode.cn
HTTP_BIND=0.0.0.0
HTTP_PORT=80
~~~

`compose.yaml` 把它们作为 `FORGE_ADMIN_HOST`、`FORGE_API_HOST` 传给 web 容器。

## 3. 配置域名解析

在域名服务商（购买 `orangenode.cn` 的平台）的“DNS 解析”里添加两条 A 记录：

| 主机记录 | 记录类型 | 记录值 | TTL |
| --- | --- | --- | --- |
| `admin` | A | `103.242.13.62` | 600 |
| `api` | A | `103.242.13.62` | 600 |

检查是否生效（本机 PowerShell）：

~~~powershell
nslookup admin.orangenode.cn
nslookup api.orangenode.cn     # 都应返回 103.242.13.62
~~~

服务器在中国大陆时，用域名访问 80 端口通常需要先完成 ICP 备案，否则会被服务商拦截；境外服务器不需要。

## 4. 让服务器认这两个域名

1. 编辑服务器 `.env`，填 `ADMIN_HOST`、`API_HOST`（见第 2 节）。
2. 确认 80 端口已放行：`ufw allow 80/tcp`，云厂商安全组也放行 80。
3. 重建 web 容器（已验证）：

~~~bash
cd /opt/orange-forge
docker compose up -d --build --wait web
~~~

4. 验证：

~~~bash
# 服务器上，用 Host 头模拟（不依赖 DNS）
curl -s -o /dev/null -w '%{http_code}\n' -H 'Host: admin.orangenode.cn' http://127.0.0.1/          # 200
curl -s -H 'Host: api.orangenode.cn' http://127.0.0.1/api/app/v1/app/launch | head -c 120             # "code":0
curl -s -o /dev/null -w '%{http_code}\n' -H 'Host: api.orangenode.cn' http://127.0.0.1/               # 404（App 域名没有后台页面）
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/                                             # 000（直接用 IP 被断开）
~~~

本机浏览器打开 `http://admin.orangenode.cn/` 能看到登录页即完成。2026-10-12 从本机经公网核对过同样四项：后台域名 200、App 域名 `launch` 返回 `code=0`、App 域名根路径 404、直接用 IP 访问空响应。

## 5. 换域名或关闭旧入口

### 5.1 换成新域名

1. 新域名按第 3 节添加 A 记录。
2. 改服务器 `.env` 的 `ADMIN_HOST`、`API_HOST`，`docker compose up -d --build --wait web`。
3. App 接口域名变了时：修改 orange-app 仓库 `config/test.json` 的 `API_BASE_URL`，提高版本号重新打包，在后台发布（建议强制更新）。见 orange-app《APK 打包签名与发布教程》。
4. **旧版 App 只认旧地址**。在大多数用户更新之前，保留旧域名解析和旧入口；否则旧 App 连不上服务器，也就收不到“请更新”的提示。

一个 `server_name` 想同时认新旧两个域名时，可以在 `.env` 写成空格分隔（例如 `API_HOST=api.orangenode.cn api.new-domain.com`），nginx 支持一个 `server_name` 多个名字（未实际执行）。

### 5.2 关闭高位端口入口

1.0.8 起 App 用域名，等后台“版本更新”确认大多数用户已到 1.0.8 后，可以关闭 39280、47120（未实际执行）：

1. 编辑 `deploy/compose.yaml` 的 `web.ports`，删除 `ADMIN_PORT`、`APP_PORT` 两行（或改成 `127.0.0.1:${ADMIN_PORT}:8081` 只在服务器本机可用），上传到服务器。
2. `docker compose up -d web`。
3. 删除对应 ufw 规则与云厂商安全组规则：`ufw delete allow 39280/tcp`、`ufw delete allow 47120/tcp`。

提醒：只删 ufw 规则关不掉 Docker 发布的端口，必须改 `compose.yaml` 的端口绑定。

### 5.3 关闭域名入口

在 `.env` 设 `HTTP_BIND=127.0.0.1` 后 `docker compose up -d web`，80 端口只在服务器本机可用。

## 6. 将来启用 HTTPS（未实际执行）

现在全部是明文 HTTP：后台口令、登录令牌和 App 接口内容在网络上可以被截获。建议在正式运营前启用 HTTPS。大致步骤：

1. 云厂商安全组与 ufw 放行 443：`ufw allow 443/tcp`。
2. 申请证书。推荐 Let's Encrypt 免费证书（90 天有效，可自动续期），用 DNS 验证方式不需要改动正在运行的 nginx：
   - 服务器安装 `certbot` 或 `acme.sh`，用域名服务商的 API 密钥做 DNS 验证，为 `admin.orangenode.cn`、`api.orangenode.cn` 签发证书。
   - 也可以在域名服务商或云厂商控制台申请免费证书，下载 nginx 格式（`.pem` + `.key`）。
3. 证书放到服务器 `/opt/orange-forge/certs/`（`chmod 600`，不进仓库），在 `compose.yaml` 的 web 服务增加 `443:443` 端口和只读挂载 `./certs:/etc/nginx/certs:ro`。
4. 在 `nginx.conf` 为两个域名各加一个 `listen 443 ssl;` 的 `server`，`include` 同一个站点片段，并指定 `ssl_certificate`、`ssl_certificate_key`；80 端口的两个域名 `server` 改为 `return 301 https://$host$request_uri;`。
5. 后台没问题后，App 的 `config/test.json` 改为 `https://api.orangenode.cn`，发新版本；并收紧 orange-app 的 `android/app/src/main/res/xml/network_security_config.xml`（片源地址大量是 http，收紧前要确认播放不受影响）。
6. 旧版 App 仍走 http，80 端口的 API 域名在一段时间内不要强制跳转到 https（App 的网络库不一定跟随跳转），等用户普遍更新后再改。
7. 证书续期后需要 `docker compose exec web nginx -s reload` 让 nginx 读到新证书，可以写进续期工具的完成钩子。

做这件事会改公共入口，动手前先在仓库里改好配置、本机验证，再按《服务器部署教程》第 6 节上传。

## 7. 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 浏览器打开域名一直转圈或“无法访问” | DNS 未生效（`nslookup` 检查）、云安全组没放行 80，或服务商因未备案拦截 |
| 打开域名显示“连接被重置” / 空响应 | 请求的 Host 不在 `ADMIN_HOST`、`API_HOST` 里（拼写错误、多了 `www`），被 444 断开。检查 `.env` 后重建 web |
| 后台页面能打开但登录报网络错误 | 用 App 域名打开了后台（App 域名没有 `/api/admin/`），改用后台域名 |
| App 提示“网络异常” | App 打包的地址与服务器不一致，或 API 域名解析失效；用 `curl http://api.orangenode.cn/api/app/v1/app/launch` 检查 |
| 改了 `.env` 不生效 | 需要 `docker compose up -d web` 重建容器，`restart` 不会读新的环境变量 |
| 改了 `nginx.conf` 不生效 | 配置在镜像里，需要上传后 `docker compose up -d --build web` |
| 返回 `"code":429` | 触发限流（接口每秒 20 次、登录注册每分钟 10 次），稍后再试 |
