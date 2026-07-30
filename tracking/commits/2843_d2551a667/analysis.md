# 提交 2843：Add SnapshotUpdateValidator to validate snapshots on commit (#14509)

## 提交信息

- **序号**：2843 / 4088
- **哈希**：d2551a6670efabec958eb97c4bc28ffbba587633
- **短哈希**：d2551a667
- **日期**：2025-11-06 17:28:27 -0800
- **作者**：Daniel Weeks
- **提交说明**：Add SnapshotUpdateValidator to validate snapshots on commit (#14509)
- **PR/Issue**：#14509

## 总体目的

Iceberg 的 `SnapshotUpdate`（如 `AppendFiles`、`RewriteFiles`、`RowDelta` 等）在提交时已有一套基于"基础快照"的并发校验（如 `ValidateCurrentSnapshot`、`ValidateNoConflictingDeletes` 等），但这些校验是预定义的、关注并发冲突的。用户在提交时有时需要基于"快照祖先链"做自定义业务校验——例如：禁止重复发布某个 WAP（Write-Audit-Publish）ID、禁止在某些快照之后提交、要求祖先满足某种条件等。此前没有通用的扩展点让用户注入这类校验。

该提交引入 `SnapshotAncestryValidator` 函数式接口，并在 `SnapshotUpdate` 与 `SnapshotProducer` 中加入 `validateWith(SnapshotAncestryValidator)` 方法，让调用方可以在提交前注入自定义校验逻辑。校验在表元数据刷新后、manifest 应用前执行，接收快照祖先链（`Iterable<Snapshot>`），返回 false 则抛 `ValidationException` 阻止提交。

## 如何达成设计目的

1. **新增 `SnapshotAncestryValidator` 接口**（`api` 模块）：`@FunctionalInterface`，继承 `Function<Iterable<Snapshot>, Boolean>`，定义 `apply(Iterable<Snapshot> baseSnapshots)` 返回布尔，并提供 `errorMessage()` 默认方法返回错误信息（抛 `ValidationException` 时使用）。提供 `NON_VALIDATING` 常量作为默认 no-op 校验器。
2. **`SnapshotUpdate` 接口新增默认方法 `validateWith`**：默认抛 `UnsupportedOperationException`，由支持校验的实现（`SnapshotProducer`）覆盖。
3. **`SnapshotProducer` 实现 `validateWith`**：保存 validator 字段，默认 `NON_VALIDATING`；在 `commit` 流程中把原 `validate(base, parentSnapshot)` 调用抽到新方法 `runValidations(parentSnapshot)`，并在其中追加快照祖先校验：用 `SnapshotUtil.ancestorsOf(parentSnapshot.snapshotId(), base::snapshot)` 取祖先链（无 parent 时传空 `List.of()`），调用 `snapshotAncestryValidator.apply(...)`，false 则 `ValidationException.check(...)` 抛出并带上 `errorMessage()`。
4. **测试**：`TestSnapshotProducer` 改为继承 `TestBase`（获得真实表）并加 `@ExtendWith(ParameterizedTestExtension.class)`；新增 `testCommitValidationPreventsCommit` 验证返回 false 时提交被阻止且表状态不变；新增 `testCommitValidationWithCustomSummaryProperties` 验证基于祖先 summary 中 `PUBLISHED_WAP_ID_PROP` 的自定义校验——首次提交允许，重复提交相同 WAP id 被阻止。

## 修改详情

### `api/src/main/java/org/apache/iceberg/SnapshotAncestryValidator.java` (+54/-0 lines, 新文件)

**修改目的**：定义快照祖先校验接口。

**工作逻辑**：
- `@FunctionalInterface` 接口继承 `Function<Iterable<Snapshot>, Boolean>`。
- `NON_VALIDATING = baseSnapshots -> true` 默认放行。
- `Boolean apply(Iterable<Snapshot> baseSnapshots)` 由用户实现，返回是否通过校验。
- `default String errorMessage()` 返回错误信息，默认 `"error message not provided"`，抛 `ValidationException` 时使用。`@Nonnull` 标注非空。

### `api/src/main/java/org/apache/iceberg/SnapshotUpdate.java` (+5/-0 lines)

**修改目的**：在 `SnapshotUpdate` 接口暴露 `validateWith` 扩展点。

**工作逻辑**：
```java
default ThisT validateWith(SnapshotAncestryValidator validator) {
    throw new UnsupportedOperationException(
        "Snapshot validation not supported by " + this.getClass().getName());
}
```
默认抛异常，由支持校验的实现覆盖。`default` 保证向后兼容，现有实现无需改动。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+32/-2 lines)

**修改目的**：在提交流程中接入自定义校验。

**工作逻辑**：
- 新增字段 `snapshotAncestryValidator`，默认 `NON_VALIDATING`。
- 实现 `validateWith(SnapshotAncestryValidator validator)`，保存 validator 并返回 `self()`。
- 在 `commit` 中把 `validate(base, parentSnapshot)` 替换为 `runValidations(parentSnapshot)`。
- `runValidations(parentSnapshot)`：先调用原有 `validate(base, parentSnapshot)`（保留并发冲突校验）；然后用 `SnapshotUtil.ancestorsOf(parentSnapshot.snapshotId(), base::snapshot)` 取祖先链（无 parent 时 `List.of()`），调用 `snapshotAncestryValidator.apply(snapshotAncestry)`，用 `ValidationException.check(valid, "Snapshot ancestry validation failed: %s", snapshotAncestryValidator.errorMessage())` 在 false 时抛异常阻止提交。注意祖先校验在 `base`（已刷新的 TableMetadata）上执行，确保读到最新表状态。

### `core/src/test/java/org/apache/iceberg/TestSnapshotProducer.java` (+86/-2 lines)

**修改目的**：验证自定义校验能阻止非法提交且不破坏表状态。

**工作逻辑**：
- 类改为 `extends TestBase` 并 `@ExtendWith(ParameterizedTestExtension.class)`，以获得真实表与参数化测试能力。
- `testCommitValidationPreventsCommit`：先 `table.newAppend().commit()` 建一个空快照；构造一个总返回 false 的 `SnapshotAncestryValidator`（`errorMessage()` 返回 `"Validation force failed"`）；用 `table.newAppend().validateWith(validator).appendFile(FILE_A)` 提交，断言抛 `ValidationException` 且消息为 `"Snapshot ancestry validation failed: Validation force failed"`，并断言当前快照 manifest 数为 0（FILE_A 未被提交）。
- `testCommitValidationWithCustomSummaryProperties`：构造校验器，收集祖先 summary 中的 `PUBLISHED_WAP_ID_PROP`，若已包含目标 `wapId` 则返回 false；首次 `newFastAppend().validateWith(...).appendFile(FILE_A).set(PUBLISHED_WAP_ID_PROP, wapId).commit()` 成功；第二次相同 wapId 的 append 抛 `ValidationException`；断言 `table.snapshots()` 仍只有 1 个。

## 总结

该提交为 Iceberg 的快照提交流程引入了可插拔的 `SnapshotAncestryValidator` 扩展点，让用户能在提交前基于快照祖先链做自定义业务校验（如防重复 WAP id 发布、祖先约束等）。`SnapshotUpdate.validateWith` 作为接口默认方法保证向后兼容，`SnapshotProducer` 在 `runValidations` 中于原有并发校验之后执行自定义校验，失败抛 `ValidationException` 阻止提交。测试覆盖了"阻止提交"与"基于 summary 属性的自定义校验"两类场景。这是一个提升 Iceberg 提交灵活性与可扩展性的重要 API 增强。
