# 提交 0058：Build: Bump com.fasterxml.jackson.core:jackson-annotations (#8836)

## 提交信息

- **序号**：0058 / 4088
- **哈希**：67667901687c12a477438494e821422fedd9920d
- **短哈希**：676679016
- **日期**：2023-10-16 08:33:33 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.fasterxml.jackson.core:jackson-annotations (#8836)
- **PR/Issue**：#8836

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，将 `com.fasterxml.jackson.core:jackson-annotations` 从 2.15.2 升级到 2.15.3。Jackson 是 Java 生态中最主流的 JSON 序列化/反序列化库，`jackson-annotations` 制品提供 `@JsonProperty`、`@JsonIgnore`、`@JsonCreator` 等注解，Iceberg 在多个模块中用它来标注 REST catalog 请求/响应模型、表元数据序列化等场景的 JSON 字段映射。

本次升级属于 semver-patch（补丁号）升级（2.15.2 → 2.15.3），是本批 5 个提交中风险最低的一类。Jackson 2.15.x 系列内的补丁升级通常只包含 bug 修复与小改进，不引入 API 不兼容变更，因此对 Iceberg 现有代码几乎无影响。

需要注意的一点背景：Iceberg 的版本目录中存在多个 Jackson 版本变量（`jackson-annotations`、`jackson-bom`、`jackson-dataformat-xml`，以及为 Spark/Flink 兼容而固定下来的 `jackson211`/`jackson212`/`jackson213`/`jackson214`/`jackson215` 等 rich version）。本次升级只动了主用的 `jackson-annotations` 变量（2.15.2 → 2.15.3），并不影响为 Spark/Flink 集成而锁定的那些兼容版本。这种"主用版本与引擎兼容版本分离管理"的设计，使得 Iceberg 可以独立跟进 Jackson 主线修复而不破坏与 Spark/Flink 自带 Jackson 的二进制兼容。

## 如何达成设计目的

Dependabot 通过 Gradle 集中式版本目录完成升级。在 [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml) 中，`jackson-annotations` 版本变量被同名库声明 `com.fasterxml.jackson.core:jackson-annotations` 通过 `version.ref = "jackson-annotations"` 引用，因此只需修改这一行版本号即可让该制品升级，无需改动任何源代码。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `jackson-annotations` 版本变量从 2.15.2 提升到 2.15.3，使主用的 jackson-annotations 制品获得该补丁版本的修复。

**工作逻辑**：在 `[versions]` 段中，将 `jackson-annotations = "2.15.2"` 改为 `jackson-annotations = "2.15.3"`。由于这是同一 minor 系列内的 patch 升级，且仅影响注解制品（不涉及 `jackson-databind`/`jackson-core` 的运行时行为），回归风险极低；CI 中涉及 REST catalog 与 JSON 序列化的测试足以覆盖。值得指出的是，`jackson-bom`（2.14.2）与 `jackson215`（rich version 锁定 2.15.x）等变量并未在本次提交中改动，体现了 Iceberg 对 Jackson 多版本并存管理的克制与精确。

## 小结

通过一行版本目录改动，将 Iceberg 主用的 jackson-annotations 从 2.15.2 升级到 2.15.3，是低风险的常规补丁维护，且未触碰为 Spark/Flink 兼容而锁定的其它 Jackson 版本变量。
