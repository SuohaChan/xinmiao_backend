# 运行镜像：使用已编译好的 fat jar（本机或 CI 先执行 mvn package）
# 构建：mvn -f pom.xml -DskipTests package && docker compose --env-file env/prod.env build backend
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ARG JAR_FILE=xinmiao-server/target/xinmiao-server-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 9090
ENV JAVA_TOOL_OPTIONS=""
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
