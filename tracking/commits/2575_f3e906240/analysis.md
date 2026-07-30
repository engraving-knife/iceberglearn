# 提交 2575：REST Spec: Update the max allowed table format version to 3 (#13505)

## 提交信息

- **序号**：2575 / 4088
- **哈希**：f3e906240ed920205b0bfa4625420a09bf0c6c6a
- **短哈希**：f3e906240
- **日期**：2025-08-28 22:59:32 -0600
- **作者**：JB Onofré
- **提交说明**：REST Spec: Update the max allowed table format version to 3 (#13505)
- **PR/Issue**：#13505

## 总体目的

此次提交更新 Iceberg REST Catalog OpenAPI 规范，将 `TableMetadata` 中 `format-version` 字段允许的最大值从 2 提升到 3。Iceberg 社区正在推进 Table Format Version 3（v3 表格式）的开发，v3 引入了新的特性（如新的删除/行级变更语义、新增字段类型等）。REST Catalog 规范作为 Iceberg Catalog 的标准 HTTP API 契约，需要同步允许 v3 格式版本的表元数据通过 API 创建和返回。

此前规范中 `format-version` 的上限为 2（`maximum: 2` / `le=2`），这意味着任何使用 REST Catalog OpenAPI 规范生成客户端或做服务端校验的实现都会拒绝 format-version=3 的表元数据。此次修改将上限放宽到 3，使 REST Catalog 规范与 Iceberg 核心对 v3 格式的支持对齐。

## 如何达成设计目的

- 在 YAML 规范文件中将 `format-version` 的 `maximum` 从 `2` 改为 `3`。
- 在 Python (Pydantic) 规范模型中将 `Field(..., le=2)` 改为 `le=3`。
- 两个字段保持 `minimum: 1`（`ge=1`）不变，即允许 1、2、3 三个版本。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+1/-1)

**修改目的**：YAML 规范中放宽 format-version 上限。

**工作逻辑**：`TableMetadata` 组件的 `format-version` 字段，`minimum: 1` 不变，`maximum` 从 `2` 改为 `3`。

### `open-api/rest-catalog-open-api.py` (+1/-1)

**修改目的**：Python 模型中放宽 format-version 上限。

**工作逻辑**：`TableMetadata` 类的 `format_version` 字段，`Field(..., alias='format-version', ge=1, le=2)` 改为 `le=3`。

## 总结

一次小型规范同步提交，将 REST Catalog OpenAPI 规范中 `TableMetadata.format-version` 的最大允许值从 2 提升到 3，以支持 Iceberg v3 表格式通过 REST API 创建和传输。仅修改 YAML 和 Python 两个规范文件的约束值，无功能代码变更。
