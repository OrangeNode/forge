# 管理端镜像：nginx 托管本地构建的 frontend/admin/dist（部署时复制为同目录 dist），并把接口反向代理到后端。
# 入口（后台、App 高位端口与 80 端口域名分流）见 nginx.conf。nginx.conf 是模板，
# 官方镜像启动时把 /etc/nginx/templates/*.template 替换环境变量后写入 conf.d；只替换 FORGE_ 开头的变量。
FROM nginx:1.28.0-alpine

ENV NGINX_ENVSUBST_FILTER=^FORGE_

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY snippets/ /etc/nginx/snippets/
COPY dist/ /usr/share/nginx/html/
