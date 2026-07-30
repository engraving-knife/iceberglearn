# 提交 2665：Core: Fix Scan Plan API resource paths (#14120)

## 提交信息

- **序号**：2665 / 4088
- **哈希**：b99caa4aaf53bb30eebcb478743d3845e76ccf4c
- **短哈希**：b99caa4aa
- **日期**：2025-09-20 10:01:08 -0600
- **作者**：Prashant Singh
- **提交说明**：Core: Fix Scan Plan API resource paths (#14120)
- **PR/Issue**：#14120
- **共同作者**：Prashant Singh (prashant.singh@snowflake.com)

## 总体目的

本提交修复了 Iceberg REST Catalog 中 Scan Plan API 的资源路径定义错误。Scan Plan API 是表级别的 API，其路径应包含命名空间（namespace）信息，但原有的三个路径常量（`V1_TABLE_SCAN_PLAN_SUBMIT`、`V1_TABLE_SCAN_PLAN`、`V1_TABLE_SCAN_PLAN_TASKS`）错误地省略了 `namespaces/{namespace}` 部分，直接使用 `/v1/{prefix}/tables/{table}/...` 格式。

这与 Iceberg REST API 规范中其他表级别 API 的路径格式不一致。例如，`V1_TABLE`、`V1_TABLE_METRICS` 等路径都包含 `namespaces/{namespace}` 段：`/v1/{prefix}/namespaces/{namespace}/tables/{table}/...`。缺少命名空间段意味着 Scan Plan API 无法正确路由到特定命名空间下的表。

由于这些路径常量之前就是错误的（使用错误路径的 API 从未正常工作过），因此修复不会造成实际的兼容性破坏。

## 如何达成设计目的

通过修改 `ResourcePaths.java` 中三个路径常量的值，在 `{prefix}` 和 `tables` 之间插入 `namespaces/{namespace}` 段，使其与其他表级别 API 路径格式一致。同时在 `revapi.yml` 中记录这些常量值变更，标注理由说明这不是实际的兼容性破坏。

## 修改详情

### `.palantir/revapi.yml` (+12/-0 lines)

**修改目的**：记录三个路径常量的值变更，使 API 兼容性检查通过。

**工作逻辑**：在 `acceptedBreaks` 的 `"1.10.0"` 版本块下新增三条 `java.field.constantValueChanged` 记录，分别对应 `V1_TABLE_SCAN_PLAN`、`V1_TABLE_SCAN_PLAN_SUBMIT`、`V1_TABLE_SCAN_PLAN_TASKS`，每条标注理由："Plan API is table scoped and path constant value should include namespace. No actual breakage because it never worked before with incorrect value."

### `core/src/main/java/org/apache/iceberg/rest/ResourcePaths.java` (+9/-3 lines)

**修改目的**：修复三个 Scan Plan API 路径常量，添加缺失的命名空间段。

**工作逻辑**：
- `V1_TABLE_SCAN_PLAN_SUBMIT`：从 `/v1/{prefix}/tables/{table}/plan` 改为 `/v1/{prefix}/namespaces/{namespace}/tables/{table}/plan`
- `V1_TABLE_SCAN_PLAN`：从 `/v1/{prefix}/tables/{table}/plan/{plan-id}` 改为 `/v1/{prefix}/namespaces/{namespace}/tables/{table}/plan/{plan-id}`
- `V1_TABLE_SCAN_PLAN_TASKS`：从 `/v1/{prefix}/tables/{table}/tasks` 改为 `/v1/{prefix}/namespaces/{namespace}/tables/{table}/tasks`

修复后的路径格式与同文件中其他表级别 API（如 `V1_TABLE`、`V1_TABLE_METRICS`）保持一致。

## 总结

本提交修复了 Scan Plan API 三个资源路径常量中缺失命名空间段的问题。修复后的路径格式 `/v1/{prefix}/namespaces/{namespace}/tables/{table}/...` 与其他表级别 REST API 保持一致。由于错误路径从未正常工作过，此次修复不会造成实际的兼容性破坏，revapi.yml 中的记录也确认了这一点。
