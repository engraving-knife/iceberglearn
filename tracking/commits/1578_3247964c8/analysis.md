# 提交 1578 3247964c8 分析

## 提交信息
- 哈希：3247964c88dbc95e1bbf4da8c39f9a5d9cf950fa
- 日期：2025-01-13（Mon Jan 13 22:00:02 2025 +0530）
- 作者：S N Munendra <9696252+munendrasn@users.noreply.github.com>
- 消息：Spec: Add cross-region bucket access property to config (#11260)

## 总体目的

本提交向 Iceberg REST Catalog OpenAPI 规范中追加一项新的 S3 配置属性 `s3.cross-region-access-enabled`，用于声明是否启用 S3 跨区域（Cross-Region）bucket 访问。

Iceberg REST Catalog 在返回 `LoadTableResult` 时，会附带一组 `config` 属性，客户端用这些属性来配置对底层对象存储（如 S3）的访问。已有属性涵盖凭证（`s3.access-key-id`、`s3.secret-access-key`、`s3.session-token`）和远程签名（`s3.remote-signing-enabled`）。本提交补充 `s3.cross-region-access-enabled`：当值为 `true` 时，表示启用 S3 Cross-Region bucket 访问。

S3 Cross-Region bucket 访问通常与 AWS 的跨区域复制或跨区域请求场景相关：当表数据所在的 S3 bucket 与计算资源所在区域不同时，客户端需要使用跨区域访问模式（例如设置 AWS S3 客户端的 cross-region 访问器）才能正确访问数据。将该开关纳入 REST Catalog 的 config 属性，让服务端能够向客户端显式声明该表的数据访问需要启用跨区域访问，避免客户端因默认按同区域访问而失败。

本次改动是 OpenAPI 规范层面的文档性变更（仅描述属性含义），不包含 Java 实现代码。实际的属性处理逻辑（在 REST 服务端设置、在客户端读取并配置 S3 client）应在各自的实现模块中完成，本提交只规范契约。

## 如何达成设计目的

同时修改 OpenAPI 规范的两个等价表示文件：`rest-catalog-open-api.yaml`（OpenAPI 标准 YAML 规范）和 `rest-catalog-open-api.py`（Python Pydantic 模型表示，用于 Python 客户端生成）。在 `LoadTableResult.config` 属性的描述文本中追加一行说明 `s3.cross-region-access-enabled` 的含义。

### 修改详情

#### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 `LoadTableResult` 的 `config` 字段描述中追加 `s3.cross-region-access-enabled` 属性说明。

**工作逻辑**：
```yaml
         - `s3.secret-access-key`: secret for credentials that provide access to data in S3
         - `s3.session-token`: if present, this value should be used for as the session token
         - `s3.remote-signing-enabled`: if `true` remote signing should be performed as described in the `s3-signer-open-api.yaml` specification
+        - `s3.cross-region-access-enabled`: if `true`, S3 Cross-Region bucket access is enabled
```

该描述位于 `LoadTableResult` 的 `config` 字段下，向 REST Catalog 客户端实现者声明：当 config 中出现 `s3.cross-region-access-enabled=true` 时，应启用 S3 跨区域 bucket 访问。

#### `open-api/rest-catalog-open-api.py`

**修改目的**：在 Pydantic `LoadTableResult` 模型的 `config` 字段 docstring 中同步追加同一行说明，保持 Python 表示与 YAML 表示一致。

**工作逻辑**：在 `LoadTableResult` 类的 `config` 字段 docstring 中追加与 YAML 相同的描述行。这确保通过 Pydantic 模型生成的 Python 客户端文档/类型提示也包含该属性说明。

## 小结

- **成效**：REST Catalog OpenAPI 规范现声明 `s3.cross-region-access-enabled` 配置属性，为服务端与客户端之间传递"是否需要 S3 跨区域访问"提供了标准化契约。客户端实现可据此配置 S3 客户端的跨区域访问模式。
- **影响范围**：仅 `open-api/` 目录下两个规范文件各增加一行描述，无产品代码、构建或测试逻辑变更。属于规范契约层面的增量。
- **回迁到 1.4.x 的注意事项**：OpenAPI 规范通常由 main 分支统一演进，1.4.x 发布的 REST Catalog 规范快照一般不单独更新。**通常无需回迁**。若 1.4.x 用户确实需要跨区域访问属性支持，需要在客户端和 1.4.x 服务端两边同时实现该属性的生成与消费，单纯回迁规范描述无实际效果。
