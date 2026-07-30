# 提交 1151：OpenAPI: Fix YAML example and value json formatting (#11119)

## 提交信息

- **序号**：1151
- **哈希**：e3d3f8845a8ad8d29ebcee0cf3510001dba73874
- **短哈希**：e3d3f8845
- **日期**：2024-09-12（Thu Sep 12 14:56:26 2024 -0700）
- **作者**：Daniel Weeks <dweeks@apache.org>
- **提交说明**：OpenAPI: Fix YAML example and value json formatting (#11119)
- **PR/Issue**：#11119

## 总体目的

Iceberg 的 REST Catalog OpenAPI 规范文件 `open-api/rest-catalog-open-api.yaml` 中，许多响应与示例使用 YAML 的"flow style"嵌入 JSON 文本来展示响应体示例，形如：

```yaml
example: {
  "error": {
    "message": "Internal Server Error",
    "type": "CommitStateUnknownException",
    "code": 500
  }
}
```

但仓库中此类 JSON 块的缩进风格不统一——有的把 JSON 内容缩进 2 个空格（与 `example:` 同级），有的缩进 4 个空格，关闭花括号 `}` 的位置也参差不齐。这导致：
1. 阅读时视觉对齐混乱，难以快速辨认 JSON 块的边界；
2. 一些 YAML 解析器/校验器对 flow style 块的缩进敏感，可能产生解析歧义；
3. 提交 1144 引入的 `MetricResult` example 使用了 YAML mapping 风格而非 JSON flow 风格，与周围示例不一致。

本提交对全文件约 30 处 `example: { ... }` 与 `value: { ... }` 块统一缩进风格：把 JSON 内容统一再加深 2 个空格（让内容相对 `example:` 关键字多缩进 4 个空格），把关闭花括号 `}` 单独放一行并保持与 `example:` 同级缩进；并把 `MetricResult` 的 YAML mapping 风格 example 改写为 JSON flow 风格，与全文件统一。这是纯格式化提交，不改变任何 API 契约或示例数据。

## 如何达成设计目的

通过逐处调整 `open-api/rest-catalog-open-api.yaml` 中 flow style JSON 块的缩进完成统一：

1. **统一 `example: { ... }` 缩进**：把所有形如
   ```yaml
   example: {
     "error": {
       ...
     }
   }
   ```
   改为
   ```yaml
   example: {
       "error": {
         ...
       }
     }
   ```
   即 JSON 内容相对 `example:` 多缩进 4 个空格，关闭 `}` 与 `example:` 关键字的 `e` 字符对齐（位于 `example:` 之后 6 个空格处）。
2. **统一 `value: { ... }` 缩进**：同上规则，应用于 `components/examples` 下的示例（如 `NoSuchTableError`、`RenameTableSameNamespace` 等）。
3. **统一 `example: [ ... ]` 缩进**：对数组形式的 example（如 `CatalogConfig.endpoints`）应用相同规则。
4. **`MetricResult` example 改写**：把原本使用 YAML mapping 风格的 `example:` 块改为 JSON flow 风格 `example: { ... }`，与全文件其它示例保持一致；同时修正了原 YAML 中 JSON 对象末尾多余的逗号（如 `"value": 1,`），让 JSON 严格合法。
5. **保持所有示例的语义内容不变**：所有 message、type、code、字段值等数据完全保留，仅缩进与风格调整。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

涉及约 30 处修改，按位置归类如下（仅说明改动模式，不逐一罗列）：

#### 1. `paths:` 下各端点的响应 example（约 12 处）

包括：
- `GET /v1/config` 的 200 响应中的 `CatalogConfig` example（含 `overrides`/`defaults`/`endpoints`）；
- `POST /v1/{prefix}/namespaces/{namespace}/tables/{table}`（commit 端点）的 500/502/504/5XX 错误响应 example；
- `POST .../views`（view commit）的 500/502/504/5XX 错误响应 example；
- `POST .../tables/{table}/metrics` 的 500/502/504/5XX 错误响应 example。

每处均按上述规则把 JSON 内容缩进加深 2 个空格、关闭 `}` 单独成行。

#### 2. `components/schemas/CatalogConfig.endpoints` 的 example 数组

将 `example: [ ... ]` 中每个字符串元素的缩进加深 2 个空格，关闭 `]` 单独成行。

#### 3. `components/schemas/MetricResult` 的 example（结构性改写）

原 example 使用 YAML mapping 风格：
```yaml
example:
  "metrics": {
    "total-planning-duration": {
      "count": 1,
      ...
    },
    "result-data-files": {
      "unit": "count",
      "value": 1,    # 注意末尾多余逗号
    },
    ...
  }
```

改写为 JSON flow 风格：
```yaml
example: {
    "metrics": {
      "total-planning-duration": {
        "count": 1,
        ...
      },
      "result-data-files": {
        "unit": "count",
        "value": 1
      },
      ...
    }
  }
```

改动要点：
- `example:` 后接 `{`，开启 JSON flow 块；
- 整体缩进统一为 4 空格 + 6 空格 + 8 空格的层级；
- 删除了每个对象最后一个键值对末尾的多余逗号（`"value": 1,` → `"value": 1`），让 JSON 严格合法（YAML 中尾随逗号会被当作字符串内容，可能造成解析问题）。

#### 4. `components/responses/` 下的响应 example（约 8 处）

包括 `BadRequestErrorResponse`、`UnauthorizedResponse`、`ForbiddenResponse`、`UnsupportedOperationResponse`、`AuthenticationTimeoutResponse`、`ServiceUnavailableResponse`、`ServerErrorResponse`、`CreateNamespaceResponse`、`UpdateNamespacePropertiesResponse` 等。每处按规则调整缩进。

#### 5. `components/examples/` 下的命名示例（约 12 处）

包括 `ListTablesEmptyExample`、`ListNamespacesEmptyExample`、`ListNamespacesNonEmptyExample`、`ListTablesNonEmptyExample`、`NamespaceAlreadyExistsError`、`NoSuchPlanIdError`、`NoSuchPlanTaskError`、`NoSuchTableError`、`NoSuchViewError`、`NoSuchNamespaceError`、`RenameTableSameNamespace`、`RenameViewSameNamespace`、`TableAlreadyExistsError`、`ViewAlreadyExistsError`、`UnprocessableEntityDuplicateKey`、`UpdateAndRemoveNamespacePropertiesRequest` 等。每处把 `value: { ... }` 中的 JSON 内容缩进加深 2 个空格。

注意 `NoSuchPlanIdError` 与 `NoSuchPlanTaskError` 是提交 1144 新增的示例，本提交一并统一其缩进——这也是为什么 1144 的回迁注意事项中提到"后续提交会对本提交引入的 YAML 做格式修复"。

## 小结

- **成效**：统一了 `rest-catalog-open-api.yaml` 中约 30 处 flow style JSON 示例块的缩进风格，让全文件 example 缩进一致、视觉对齐清晰；并把 `MetricResult` 的 YAML mapping 风格 example 改写为 JSON flow 风格、删除尾随逗号，与全文件统一并提升 JSON 合法性。
- **影响范围**：仅 `open-api/rest-catalog-open-api.yaml` 一个文件，+229/-228 行（净 +1 行，来自 `MetricResult` 改写时新增的闭合花括号行）。所有改动均为格式化，**不改变任何 API 契约、不改变示例数据语义**。
- **回迁到 1.4.x 的注意事项**：
  - 这是纯格式化提交，**无运行时影响**，回迁零风险。
  - 是否回迁取决于 1.4.x 是否已回迁提交 1144（新增 Scan Planning Endpoints）。若 1.4.x 已回迁 1144，则本提交中针对 `NoSuchPlanIdError`/`NoSuchPlanTaskError` 的缩进调整应一并回迁，保持规范文件格式一致；若 1.4.x 未回迁 1144，则本提交中针对既有示例的格式化可单独回迁（无副作用），但价值有限。
  - `MetricResult` 的尾随逗号修复是潜在的正确性改进（虽然 YAML 解析器通常容忍），若 1.4.x 中存在该示例，建议至少回迁这一处。
  - 由于本提交是纯格式化，回迁后不会影响任何 REST Catalog 实现的行为，也不会影响 Swagger Editor 渲染结果（渲染只看语义不看缩进）。
