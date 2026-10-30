# 多阶段构建：第一阶段编译（利用依赖缓存层），第二阶段只保留 JRE 与 jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml ./pom.xml
COPY backend/ ./
# co-bootstrap/pom.xml 里设了 <finalName>campus-overflow</finalName>，产物固定叫
# campus-overflow.jar，不是 Maven 默认的 co-bootstrap-<version>.jar。
#
# 这里显式断言产物存在，是因为踩过一次坑：原来写的是 COPY .../co-bootstrap-*.jar，
# glob 一个都没匹配上时 BuildKit 不报错，而是静默产出一个 32 字节的空层，于是镜像
# 「构建成功」，/app 却是空目录，容器启动即 Error: Unable to access jarfile app.jar。
# 断言把它变成构建期失败，而不是运行期事故。
RUN mvn -B -DskipTests clean package \
 && test -f /src/co-bootstrap/target/campus-overflow.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && apk add --no-cache wget
WORKDIR /app
COPY --from=build /src/co-bootstrap/target/campus-overflow.jar app.jar
# 第二道保险：就算 COPY 又被静默跳过，也让构建在这里失败，不推一个跑不起来的镜像
RUN test -s app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
