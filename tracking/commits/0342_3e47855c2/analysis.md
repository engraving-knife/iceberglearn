# 提交 0342：Build: Bump software.amazon.awssdk:bom from 2.21.42 to 2.22.12 (#9426)

## 提交信息

- **序号**：0342
- **哈希**：3e47855c2a7bccdaff9e9b25056621d03f6110a9
- **短哈希**：3e47855c2
- **日期**：2024-01-08 16:39:21 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.21.42 to 2.22.12 (#9426)
- **PR/Issue**：#9426

## 总体目的

本提交是由 GitHub Dependabot 自动生成的依赖版本升级，把 AWS SDK for Java 2.x 的 BOM（Bill of Materials）从 `2.21.42` 升到 `2.22.12`，跨越约 11 个 minor 版本（`2.21.x` → `2.22.x`），属于 `version-update:semver-minor` 类型的更新。Iceberg 在 AWS 集成模块（`aws/`、`aws-bundle/`）以及主项目的 `compileOnly`/`testImplementation` 作用域中通过 `platform(libs.awssdk.bom)` 引入该 BOM，BOM 本身不直接引入依赖，而是统一约束所有 `software.amazon.awssdk:*` 子模块（如 `s3`、`sts`、`glue`、`kms`、`dynamodb`、`core` 等）的版本号，确保 Iceberg 在使用 AWS SDK 各子模块时使用同一套互相兼容的版本。Dependabot 的自动化 minor 升级策略的目的是：(1) 让项目持续获得 AWS SDK 的功能增强与 bug 修复；(2) 避免版本号长期落后积累出大跨度的"陈年升级债"；(3) 通过频繁小步升级及时发现兼容性问题。

`2.22.x` 系列是 AWS SDK 在 2023 年底到 2024 年初的稳定发布线，主要包含若干 S3 客户端的 bug 修复、HTTP 客户端（Apache HttpClient、Netty NioAsyncHttpClient）稳定性改进、STS/S3 Control 等服务的 API 模型更新，以及与 `s3-accessgrants` 插件（Iceberg 在 `2.22.x` 引入的新依赖，版本固定为 `1.0.1`）的协同调整。这类 minor 升级通常对调用方代码透明，因为 AWS SDK 2.x 严格遵守 SemVer——在 minor 升级中只增加新 API、不修改既有 API 签名；但 Iceberg 在合并时仍需运行完整 CI（特别是 `aws/`、`aws-bundle/` 子项目的 S3Mock 测试）验证无回归。

## 如何达成设计目的

Dependabot 的实现方式是修改单一版本 catalog 文件 `gradle/libs.versions.toml` 中的版本号条目 `awssdk-bom = "2.21.42"` → `awssdk-bom = "2.22.12"`。Gradle 的 version catalog 机制会自动把该版本号通过 `version.ref = "awssdk-bom"` 传播到 BOM 模块定义 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }`，再通过 `build.gradle` 中的 `platform(libs.awssdk.bom)` / `implementation platform(libs.awssdk.bom)` 等声明传播到具体子模块。这种"单一来源"（single source of truth）的版本管理模式是 Iceberg 选择 Gradle version catalog 的关键收益之一——任何 `software.amazon.awssdk:*` 子模块的版本变更只需改一行版本号，所有依赖处自动同步。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 AWS SDK BOM 的版本号从 `2.21.42` 升级到 `2.22.12`。

**工作逻辑**：唯一改动是第 30 行（在 `awssdk-bom = "2.21.42"` 处），把双引号内的版本字符串改为 `"2.22.12"`。版本号格式为 `MAJOR.MINOR.PATCH`，本次升级属于 minor 跨越（`2.21` → `2.22`），共跨越约 11 个 patch 版本（`2.21.42` → `2.21.<highest>` → `2.22.0` → `2.22.12`）。

文件中相关的几条关键声明（未修改、仅作上下文说明）：
- `awssdk-bom = "2.22.12"`（本次修改）——版本号字面量。
- `awssdk-s3accessgrants = "1.0.1"`——AWS S3 Access Grants 插件的版本，固定为 `1.0.1`，与 `awssdk-bom` 解耦，不在本次升级范围内。
- `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }`——BOM 模块坐标与版本引用，通过 `version.ref` 关联到上面的版本号字面量。
- `awssdk-s3accessgrants = { module = "software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin", version.ref = "awssdk-s3accessgrants" }`——S3 Access Grants 插件坐标。

下游消费方式（不在本提交改动，但说明版本传播路径）：
- `build.gradle` 中 `compileOnly(platform(libs.awssdk.bom))` 与 `testImplementation(platform(libs.awssdk.bom))`——把 BOM 引入主项目的编译与测试作用域。
- `aws-bundle/build.gradle` 中 `implementation platform(libs.awssdk.bom)`——在 AWS bundle 模块中把 BOM 作为运行时依赖。
- 各 `aws/`、`aws-bundle/` 子项目下的 `implementation "software.amazon.awssdk:s3"` / `"software.amazon.awssdk:sts"` / `"software.amazon.awssdk:glue"` 等声明省略版本号，由 BOM 自动解析为 `2.22.12`。

## 小结

本提交是 Dependabot 的常规依赖维护操作，单文件单行修改，目的是让 AWS SDK BOM 保持在与上游同步的较新版本，获取功能改进与 bug 修复。修改通过 Gradle version catalog 的"单一来源"机制自动传播到所有 AWS SDK 子模块的依赖声明，无需逐个修改 `build.gradle`。CI 在合并后会跑 `aws/`、`aws-bundle/` 子项目的 S3Mock 集成测试以验证无回归。
