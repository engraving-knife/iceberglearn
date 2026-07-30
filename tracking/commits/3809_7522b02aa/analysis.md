# 提交 3809：Build: Bump software.amazon.awssdk:bom from 2.44.7 to 2.44.12 (#16634)

## 提交信息

- **序号**：3809 / 4088
- **哈希**：7522b02aaf42d51d4b12e211119d159c9dc03552
- **短哈希**：7522b02aa
- **日期**：2026-05-31 08:42:28 -0700
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.44.7 to 2.44.12 (#16634)
- **PR/Issue**：#16634

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目依赖的 AWS SDK for Java BOM（`software.amazon.awssdk:bom`）从 `2.44.7` 升级到 `2.44.12`。AWS SDK BOM 是一个依赖管理清单，统一管理 AWS SDK 各模块（S3、DynamoDB、Glue、STS 等）的版本，Iceberg 的 AWS 集成模块（如 `aws-bundle`、S3 catalog、Glue catalog 等）依赖它来访问 AWS 服务。这是一个 patch 级升级（2.44.7 → 2.44.12），跨越 5 个 patch 版本，通常包含多个 bug 修复和小改进，属于常规依赖维护，有助于及时获取 AWS SDK 的修复与安全补丁。

## 如何达成设计目的

Dependabot 检测到 `gradle/libs.versions.toml` 中 `awssdk-bom` 版本有新发布，自动创建 PR 升级版本字符串。由于使用 BOM 管理版本，仅需修改一处即可让所有 AWS SDK 模块版本同步更新。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：
将版本目录中的版本声明从 `2.44.7` 改为 `2.44.12`：
```toml
-awssdk-bom = "2.44.7"
+awssdk-bom = "2.44.12"
```
所有通过 BOM 引入的 AWS SDK 模块会自动获得新版本。

## 总结

这是一次常规的 patch 级依赖升级，由 Dependabot 自动完成。AWS SDK 是 Iceberg AWS 集成的核心依赖，及时升级有助于获取 bug 修复与安全补丁，保持与 AWS 服务的兼容性。由于是 BOM 管理，改动仅一行，影响范围可控。
