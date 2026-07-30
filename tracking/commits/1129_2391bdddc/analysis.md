# 提交 1129：open-api: Fix compile warnings for testFixtures (#11071)

## 提交信息

- **序号**：1129 / 4088
- **哈希**：2391bdddca49d568a082988cdecb88c17093b5c0
- **短哈希**：2391bdddc
- **日期**：2024-09-05（Thu Sep 5 21:38:03 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：open-api: Fix compile warnings for testFixtures (#11071)
- **PR/Issue**：#11071

## 总体目的

`iceberg-open-api` 模块下有一个 `testFixtures`（测试夹具）源集，用于为 open-api 相关测试提供可复用的工具类（如内嵌 Jetty 服务器、SQLite JDBC 等，见 build.gradle 中该模块的 `testFixturesImplementation`）。这些测试夹具代码里引用了 JUnit 5 的 API 守卫注解（如 `@API` 来自 `org.apiguardian:apiguardian-api`），但 `testFixtures` 的依赖配置中没有引入 `apiguardian`，导致编译期出现"找不到 `@API` 注解类型"的告警。

本提交给 `iceberg-open-api` 模块的 `testFixtures` 增加 `apiguardian` 的 `compileOnly` 依赖，并把 `apiguardian` 注册到版本目录，从而消除编译告警。这与本批提交 1128（启用更多 error-prone 检查）属于同一波"清理告警以支撑更严格检查"的准备工作。

## 如何达成设计目的

两步：

1. 在 `gradle/libs.versions.toml` 中新增 `apiguardian = "1.1.2"` 版本变量和 `apiguardian = { module = "org.apiguardian:apiguardian-api", version.ref = "apiguardian" }` 库坐标，归入 "test libraries" 段。
2. 在 `build.gradle` 的 `project(':iceberg-open-api')` 块内、`testFixturesImplementation` 依赖之后，追加 `testFixturesCompileOnly libs.apiguardian`。

使用 `compileOnly` 而非 `implementation` 的原因：`@API` 是仅编译期需要的注解（运行时由 JUnit 平台通过反射读取，不需要把 apiguardian 打进测试夹具的运行时 classpath），用 `compileOnly` 既满足编译又避免不必要的运行时依赖传递。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：注册 apiguardian 版本与坐标，供 build.gradle 引用。

**工作逻辑**：

- 在版本变量区（`aircompressor` 之后）新增：
  ```toml
  apiguardian = "1.1.2"
  ```
- 在 "test libraries" 段（`assertj-core` 之前）新增：
  ```toml
  apiguardian = { module = "org.apiguardian:apiguardian-api", version.ref = "apiguardian" }
  ```

### `build.gradle`

**修改目的**：为 `iceberg-open-api` 模块的 testFixtures 源集提供 apiguardian 编译期依赖。

**工作逻辑**：在 `project(':iceberg-open-api')` 的 dependencies 块内，紧跟现有的三条 `testFixturesImplementation`（jetty.servlet、jetty.server、sqlite.jdbc）之后追加：

```groovy
testFixturesCompileOnly libs.apiguardian
```

这样 testFixtures 源集中的测试夹具代码在编译时即可解析到 `org.apiguardian.api.API` 注解，告警消除。

## 小结

- **成效**：消除 `iceberg-open-api` testFixtures 因缺少 `apiguardian-api` 而产生的编译告警，配合后续启用更严格 error-prone 检查（1128）保持构建干净；同时把 apiguardian 纳入版本目录统一管理。
- **影响范围**：`gradle/libs.versions.toml`（+2 行）与 `build.gradle`（+2 行，含空行），仅影响 `iceberg-open-api` 模块 testFixtures 的编译期 classpath，不影响任何运行时行为或发布产物（compileOnly 不传递到运行时）。
- **回迁到 1.4.x 的注意事项**：这是测试夹具的编译期依赖修复，对 1.4.x 发布产物无影响。若 1.4.x 也构建 `iceberg-open-api` 模块且存在相同告警，可 cherry-pick 以保持构建干净；否则**无需回迁**。cherry-pick 时需同时带上 libs.versions.toml 的两处新增，无冲突风险。
