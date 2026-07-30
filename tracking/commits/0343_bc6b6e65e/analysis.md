# 提交 0343：Build: Bump com.fasterxml.jackson.dataformat:jackson-dataformat-xml (#9395)

## 提交信息

- **序号**：0343
- **哈希**：bc6b6e65ee221fc4c4f7f72d3585b768f7beeecb
- **短哈希**：bc6b6e65e
- **日期**：2024-01-08 16:44:08 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.fasterxml.jackson.dataformat:jackson-dataformat-xml (#9395)
- **PR/Issue**：#9395

## 总体目的

本提交是由 GitHub Dependabot 自动生成的依赖版本升级，把 Jackson 的 XML 数据格式模块 `com.fasterxml.jackson.dataformat:jackson-dataformat-xml` 从 `2.16.0` 升级到 `2.16.1`，属于 `version-update:semver-patch` 类型的更新。Jackson 2.16.x 系列是 2023 年底发布的稳定线，`2.16.1` 是 `2.16.0` 之后的第一个 patch 版本，通常只包含 bug 修复、不引入新 API、不破坏二进制兼容性。Dependabot 推荐此次升级的依据是上游 [FasterXML/jackson-dataformat-xml](https://github.com/FasterXML/jackson-dataformat-xml) 仓库在 `2.16.0` 与 `2.16.1` 之间发布的 commits（PR 描述中附带了 commit range 对比链接），多为修复边界情况下的 XML 解析与序列化问题。

值得注意的细节是：Iceberg 在 `gradle/libs.versions.toml` 中存在多条 Jackson 相关的版本条目，体现了 Jackson 在 Iceberg 中的复杂使用模式：

- `jackson-bom = "2.14.2"`——主 Jackson BOM 版本，通过 `jackson-bom = { module = "com.fasterxml.jackson:jackson-bom", version.ref = "jackson-bom" }` 引入，约束 `jackson-core`、`jackson-databind`、`jackson-annotations` 等核心模块到 `2.14.2`。
- `jackson-annotations = "2.16.0"`——Jackson 注解模块单独指定到 `2.16.0`。
- `jackson-dataformat-xml = "2.16.0"`（本次升级到 `2.16.1`）——XML 数据格式模块，单独指定版本号，不依赖 `jackson-bom`。
- `jackson211 = { strictly = "[2.11, 2.12[", prefer = "2.11.4"}`、`jackson212`、`jackson213`、`jackson214`、`jackson215`——为兼容不同 Spark/Flink 版本而引入的"rich version"约束，用 `strictly` 锁定 Jackson 主版本范围（如 Spark 3.3 需要 Jackson `2.13.x`、Spark 3.5 需要 `2.15.x`），通过 Gradle 的 rich version 机制避免依赖冲突时的版本漂移。

`jackson-dataformat-xml` 在 Iceberg 中主要用于解析 AWS Glue Catalog 与 Hive Metastore 中可能遇到的 XML 格式响应（例如 AWS STS 的某些返回、S3 的 XML 错误响应），以及在测试中序列化/反序列化 XML 文档。它在 `build.gradle` 中以 `testImplementation libs.jackson.dataformat.xml` 形式引入主项目测试作用域，仅用于测试而非运行时依赖，因此 patch 升级的影响面更小。

## 如何达成设计目的

Dependabot 通过修改版本 catalog 文件 `gradle/libs.versions.toml` 中的版本号条目完成升级，机制与所有 Dependabot for Gradle version catalog 的 PR 一致：单文件、单行修改、通过 `version.ref` 自动传播到所有消费方。Jackson 的依赖声明与 `awssdk-bom` 不同点在于：`jackson-dataformat-xml` 没有显式 BOM 约束（`jackson-bom` 锁定的是 `jackson-core`/`jackson-databind`/`jackson-annotations`，不含 `jackson-dataformat-xml`），所以它在 catalog 中是独立版本号条目，每次升级需单独改。CI 合并时会跑相关测试（主要是涉及 XML 解析的测试，如 AWS Glue Catalog 测试、Hive Metastore 集成测试）验证无回归。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Jackson XML 数据格式模块的版本号从 `2.16.0` 升级到 `2.16.1`。

**工作逻辑**：唯一改动是把 `jackson-dataformat-xml = "2.16.0"` 改为 `jackson-dataformat-xml = "2.16.1"`。

文件中相关的 Jackson 版本条目（未修改，仅作上下文说明）：
- `jackson-annotations = "2.16.0"`——Jackson 注解模块，未在本次升级范围内（仍是 `2.16.0`）。
- `jackson-bom = "2.14.2"`——主 Jackson BOM，未在本次升级范围内。
- `jackson-dataformat-xml = "2.16.1"`（本次修改）——XML 格式模块，独立版本号。
- `jackson-dataformat-xml = { module = "com.fasterxml.jackson.dataformat:jackson-dataformat-xml", version.ref = "jackson-dataformat-xml" }`——模块坐标与版本引用。

下游消费方式（不在本提交改动）：
- `build.gradle` 中 `testImplementation libs.jackson.dataformat.xml`——把 Jackson XML 模块引入主项目测试作用域，用于解析 AWS 服务返回的 XML 响应。

## 小结

本提交是 Dependabot 的常规依赖维护操作，单文件单行修改，把 Jackson XML 数据格式模块从 `2.16.0` patch 升级到 `2.16.1`，获取上游的 bug 修复。该模块在 Iceberg 中仅用于测试作用域的 XML 解析，升级影响面小。CI 合并后会跑相关测试验证无回归。
