# 提交 0197：Flink: Proper backport for #8852 (#9146)

## 提交信息

- **序号**：0197 / 4088
- **哈希**：10a856e0f4e6bd1653ed66a5ba232277834b9a8d
- **短哈希**：10a856e0f
- **日期**：2023-11-24
- **作者**：pvary
- **提交说明**：Flink: Proper backport for #8852 (#9146)
- **PR/Issue**：#9146（为 #8852 的正确回 port）

## 总体目的

这个提交是对早先 PR #8852 回 port 到 Flink v1.15/v1.16 的一次"修正性回 port"（proper backport）。#8852 原本是对 Flink source 测试的改动，但之前的回 port 在代码风格上与上游主线（main）不一致——主要体现在静态导入（static import）的使用以及一个不必要的局部变量 `CountDownLatch latch`。本提交把 v1.15 与 v1.16 两个分支的对应测试代码调整成与 main 完全一致的写法，确保后续维护与 cherry-pick 不会因风格差异产生冲突。

具体地，原回 port 在 `TestIcebergSourceFailover` 中通过 `static import` 引入 `assertTableRecords` 直接调用，而主线使用的是 `SimpleDataUtil.assertTableRecords(...)` 的常规调用形式；在 `TestStreamingMonitorFunction` 中，原回 port 先声明 `CountDownLatch latch = new CountDownLatch(1)` 再传给 `TestSourceContext`，而主线直接内联为 `new TestSourceContext(new CountDownLatch(1))`。这些都是纯测试代码的风格修正，不改变运行时行为，但对保持多分支（main、v1.16、v1.15）之间测试代码的一致性具有重要意义，能降低未来回 port 其他改动时的合并冲突概率。

## 如何达成设计目的

对 v1.15 与 v1.16 两个 Flink 分支的 `TestIcebergSourceFailover` 与 `TestStreamingMonitorFunction` 两个测试类做对称的、与主线一致的改写：把静态导入改为常规导入并显式以 `SimpleDataUtil.assertTableRecords(...)` 调用；把 `CountDownLatch latch` 局部变量内联进 `TestSourceContext` 构造参数。两个分支的改动完全镜像。

## 修改详情

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java` 与 `flink/v1.16/.../TestIcebergSourceFailover.java`

**修改目的**：把对 `assertTableRecords` 的调用方式从静态导入改为 `SimpleDataUtil.assertTableRecords(...)`，与主线保持一致。

**工作逻辑**：移除 `import static org.apache.iceberg.flink.SimpleDataUtil.assertTableRecords;`，新增常规 `import org.apache.iceberg.flink.SimpleDataUtil;`；两处 `assertTableRecords(sinkTableResource.table(), expectedRecords, Duration.ofSeconds(120));` 改为 `SimpleDataUtil.assertTableRecords(sinkTableResource.table(), expectedRecords, Duration.ofSeconds(120));`（因格式化拆成两行）。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingMonitorFunction.java` 与 `flink/v1.16/.../TestStreamingMonitorFunction.java`

**修改目的**：内联 `CountDownLatch latch` 局部变量，消除与主线不一致的多余变量声明。

**工作逻辑**：五处 `CountDownLatch latch = new CountDownLatch(1); TestSourceContext sourceContext = new TestSourceContext(latch);` 合并为 `TestSourceContext sourceContext = new TestSourceContext(new CountDownLatch(1));`（`latch` 仅用于传给 `TestSourceContext`，内联后无副作用差异）。v1.15 版本另在 `function.close()` 前补一行空行以对齐主线格式。

## 小结

该提交通过把 v1.15/v1.16 两个分支的测试代码风格修正为与主线一致（静态导入改常规导入、内联临时变量），保证了 #8852 回 port 的正确性与多分支一致性，降低了未来跨分支合并的冲突风险。
