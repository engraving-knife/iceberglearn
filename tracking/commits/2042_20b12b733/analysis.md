# 提交 2042：Build: Bump testcontainers from 1.20.6 to 1.21.0

## 提交信息

- **序号**：2042 / 4088
- **哈希**：20b12b7339628b093c099761d994bf4dbc47d10d
- **短哈希**：20b12b733
- **日期**：2025-04-28 07:55:18 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump testcontainers from 1.20.6 to 1.21.0 (#12904)
- **PR/Issue**：#12904

## 总体目的

本提交由 Dependabot 自动生成，将 Testcontainers 依赖从 1.20.6 升级到 1.21.0。Testcontainers 是 Iceberg 集成测试中使用的重要框架，提供 Docker 容器化的测试环境（如启动数据库、消息队列等进行集成测试）。此次升级为次版本升级（semver-minor），可能包含新功能和改进。

## 如何达成设计目的

通过修改 Gradle 版本目录文件中的 Testcontainers 版本号，自动更新所有引用该版本变量的 Testcontainers 依赖。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 Testcontainers 版本号。

**工作逻辑**：
将 `testcontainers = "1.20.6"` 改为 `testcontainers = "1.21.0"`。此版本变量被以下 3 个 Testcontainers 依赖引用，均自动升级：
- `org.testcontainers:testcontainers`
- `org.testcontainers:junit-jupiter`
- `org.testcontainers:minio`

## 总结

Dependabot 自动依赖升级提交，将 Testcontainers 从 1.20.6 升级到 1.21.0（次版本升级），涉及 3 个 Testcontainers 相关依赖。改动仅 1 行版本号变更。
