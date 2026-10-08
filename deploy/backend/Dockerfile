# 后端运行镜像：只放入本地构建好的可执行 jar（backend/forge-application/target/forge-application-1.0.0.jar，
# 部署时复制为同目录 app.jar），不在镜像内编译。
FROM eclipse-temurin:21.0.8_9-jre-noble

RUN groupadd --system forge && useradd --system --gid forge --home-dir /app forge \
    && mkdir -p /app /data && chown forge:forge /app /data

WORKDIR /app
COPY --chown=forge:forge app.jar /app/app.jar

USER forge
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
