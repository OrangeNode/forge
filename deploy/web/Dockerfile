# 管理端镜像：nginx 托管本地构建的 frontend/admin/dist（部署时复制为同目录 dist），并把接口反向代理到后端。
# 三个入口（后台、App、旧 80 端口）见 nginx.conf。
FROM nginx:1.28.0-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY snippets/ /etc/nginx/snippets/
COPY dist/ /usr/share/nginx/html/
