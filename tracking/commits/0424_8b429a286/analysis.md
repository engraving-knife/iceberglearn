# 提交 0424：Build: Bump software.amazon.awssdk:bom from 2.23.2 to 2.23.12 (#9573)

## 提交信息

- **序号**：0424
- **哈希**：8b429a286db27130116c3cdbb9501e2cb0bf3b91
- **短哈希**：8b429a286
- **日期**：2024 年 1 月 30 日（周二）10:01:06 -0800
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.23.2 to 2.23.12 (#9573)
- **PR/Issue**：#9573

## 总体目的

这是一个由 GitHub Dependabot 自动生成的依赖版本升级提交，将 AWS SDK for Java v2 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 `2.23.2` 升级到 `2.23.12`。

AWS SDK BOM 是一个 Maven POM 文件，其核心作用是集中统一管理 AWS SDK 全家桶中所有模块的版本号。Iceberg 在与 AWS 交互（访问 S3、Glue、DynamoDB 等服务）时深度依赖 AWS SDK。通过引入 BOM，项目可以在不逐一指定每个 AWS SDK 子模块版本的前提下，保证它们彼此兼容、版本一致。因此 BOM 版本决定了 Iceberg 运行时使用的全部 AWS SDK 模块版本，是整个 AWS 集成链路的版本基线。

本次升级属于 **semver-patch**（补丁级）变更，依赖类型为 `direct:production`。补丁级升级通常只包含缺陷修复、安全补丁和小幅改进，不涉及 API 破坏性变更，风险较低。从 2.23.2 到 2.23.12 共跨越 10 个补丁版本，属于一次常规的滚动更新，旨在获取最新的缺陷修复与安全增强。

本提交与紧邻的前一个提交 0423（aws-s3-accessgrants 插件主版本升级）在时间上仅相隔 4 分钟，二者共同构成对 AWS 相关依赖的一次集中刷新。值得注意的是，0424 的 diff 上下文中已显示 `awssdk-s3accessgrants = "2.0.0"`，说明本提交在 0423 之后落地，二者顺序明确。

## 如何达成设计目的

Iceberg 采用 Gradle 版本目录（version catalog）集中管理依赖版本，文件位于 `gradle/libs.versions.toml`。本次仅修改 BOM 版本对应的别名变量 `awssdk-bom` 的值，所有通过 BOM 引入的 AWS SDK 模块会自动跟随该基线版本，无需逐模块改动，实现单点升级、全局生效。

## 修改详情

### gradle/libs.versions.toml

**修改目的**：更新版本目录中 AWS SDK BOM 的版本基线声明。

**工作逻辑**：将键 `awssdk-bom` 对应的值由 `"2.23.2"` 改为 `"2.23.12"`。该声明位于依赖版本集中管理区域，紧邻 `avro`、`assertj-core`、`awaitility` 等其它依赖。下游模块在引入 AWS SDK 各子模块时通过引用此 BOM 别名，确保所有 AWS SDK 组件版本与该基线保持一致，避免版本漂移导致的不兼容问题。

## 小结

这是一个典型的低风险依赖补丁升级提交，体现了 Iceberg 项目对 AWS SDK 依赖的持续维护。BOM 作为版本基线，其升级会影响全部 AWS SDK 子模块的运行时版本，但因属补丁级升级，主要带来稳定性与安全性收益。该提交与 0423 共同完成 AWS 相关依赖的集中刷新，是依赖治理日常工作的一部分。
