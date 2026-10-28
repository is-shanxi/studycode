# Java 业务后端

Java 21 + Spring Boot 3 模块化单体，是 PostgreSQL 中业务数据的唯一写入方。身份、题库、练习、社区、通知、运营与 AI 网关将在后续阶段按域接入。

## 命令

```powershell
./mvnw test
./mvnw verify   # 包含 Checkstyle
./mvnw spring-boot:run
```

本地数据库连接与 Flyway 配置在 P1 基础阶段（Phase 2）接入。
