# 提交 0171：Core: Fix split size calculations in file rewriters (#9069)

## 提交信息

- **序号**：0171 / 4088
- **哈希**：2e2ac8dfd2ab2c214444cccb0e6024e1f80b0502
- **短哈希**：2e2ac8dfd
- **日期**：2023-11-16 11:31:17 -0600
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Fix split size calculations in file rewriters (#9069)
- **PR/Issue**：#9069

## 总体目的

这个提交修复了 `SizeBasedFileRewriter` 中 `splitSize(long inputSize)` 的计算逻辑缺陷。该类是 Iceberg `actions` 包下数据/删除文件重写器（如 `SizeBasedDataRewriter`、`SizeBasedPositionDeletesRewriter` 等）的抽象父类，负责在 bin-packing 式的压缩重写过程中确定每个 split 的目标大小。

旧逻辑只有一处上限约束：`Math.min(estimatedSplitSize, writeMaxFileSize())`，即把估算的 split 大小夹到 `writeMaxFileSize` 以下。问题在于完全没有下限保护——当输入文件总大小相对于期望输出文件数偏小时，`estimatedSplitSize = inputSize / numOutputFiles + SPLIT_OVERHEAD` 可能远小于 `targetFileSize`，导致 bin-pack 出来的 split 目标尺寸过小，最终写出比 `targetFileSize` 还小的文件，违背了压缩重写"产出接近 targetFileSize 的大文件"的初衷。

新逻辑把 split 大小夹在区间 `[targetFileSize, writeMaxFileSize()]` 内：当估算值小于 `targetFileSize` 时直接返回 `targetFileSize`，当估算值大于 `writeMaxFileSize()` 时返回 `writeMaxFileSize()`，否则返回估算值。这样保证 bin-pack 的目标 split 至少为 `targetFileSize`，避免在小输入场景下产生过多小文件，同时仍受 `writeMaxFileSize` 上限约束防止单文件过大。

这对 Iceberg 表的运维意义在于：`rewrite_data_files` / `rewrite_delete_files` 这类压缩动作的输出大小更可预期，尤其在"输入略大于 targetFileSize 但小于 2×targetFileSize 的尾数场景"中不再产生小于目标的小文件，提升存储布局质量与查询性能。

## 如何达成设计目的

通过改写 [`SizeBasedFileRewriter.splitSize`](../../../core/src/main/java/org/apache/iceberg/actions/SizeBasedFileRewriter.java) 方法的返回逻辑，把单一上限 `Math.min` 改为三分支的双边夹取（lower/upper bound clamp），并同步更新方法 Javadoc 解释新行为。同时新增 `TestSizeBasedRewriter` 单测，针对 lower bound 场景构造可复现用例验证修复后的 split 大小落在 `[targetFileSize, writeMaxFileSize)` 区间。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedFileRewriter.java`

**修改目的**：修正 `splitSize` 的下限，使其不会返回小于 `targetFileSize` 的值，避免压缩重写产出过小文件。

**工作逻辑**：

原方法体：

```java
long estimatedSplitSize = (inputSize / numOutputFiles(inputSize)) + SPLIT_OVERHEAD;
return Math.min(estimatedSplitSize, writeMaxFileSize());
```

新方法体：

```java
long estimatedSplitSize = (inputSize / numOutputFiles(inputSize)) + SPLIT_OVERHEAD;
if (estimatedSplitSize < targetFileSize) {
  return targetFileSize;
} else if (estimatedSplitSize > writeMaxFileSize()) {
  return writeMaxFileSize();
} else {
  return estimatedSplitSize;
}
```

关键变化：引入 `targetFileSize` 作为下界。`targetFileSize` 是父类 `FileRewriter` 的配置项（`TARGET_FILE_SIZE_BYTES`），表示理想输出文件大小；`writeMaxFileSize()` 是上界（`MAX_FILE_SIZE_BYTES`，通常为 target 的 1.5–2 倍，留出余量防止单文件超限）。`SPLIT_OVERHEAD` 仍是原常量，用作 inputSize/numOutputFiles 之上加一点余量，避免微小估算误差导致多产出一个全新的小文件。

Javadoc 也一并重写：从"取 max write threshold 与估算 split 的较小值并加 overhead"改为"目标 split = inputSize / 期望输出文件数，最终 split 调整为至少 targetFileSize 但小于 max write file size"。

### `core/src/test/java/org/apache/iceberg/actions/TestSizeBasedRewriter.java`

**修改目的**：新增针对 `splitSize` 下限行为的回归测试，防止修复被回退。

**工作逻辑**：新增参数化测试类（formatVersion = 1, 2），定义内部子类 `SizeBasedDataFileRewriterImpl` 继承 `SizeBasedDataRewriter`，将 protected 的 `splitSize(inputSize)` 和 `numOutputFiles(inputSize)` 暴露为 public `computeSplitSize` / `computeNumOutputFiles`，方便测试调用；`rewrite` 方法直接抛 `UnsupportedOperationException`，因为测试不关心实际重写。

`testSplitSizeLowerBound` 用 4 个 `MockFileScanTask`，每个 145 MB，总 580 MB；配置 `MIN_FILE_SIZE_BYTES=256MB`、`TARGET_FILE_SIZE_BYTES=512MB`、`MAX_FILE_SIZE_BYTES=768MB`。

- 断言 `numOutputFiles` == 2：580/512=1.13，余数 68MB > 10%×512≈51MB（参见 `numOutputFiles` 的 10% 阈值规则），故取 2 个文件。
- 断言 `splitSize >= 512MB` 且 `< 768MB`：估算值 = 580/2 + SPLIT_OVERHEAD ≈ 290MB+overhead，落在 `targetFileSize` 之下，旧逻辑会返回该小值；新逻辑因下限保护返回 `targetFileSize=512MB`，且 512MB < maxFileSize=768MB。

这一用例精确复现了 bug 触发条件（估算 split < target），并验证修复后 split 落在期望区间。

## 小结

通过给 `splitSize` 加上 `targetFileSize` 下限保护，修复了 `SizeBasedFileRewriter` 在小输入场景下产出小于目标大小的文件的缺陷，让压缩重写的输出大小更可预期。
