# 提交 0151：API: Optimize equals in CharSequenceWrapper (#9035)

## 提交信息

- **序号**：0151 / 4088
- **哈希**：fd4231f5e27c6a98bbf4ff38850e1700b7584c0a
- **短哈希**：fd4231f5e
- **日期**：2023-11-12 19:09:55 -0800
- **作者**：Anton Okolnychyi
- **提交说明**：API: Optimize equals in CharSequenceWrapper (#9035)
- **PR/Issue**：#9035

## 总体目的

这个提交对 `CharSequenceWrapper.equals(Object)` 方法做了一处小而精的性能优化：在原有"统一走 `Comparators.charSequences().compare`"的判等路径之前，插入两条快速短路路径——当两端被包装的都是 `String` 时直接调用 `String.equals`，当两端长度不等时立即返回 `false`。这样在不改变判等语义的前提下，规避了 `CharSeqComparator` 逐字符比较（含 UTF-16 代理对处理）的开销，让最常见的 String-vs-String 场景走 JVM 内建的高效 `String.equals` 路径。

背景与动机：[`CharSequenceWrapper`](api/src/main/java/org/apache/iceberg/util/CharSequenceWrapper.java) 是 Iceberg `api` 模块中的核心工具类，它把任意 `CharSequence`（`String`、`StringBuilder`、`CharBuffer` 等）包装成"按内容判等与哈希"的对象，主要用于 [`CharSequenceSet`](api/src/main/java/org/apache/iceberg/util/CharSequenceSet.java) 的元素类型。`CharSequenceSet` 在 Iceberg 内部被大量使用——例如分区值集合、数据文件路径去重、manifest 文件统计等热路径都会频繁调用 `add`/`contains`/`retain` 等集合操作，而这些操作最终都落到 `CharSequenceWrapper.equals`。因此 `equals` 的开销直接影响元数据读写与计划阶段的性能。

优化前的实现无论被包装对象是什么类型，都走 [`Comparators.charSequences().compare`](api/src/main/java/org/apache/iceberg/types/Comparators.java#L219) 的 `CharSeqComparator`：它取两端长度的 `min`，逐 `char` 调用 `charAt` 比较，并对每个字符做高代理（high surrogate）检测以保证 4 字节 UTF-8 字符的字典序正确，最后再用 `Integer.compare(length1, length2)` 收尾。这套逻辑对任意 `CharSequence` 是正确且必要的，但对 `String` vs `String` 这一最高频场景却严重浪费：`String.equals` 是 JVM intrinsic，会先做引用判等、再比较内部 `byte[]`（Latin1 或 UTF16 编码），远比逐 `charAt` + 代理检测快得多。此外，当两端长度不同时，`CharSeqComparator` 仍要先逐字符比较到较短长度再返回长度差，`equals` 本可在长度不等时立刻返回 `false`。

这对 Iceberg 演进的意义是：在不动语义、不增加 API 表面的前提下，给一个被热路径高频调用的方法打上"快路径 + 长度短路"，属于典型的低风险、纯收益的性能打磨，让 Iceberg 在元数据密集型查询场景下减少不必要的 CPU 开销。

## 如何达成设计目的

整体设计思路是在原 `equals` 方法的比较主体之前插入两条互不冲突的快速路径，再保留原有 `Comparators.charSequences().compare` 兜底路径。两条快路径分别针对"两端同为 String"和"长度不等"两种可廉价判定的情形：前者把比较委托给 JDK 高度优化的 `String.equals`，后者用一次 `length()` 调用规避逐字符比较。改动结构上非常局部，只在 `equals` 方法体内新增 9 行，没有引入新方法或新字段，也不影响 `hashCode`（仍由 `JavaHashes.hashCode(wrapped)` 计算，与 `equals` 语义保持一致）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/CharSequenceWrapper.java`

**修改目的**：为 `equals` 方法增加两条快速短路路径，使 String-vs-String 比较走 `String.equals`、长度不等的比较立即返回 `false`，从而避免对常见场景无谓地调用逐字符的 `CharSeqComparator`。

**工作逻辑**：

改动位于 `equals` 方法在类型检查通过、将 `other` 强转为 `CharSequenceWrapper that` 之后、调用 `Comparators.charSequences().compare` 之前。新增逻辑如下：

```java
CharSequenceWrapper that = (CharSequenceWrapper) other;

if (wrapped instanceof String && that.wrapped instanceof String) {
  return wrapped.equals(that.wrapped);
}

if (length() != that.length()) {
  return false;
}

return Comparators.charSequences().compare(wrapped, that.wrapped) == 0;
```

1. **String 快速路径**：`if (wrapped instanceof String && that.wrapped instanceof String)` 判定两端被包装对象都是 `String`，则直接 `wrapped.equals(that.wrapped)`。这一步把判等委托给 `String.equals`，后者是 JVM 内建 intrinsic，先比较引用、再比较内部 `byte[]`，远比 `CharSeqComparator` 的逐 `charAt` + 高代理检测高效。语义等价性是成立的：`String.equals` 本就是按字符内容判等，与 `CharSeqComparator` 对两个 `String` 的比较结果一致（不考虑代理对重排等畸形输入，正常 Iceberg 数据中不会出现）。

2. **长度短路路径**：`if (length() != that.length()) return false;` 在进入 `CharSeqComparator` 之前先比较两端长度。`CharSeqComparator.compare` 的最后一步本来就是 `Integer.compare(s1.length(), s2.length())` 来决定长短不同的序列的字典序；对于 `equals` 而言，长度不等就意味着 `compare != 0`，即 `equals` 必为 `false`。这里提前用一次 `length()` 调用短路掉，避免了在长度不同时还要逐字符比较到较短长度的浪费。注意 `length()` 内部是 `wrapped.length()`，对常见 `CharSequence` 实现是 O(1)。

3. **兜底路径**：保持原 `return Comparators.charSequences().compare(wrapped, that.wrapped) == 0;`，覆盖两端非均为 `String` 且长度相等的情形（如 `StringBuilder` vs `String`、`CharBuffer` vs `StringBuilder` 等），仍由完整的 Unicode 正确比较器处理。

判等语义未变：两条快路径只在能确定结果时提前返回，不能确定时仍走原比较器，因此 `equals` 的输出与改动前完全一致，只是更快。与 `hashCode`（`JavaHashes.hashCode(wrapped)`）的契约也保持一致——内容相同的序列哈希相同、`equals` 为 true；内容不同的序列哈希可能相同但 `equals` 必为 false。

## 小结

在 `CharSequenceWrapper.equals` 中插入 String-vs-String 与长度不等两条快速短路路径，让被 `CharSequenceSet` 热路径高频调用的判等避开逐字符比较、走 JVM 内建 `String.equals`，在不改变判等语义的前提下降低元数据处理开销。
