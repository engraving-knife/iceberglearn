# 提交 2768：Build: Bump org.apache.avro:avro from 1.12.0 to 1.12.1 (#14369)

## 提交信息

- **序号**：2768 / 4088
- **哈希**：4cea662253f440366e0358d1cadae36004d883b5
- **短哈希**：4cea66225
- **日期**：2025-10-18 22:45:41 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.avro:avro from 1.12.0 to 1.12.1 (#14369)
- **PR/Issue**：#14369

## 总体目的

本提交由 dependabot 自动生成，将 Apache Avro 依赖从 1.12.0 升级到 1.12.1（补丁版本升级）。

背景在于：Iceberg 使用 Apache Avro 作为元数据文件（metadata.json、snapshot、manifest 等）和部分数据文件的序列化格式。Avro 是 Iceberg 的核心依赖之一。dependabot 定期检查 Avro 版本更新。本次是 1.12.x 系列内的补丁升级（1.12.0 → 1.12.1），属于 `version-update:semver-patch`，通常包含 bug 修复和改进，不引入破坏性 API 变化。

Avro 1.12.1 作为 1.12.0 的补丁版本，预期修复了 1.12.0 中发现的问题。由于 Iceberg 大量使用 Avro 进行序列化，升级补丁版本有助于获得稳定性修复。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中将 `avro` 版本引用从 `1.12.0` 改为 `1.12.1`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Apache Avro 版本。

**工作逻辑**：将 `avro = "1.12.0"` 改为 `avro = "1.12.1"`。所有引用 `avro` 版本的库坐标（如 `org.apache.avro:avro`）会随之升级。

## 总结

本提交是 dependabot 自动发起的 Apache Avro 补丁升级（1.12.0 → 1.12.1）。作为 semver-patch 级别升级，预期包含 bug 修复和稳定性改进。由于 Avro 是 Iceberg 元数据序列化的核心依赖，补丁升级有助于提升稳定性。对 API 无破坏性影响，风险较低。
