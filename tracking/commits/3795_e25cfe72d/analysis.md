# 提交 3795：Flink: Backport honor schema identifier fields in dynamic-sink record routing (#16597)

## 提交信息

- **序号**：3795 / 4088
- **哈希**：e25cfe72d0e9063b554d9dbcd23eb430b8a6fe46
- **短哈希**：e25cfe72d
- **日期**：2026-05-28 17:56:32 +0200
- **作者**：Jordan Epstein
- **提交说明**：Flink: Backport honor schema identifier fields in dynamic-sink record routing (#16597)
- **PR/Issue**：#16597（Backports #16243）

## 总体目的

这个提交是将第 3793 号提交（#16243）中"Flink 动态 Sink 记录路由考虑 schema 标识符字段"的修复回移植到 Flink v1.20 和 v2.0 版本分支。原始修复只合入了 Flink v2.1 分支，但 v1.20 和 v2.0 作为已发布的维护版本也需要这个修复。

问题背景与 3793 号提交相同：Flink 动态 Sink 在记录路由中没有考虑 schema 的标识符字段（identifier fields）。当用户未显式设置 equalityFields 但表 schema 有标识符字段时，这些字段应该被用作 equality 字段，且相关记录不能使用 forward 模式（必须 hash 分布以保持 equality-delete 语义正确）。

## 如何达成设计目的

与 3793 号提交完全相同的修改，对 Flink v1.20 和 v2.0 两个版本分支分别应用：
1. 在 `DynamicSinkUtil` 中新增 `resolveEqualityFieldNames` 方法。
2. 在 `DynamicRecordProcessor` 中新增 `isForwardEligible` 方法。
3. 修改 `HashKeyGenerator` 使用解析后的有效 equality 字段和 distribution mode。
4. 添加相应的测试。

## 修改详情

### `flink/v1.20/flink/src/main/java/.../DynamicRecordProcessor.java` (+12/-1 lines)

**修改目的**：修改 forward 模式判断逻辑，考虑 equality 字段。

**工作逻辑**：与 3793 号提交相同，将 `isForward` 判断从 `data.distributionMode() == null` 改为 `isForwardEligible(data)`，新增 `isForwardEligible` 方法检查 distribution mode 为 null 且解析后的 equality 字段集为空。

### `flink/v1.20/flink/src/main/java/.../DynamicSinkUtil.java` (+15/-0 lines)

**修改目的**：新增 equality 字段名解析方法。

**工作逻辑**：与 3793 号提交相同，新增 `resolveEqualityFieldNames` 方法，用户设置的 equalityFields 优先，否则回退到 schema 的标识符字段名。

### `flink/v1.20/flink/src/main/java/.../HashKeyGenerator.java` (+12/-5 lines)

**修改目的**：使用解析后的有效 equality 字段和 distribution mode。

**工作逻辑**：与 3793 号提交相同，提前解析有效 schema、equality 字段和 distribution mode，用于缓存键和 key selector。

### `flink/v1.20/flink/src/test/java/.../TestDynamicRecordProcessor.java` (+101/-0 lines)
### `flink/v1.20/flink/src/test/java/.../TestHashKeyGenerator.java` (+97/-0 lines)

**修改目的**：测试标识符字段在记录路由中的行为。

**工作逻辑**：与 3793 号提交相同的测试。

### `flink/v2.0/flink/src/main/java/.../DynamicRecordProcessor.java` (+12/-1 lines)
### `flink/v2.0/flink/src/main/java/.../DynamicSinkUtil.java` (+15/-0 lines)
### `flink/v2.0/flink/src/main/java/.../HashKeyGenerator.java` (+12/-5 lines)
### `flink/v2.0/flink/src/test/java/.../TestDynamicRecordProcessor.java` (+101/-0 lines)
### `flink/v2.0/flink/src/test/java/.../TestHashKeyGenerator.java` (+97/-0 lines)

**修改目的**：对 Flink v2.0 应用完全相同的修改和测试。

**工作逻辑**：与 v1.20 完全一致。

## 总结

这是 3793 号提交的 backport，将 Flink 动态 Sink 记录路由中考虑 schema 标识符字段的修复应用到 v1.20 和 v2.0 两个维护版本。确保所有维护中的 Flink 版本都正确处理标识符字段的 equality-delete 语义，保证数据正确性。这是多版本维护的典型工作。
