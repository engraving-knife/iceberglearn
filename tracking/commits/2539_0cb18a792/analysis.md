# 提交 2539：Flink: Move state import in SkipOnError from v2 to v1 (#13888)

## 提交信息

- **序号**：2539 / 4088
- **哈希**：0cb18a79295e3b2a45e0d57e05f7029e5af5a64a
- **短哈希**：0cb18a792
- **日期**：2025-08-21 19:01:21 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Move state import in SkipOnError from v2 to v1 (#13888)
- **PR/Issue**：#13888

## 总体目的

上一提交 #13302 在 Flink v2.0 的 `SkipOnError` 算子中使用了 `org.apache.flink.api.common.state.v2.ListState` 与 `ListStateDescriptor`（Flink 状态 API v2）。但该算子继承的是 `AbstractStreamOperator` 并使用 `StateInitializationContext`（属于旧的 v1 算子体系），v2 状态 API 与 v1 算子上下文混用会导致状态注册与访问不一致的问题，可能引发运行时错误或状态不兼容。

本次提交把 `SkipOnError` 中的状态 API 从 v2 改回 v1（即 `org.apache.flink.api.common.state.ListState` / `ListStateDescriptor`），与算子基类保持一致。同时为 `processElement1`/`processElement2` 增加 `throws Exception`，以匹配 v1 接口签名（v1 的 `processElement` 声明了 `throws Exception`，v2 不需要）。

这是对 #13302 的快速修复，确保 v2.0 maintenance 算子状态访问路径正确。

## 如何达成设计目的

- 把 `import org.apache.flink.api.common.state.v2.ListState` 改为 `import org.apache.flink.api.common.state.ListState`，`ListStateDescriptor` 同理。
- `processElement1` 与 `processElement2` 方法签名增加 `throws Exception`，匹配 v1 `TwoInputStreamOperator` 接口契约。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/SkipOnError.java` (+4/-4)

**修改目的**：状态 API 回退到 v1，与算子基类一致。

**工作逻辑**：
- 修改两个 import：`state.v2.ListState` → `state.ListState`，`state.v2.ListStateDescriptor` → `state.ListStateDescriptor`。
- `processElement1`、`processElement2` 增加 `throws Exception`。其余逻辑（`filesToDelete.add`、`hasError.add`、`hasErrorFlag=true`、`filesToDelete.clear()`）不变。

## 总结

修复 #13302 引入的 `SkipOnError` 算子中 v2 状态 API 与 v1 算子基类不匹配的问题，将 `ListState`/`ListStateDescriptor` 的 import 从 `state.v2` 包改回 `state` 包，并为 process 方法补 `throws Exception` 签名，保证状态访问与算子体系一致。
