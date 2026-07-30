# 提交 1192：[Core] Fix TestFastAppend.testAddManyFiles() (#11218)

## 提交信息

- **序号**：1192 / 4088
- **哈希**：dddb5f423b353d961b8a08eb2cb4371d453c2959
- **短哈希**：dddb5f423
- **日期**：2024-09-26（Thu Sep 26 17:13:26 2024 -0700）
- **作者**：Anurag Mantripragada <amantripragada@apple.com>
- **提交说明**：[Core] Fix TestFastAppend.testAddManyFiles() (#11218)
- **PR/Issue**：#11218

## 总体目的

`TestFastAppend` 是位于 `core` 模块的测试类，用于验证 Iceberg 的 Fast Append（快速追加）行为。该类中有一个 `testAddManyFiles()` 测试方法，原代码使用 `table.newAppend()` 创建追加操作，而 `newAppend()` 返回的是普通的 `AppendFiles`（由 `MergeAppend` 实现，会触发 manifest 合并逻辑），与 `TestFastAppend` 类的命名意图（专门测试 Fast Append）不符，因此测试名与实际行为不一致。

本提交将测试中创建 Append 操作的方式从 `table.newAppend()` 改为 `table.newFastAppend()`，让该测试方法真正调用 Fast Append 路径，使测试名与测试行为一致，确保测试断言针对的是 Fast Append 的实际表现。

## 如何达成设计目的

直接修改 `core/src/test/java/org/apache/iceberg/TestFastAppend.java` 中 `testAddManyFiles()` 方法内的一行代码，将 `AppendFiles append = table.newAppend();` 改为 `AppendFiles append = table.newFastAppend();`。改动只触及一行，不影响其他测试方法或生产代码。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestFastAppend.java`

**修改目的**：让 `testAddManyFiles()` 真正走 Fast Append 路径，与类名及方法语义保持一致。

**工作逻辑**：原代码先在循环里构造多个 `dataFile` 加入列表，然后通过 `table.newAppend()` 创建 append 操作并调用 `appendFile` 逐个追加，最后 `commit()`。`newAppend()` 走的是 `MergeAppend`，会在提交时进行 manifest 合并，而 `newFastAppend()` 走 `FastAppend`，会直接追加新的 manifest 而不触发合并。本提交将 append 操作创建方式改为 `table.newFastAppend()`，其余流程（`forEach` 追加、`commit`）保持不变：

```java
AppendFiles append = table.newFastAppend();
dataFiles.forEach(append::appendFile);
append.commit();
```

这样测试就真正断言 Fast Append 行为，避免"测试名说 FastAppend，实际测 MergeAppend"的语义错位。

## 小结

- **成效**：`TestFastAppend.testAddManyFiles()` 现在确实测试 Fast Append 路径，与类和方法名一致；测试用例的语义与覆盖目标对齐。
- **影响范围**：仅一行测试代码改动，不触及生产代码，不影响发布产物或运行时行为。
- **回迁到 1.4.x 的注意事项**：这是纯粹的测试用例修正，不修复任何 bug，也不改变生产行为。1.4.x 的测试若存在同样的"测试名与实际行为不符"问题，回迁此改动可提升测试语义一致性，但对 1.4.x 的功能正确性没有影响，**回迁价值低，可选回迁**。如果 1.4.x 测试基线本就使用 `newAppend()`，则不会破坏 CI。
