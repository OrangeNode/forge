# 管理端镜像：nginx 托管本地构建的 frontend/admin/dist（部署时复制为同目录 dist），并把 /api 反向代理到后端。
FROM nginx:1.28.0-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
