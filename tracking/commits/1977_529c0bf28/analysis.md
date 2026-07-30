# 提交 1977：Core: Return this instead of null in enableRowLineage() (#12747)

## 提交信息

- **序号**：1977 / 4088
- **哈希**：529c0bf2855921d1c964159188acbf531a807684
- **短哈希**：529c0bf28
- **日期**：2025-04-09 09:10:09 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Return this instead of null in enableRowLineage() (#12747)
- **PR/Issue**：#12747

## 总体目的

本提交是提交 1973（为所有 v3 表启用 row lineage）的一个小修复。在 1973 中，`TableMetadata.Builder.enableRowLineage()` 被重写为对 v3+ 表的 no-op，但当时返回的是 `null` 而非 `this`。

`enableRowLineage()` 的方法签名声明返回 `Builder`，按建造者模式（Builder pattern）的约定，no-op 方法应返回 `this` 以支持链式调用。返回 `null` 会导致调用方进行链式调用时立即抛出 `NullPointerException`，这是一个明显的回归缺陷。本提交将其修正为返回 `this`，恢复正常的链式调用能力。

## 如何达成设计目的

将 no-op 分支的返回值由 `null` 改为 `this`，保持建造者模式的链式调用契约。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (修改, +1/-1 lines)

**修改目的**：修复 `enableRowLineage()` no-op 返回 null 导致链式调用 NPE 的问题。

**工作逻辑**：在 `enableRowLineage()` 方法中，当 `formatVersion >= MIN_FORMAT_VERSION_ROW_LINEAGE` 时（即 v3+ 表），原 `return null;` 改为 `return this;`。该方法是 `@Deprecated` 的 no-op（v3 表 row lineage 已强制启用），返回 `this` 使其可安全地出现在链式调用中而不中断。

## 总结

本提交修正了 1973 引入的回归：`TableMetadata.Builder.enableRowLineage()` 在 v3+ 表上的 no-op 分支由返回 `null` 改为返回 `this`，恢复建造者模式的链式调用契约，避免调用方触发 NPE。仅一行改动。
