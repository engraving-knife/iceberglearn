# 提交 2818：Build: Bump com.google.cloud:libraries-bom from 26.70.0 to 26.71.0 (#14475)

## 提交信息

- **序号**：2818 / 4088
- **哈希**：47a7e20d052700a2a3f204c3421030c9af0a936a
- **短哈希**：47a7e20d0
- **日期**：2025-11-01 23:11:12 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.70.0 to 26.71.0 (#14475)
- **PR/Issue**：#14475

## 总体目的

这是 Dependabot 自动生成的依赖版本升级提交。Google Cloud Libraries BOM（`com.google.cloud:libraries-bom`）是 Google Cloud 客户端库的物料清单，统一管理 Google Cloud 各服务 SDK（如 GCS、BigQuery 等）的版本。Iceberg 使用 GCS 作为支持的存储后端之一，通过该 BOM 管理与 Google Cloud 的集成依赖。

本次从 26.70.0 升至 26.71.0，属于次版本（minor）升级，可能包含 GCS 客户端的功能增强、错误修复和服务端 API 兼容性更新。保持 Google Cloud SDK 最新有助于获得云服务的最新功能支持和稳定性改进。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中的 `google-libraries-bom` 版本属性，从 `26.70.0` 更新为 `26.71.0`。该 BOM 会统一管理所有 Google Cloud 相关依赖的版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：将 `google-libraries-bom = "26.70.0"` 改为 `google-libraries-bom = "26.71.0"`。通过 BOM 机制，所有 Google Cloud SDK 依赖（如 GCS 客户端）会自动对齐到 BOM 指定的版本，确保各 Google Cloud 模块间版本兼容。

## 总结

将 Google Cloud Libraries BOM 从 26.70.0 升级到 26.71.0，属于次版本升级，统一管理 GCS 等 Google Cloud 客户端库版本。这是 Dependabot 批量依赖升级（2811-2819）的一部分，有助于保持云存储集成的稳定性和功能时效性。
