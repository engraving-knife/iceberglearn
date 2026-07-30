# 提交 0378：Docs: Fix typo in tag reading example (#9496)

## 提交信息

- **序号**：0378
- **哈希**：fe004c5bfe26a7bfbb3d7b400dc4376420168c58
- **短哈希**：fe004c5bf
- **日期**：2024-01-17 14:50:02 +0100
- **作者**：pvary <peter.vary.apache@gmail.com>
- **提交说明**：Docs: Fix typo in tag reading example (#9496)\n\nCo-authored-by: Peter Vary <peter_vary4@apple.com>
- **PR/Issue**：#9496

## 总体目的

这个提交修复了 Java API 快速入门文档中一段 tag（标签）读取示例代码的类型错误：示例把 `table.newScan().useRef("audit-tag")` 的返回值错误地声明为 `Table`，而实际上 `newScan()` 返回的是 `TableScan`。这会误导直接照抄文档的读者写出无法编译的代码，也破坏了文档自身的准确性。

Iceberg 的 `Table` 接口与 `TableScan` 接口职责分明：`Table` 代表表的元数据视图，提供 `newScan()`、`newAppend()`、`currentSnapshot()` 等表级操作入口；而 `TableScan` 是一次扫描任务的构建器，承载过滤、投影、快照选择、ref（branch/tag）选择等扫描参数，最终通过 `planFiles()` 产出可执行的数据扫描任务。文档同一段落中紧邻的 branch 读取示例已经正确使用了 `TableScan branchRead = table.newScan().useRef("test-branch");`，但紧随其后的 tag 读取示例却写成 `Table tagRead = ...`，前后类型不一致，显然是笔误。这种"同类相邻示例一个对一个错"的情况对新手尤其有迷惑性，因为读者会倾向于认为两者类型不同的背后有深意。

修复后两处示例统一为 `TableScan`，既与 API 真实签名一致，也保持了段落内部的一致性。

## 如何达成设计目的

直接将文档示例代码中的声明类型从 `Table` 改为 `TableScan`，与上一行 branch 读取示例及 `TableScan` API 的真实返回类型对齐。改动仅限文档，不涉及任何源码。

## 修改详情

### `docs/java-api-quickstart.md`

**修改目的**：纠正 tag 读取示例中错误的变量声明类型，使其与 `table.newScan()` 的真实返回类型 `TableScan` 一致。

**工作逻辑**：

在"Reading from a branch or tag"小节的代码块中，将：

```java
// Read from the snapshot referenced by audit-tag
Table tagRead = table.newScan().useRef("audit-tag");
```

改为：

```java
// Read from the snapshot referenced by audit-tag
TableScan tagRead = table.newScan().useRef("audit-tag");
```

`Table.newScan()` 的契约是返回 `TableScan`，`.useRef(String)` 是 `TableScan` 上的链式方法，用于指定从哪个 ref（branch 或 tag）读取快照。原示例用 `Table` 接收这个返回值在语义上就是错的——`Table` 没有 `useRef` 方法，照抄会编译失败。修正后类型与上一行 branch 读取示例（`TableScan branchRead = ...`）完全一致，整段示例的语义和类型都自洽。

## 小结

一个单行的文档准确性修复。它本身不改变任何运行时行为，但消除了新手照搬快速入门示例时遇到编译错误的隐患，维护了官方文档作为"可复制粘贴起点"的可信度。文档质量在开源项目的首次体验中权重很高，这类 typo 修复虽小却是必要的。
