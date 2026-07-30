# 提交 1078：Core: Remove unused throws declarations (#10974)

## 提交信息

- **序号**：1078 / 4088
- **哈希**：f17c225f6709b430019512f426dafcc323c9b46b
- **短哈希**：f17c225f6
- **日期**：2024-08-21 10:09:23 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Core: Remove unused throws declarations (#10974)
- **PR/Issue**：#10974

## 总体目的

在 Java 中，方法签名上的 `throws` 声明用于告知调用方"该方法可能抛出这些受检异常"。但如果方法体实际上不会抛出该异常（或异常已被捕获/包装），多余的 `throws` 声明会带来一系列问题：

1. **误导调用方**：调用方被迫处理或继续声明永远不可能发生的异常，增加无谓的 try/catch 或 throws 传播。
2. **掩盖真实异常路径**：签名上列出一堆异常，让人难以判断哪些是真正需要关注的。
3. **阻碍重构**：后续修改该方法时，移除真实异常源后，多余的 throws 不会被编译器检测到，逐渐累积成"历史包袱"。
4. **测试代码尤其严重**：测试方法通常没有调用方，多余的 throws 完全是噪声，干扰阅读。

Iceberg 此前在 `TestParallelIterable` 这个测试类中，有 3 个测试方法签名上声明了 `throws IOException, IllegalAccessException, NoSuchFieldException`，但实际上方法体内并不会抛出这些异常（可能是早期实现用过反射访问私有字段、或用过会抛 IOException 的 API，后来重构掉了，但 throws 声明被遗忘）。

本提交的目的是清理这 3 个测试方法上未使用的 throws 声明，使签名与方法体实际行为一致，提升代码可读性和可维护性。这是项目"代码卫生"系列清理的一部分，通常与 error-prone 等静态检查配合（虽然本提交未直接关联某条 error-prone 规则，但符合同样的"零容忍死代码"理念）。

## 如何达成设计目的

实现方式非常直接：扫描 `TestParallelIterable` 类，找出所有声明了 `throws` 但方法体不抛出该异常的测试方法，移除其 `throws` 子句。同时移除因此变得未使用的 `import java.io.IOException`。

具体涉及 3 个测试方法：
- `closeParallelIteratorWithoutCompleteIteration`
- `closeMoreDataParallelIteratorWithoutCompleteIteration`
- `limitQueueSize`

每个方法签名从 `... throws IOException, IllegalAccessException, NoSuchFieldException` 改为无 throws 声明。`import java.io.IOException` 因不再使用而被移除。

## 修改详情

### `core/src/test/java/org/apache/iceberg/util/TestParallelIterable.java`

**修改目的**：移除 3 个测试方法上未使用的 `throws IOException, IllegalAccessException, NoSuchFieldException` 声明，以及因此未使用的 `import java.io.IOException`。

**工作逻辑**：

1. **移除未使用 import**：

```diff
 import static org.assertj.core.api.Assertions.assertThat;

-import java.io.IOException;
 import java.util.Collections;
 import java.util.Iterator;
 import java.util.List;
```

`IOException` 不再被任何方法签名引用，import 被移除。

2. **移除 3 个测试方法的 throws 声明**：

```diff
   @Test
-  public void closeParallelIteratorWithoutCompleteIteration()
-      throws IOException, IllegalAccessException, NoSuchFieldException {
+  public void closeParallelIteratorWithoutCompleteIteration() {
```

```diff
   @Test
-  public void closeMoreDataParallelIteratorWithoutCompleteIteration()
-      throws IOException, IllegalAccessException, NoSuchFieldException {
+  public void closeMoreDataParallelIteratorWithoutCompleteIteration() {
```

```diff
   @Test
-  public void limitQueueSize() throws IOException, IllegalAccessException, NoSuchFieldException {
-
+  public void limitQueueSize() {
```

三个方法的 `throws IOException, IllegalAccessException, NoSuchFieldException` 子句被整体移除。`limitQueueSize` 方法体开头的空行也一并清理（属于同一处编辑的附带整理）。

这些 throws 声明可能是早期测试实现中用反射（`IllegalAccessException`/`NoSuchFieldException` 是反射 API 的典型受检异常）访问 `ParallelIterable` 内部私有字段来断言队列状态时遗留的；后来测试重构为通过公共 API 断言，反射代码被移除，但 throws 声明被遗忘。`IOException` 可能来自早期使用 `CloseableIterable` 显式 close 的 try/catch 嵌套，重构后不再需要。

## 小结

- **成效**：清理了 `TestParallelIterable` 中 3 个测试方法上未使用的 throws 声明和 1 个未使用 import，使测试签名与方法体实际行为一致，减少代码噪声、提升可读性。
- **影响范围**：仅 1 个测试文件，4 处改动（3 个方法签名 + 1 个 import），共 3 增 7 删。不影响任何生产代码、API 或测试覆盖行为。
- **回迁到 1.4.x 的注意事项**：本提交属于纯代码清理，**可以安全回迁到 1.4.x**，风险极低。回迁前需确认 1.4.x 分支的 `TestParallelIterable` 这 3 个方法签名是否与 main 分支改造前一致（即同样有这些 throws 声明）；如果 1.4.x 上这些方法体确实仍会抛出对应异常（例如 1.4.x 仍使用反射实现），则不能简单移除 throws，否则会导致编译失败。建议直接 cherry-pick 后跑一次编译验证即可。回迁优先级低，仅为代码整洁性改进。
