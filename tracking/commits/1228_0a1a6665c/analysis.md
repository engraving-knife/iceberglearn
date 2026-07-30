# 提交 1228：Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#9705)

## 提交信息

- **序号**：1228 / 4088
- **哈希**：0a1a6665c172a1a6104bfdddcdc28eb10137c5a2
- **短哈希**：0a1a6665c
- **日期**：2024-10-12（Sat Oct 12 21:10:14 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#9705)
- **PR/Issue**：#9705

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级提交。AWS S3 Access Grants 是 AWS 提供的一种 S3 访问控制机制，允许通过 IAM 身份中心管理对 S3 数据的细粒度访问权限。`aws-s3-accessgrants-java-plugin` 是该机制的 Java 客户端插件，用于在 AWS SDK v2 的 S3 客户端中集成 Access Grants 功能。Iceberg 在 AWS 模块中引入此插件，以支持使用 S3 Access Grants 进行访问控制的场景。

本次将 `awssdk-s3accessgrants` 从 2.0.0 升级到 2.2.0，属于次版本（semver-minor）升级。

**注意**：提交消息正文中 Dependabot 声称升级"from 2.0.0 to 2.0.1"，但实际代码改动是将版本从 `2.0.0` 改为 `2.2.0`。这是因为 Dependabot 在 PR 创建时生成的描述可能基于较早的版本检查结果，而在 PR 合并前版本已更新到 2.2.0，描述未同步更新。以实际代码改动为准。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中 `awssdk-s3accessgrants` 的版本声明，从 `"2.0.0"` 改为 `"2.2.0"`。通过 Gradle 版本目录的 `version.ref` 机制，引用该版本号的库会自动同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 awssdk-s3accessgrants 版本号。

**工作逻辑**：将第 34 行的版本声明从：

```toml
awssdk-s3accessgrants = "2.0.0"
```

改为：

```toml
awssdk-s3accessgrants = "2.2.0"
```

该版本变量被 `libs.versions.toml` 中 `software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin` 库条目通过 `version.ref` 引用。此插件作为 S3 客户端的插件（plugin），在 Iceberg 的 AWS 集成模块中用于支持 S3 Access Grants 认证方式。升级后，使用 S3 Access Grants 的用户将获得 2.2.0 版本的插件功能改进。

2.0.0 → 2.2.0 跨越两个次版本，可能包含：Access Grants 凭证缓存改进、与新版 AWS SDK 的兼容性修复、新配置选项等。

## 小结

- **成效**：awssdk-s3accessgrants 从 2.0.0 升级到 2.2.0，获取两个次版本的功能改进和 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动，无代码逻辑变更。此依赖仅影响使用 S3 Access Grants 功能的用户。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前使用的 awssdk-s3accessgrants 版本为 2.0.0（与升级前一致）。此升级属于次版本更新，且该插件是可选的 S3 认证方式，影响面有限。回迁风险较低，但需注意：2.2.0 版本插件可能与特定版本的 AWS SDK v2 存在兼容性要求，回迁时需确认 1.4.x 的 awssdk-bom 版本是否满足该插件的最低要求。若 1.4.x 不计划支持 S3 Access Grants 功能，可暂不回迁。
