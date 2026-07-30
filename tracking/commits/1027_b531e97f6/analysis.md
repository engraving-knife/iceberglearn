# 提交 1027：Core: Extract filePath comparator into it's own class (#10664)

## 提交信息

- **序号**：1027 / 4088
- **哈希**：b531e97f66ef2bf80f3167152e268be0ce25f459
- **短哈希**：b531e97f6
- **日期**：2024-08-05 22:43:34 +0200
- **作者**：Denys Kuzmenko <dkuzmenko@cloudera.com>
- **提交说明**：Core: Extract filePath comparator into it's own class (#10664)
- **PR/Issue**：#10664

## 总体目的

在处理位置删除（position delete）时，Iceberg 需要将删除文件中记录的文件路径与当前读取的数据文件路径进行匹配，以判断某条位置删除记录是否作用于当前数据文件。这一匹配在 `Deletes` 类的内部过滤器 `PositionDeleteRowFilter`（`shouldKeep` 方法）中完成，原先由一个私有方法 `charSeqEquals` 实现，逐字符比较两个 `CharSequence` 是否相等。

这个相等性比较逻辑本质上是一个"比较器"的特例（结果为 0 即相等），并且带有针对文件路径特征的优化：先比长度，再比 hashCode（仅当双方都是 String 时），最后从后往前逐字符比较（因为删除文件中的路径通常共享较长前缀，差异出现在末尾的 UUID 处，从后往前扫能更快命中差异）。这套优化逻辑不仅适用于相等性判断，也可以复用于排序场景。

本提交的目的是把这段路径比较逻辑从 `Deletes` 中抽取出来，作为独立的 `FilePathComparator` 放到 `Comparators` 工具类中，并通过 `Comparators.filePath()` 对外暴露。这样既消除了 `Deletes` 中重复的私有方法，又让该优化比较能力可在其他需要按文件路径排序/比较的场景复用。

## 如何达成设计目的

在 `api` 模块的 `Comparators` 类中新增一个私有静态内部类 `FilePathComparator`，实现 `Comparator<CharSequence>`，把原先 `charSeqEquals` 中的优化逻辑搬入 `compare` 方法（返回 `int`：0 表示相等）。同时新增公共工厂方法 `Comparators.filePath()` 返回该比较器的单例实例。然后在 `core` 模块的 `Deletes` 类中，删除 `charSeqEquals` 私有方法，将 `shouldKeep` 改为调用 `Comparators.filePath().compare(dataLocation, ...) == 0`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Comparators.java`

**修改目的**：新增文件路径比较器 `FilePathComparator` 并通过 `filePath()` 方法暴露，供核心模块复用。

**工作逻辑**：在 `Comparators` 类中新增工厂方法和比较器内部类：

1. 新增公共工厂方法（与现有 `chars()` 等并列）：
```diff
   public static Comparator<CharSequence> chars() {
     return CharSeqComparator.INSTANCE;
   }

+  public static Comparator<CharSequence> filePath() {
+    return FilePathComparator.INSTANCE;
+  }
```

2. 新增私有静态内部类 `FilePathComparator`，逻辑搬自原 `Deletes.charSeqEquals`，但改为返回 `int`（比较结果）而非 `boolean`：
```java
private static class FilePathComparator implements Comparator<CharSequence> {
  private static final FilePathComparator INSTANCE = new FilePathComparator();

  @Override
  public int compare(CharSequence s1, CharSequence s2) {
    if (s1 == s2) {
      return 0;
    }
    int count = s1.length();

    int cmp = Integer.compare(count, s2.length());
    if (cmp != 0) {
      return cmp;
    }

    if (s1 instanceof String && s2 instanceof String) {
      cmp = Integer.compare(s1.hashCode(), s2.hashCode());
      if (cmp != 0) {
        return cmp;
      }
    }
    // 文件路径通常前缀相同（差异在末尾的 uuid 处），从后往前扫更快
    for (int i = count - 1; i >= 0; i--) {
      cmp = Character.compare(s1.charAt(i), s2.charAt(i));
      if (cmp != 0) {
        return cmp;
      }
    }
    return 0;
  }
}
```

优化顺序：①引用相等直接返回；②长度不等直接判定；③同为 String 时用 hashCode 快速区分；④从后往前逐字符比较命中首处差异。

### `core/src/main/java/org/apache/iceberg/deletes/Deletes.java`

**修改目的**：删除内部 `charSeqEquals` 方法，改为复用 `Comparators.filePath()` 比较器。

**工作逻辑**：
- 新增导入 `org.apache.iceberg.types.Comparators`；
- `PositionDeleteRowFilter.shouldKeep` 原调用 `charSeqEquals(dataLocation, ...)`，改为 `Comparators.filePath().compare(dataLocation, ...) == 0`（比较结果为 0 即视为相等、应保留该删除记录）；
- 删除整个 `charSeqEquals` 私有方法（约 22 行）。

```diff
     @Override
     protected boolean shouldKeep(T posDelete) {
-      return charSeqEquals(dataLocation, (CharSequence) FILENAME_ACCESSOR.get(posDelete));
-    }
-
-    private boolean charSeqEquals(CharSequence s1, CharSequence s2) {
-      // ... 原 22 行优化比较逻辑 ...
-    }
+      return Comparators.filePath()
+              .compare(dataLocation, (CharSequence) FILENAME_ACCESSOR.get(posDelete))
+          == 0;
     }
```

比较语义从"相等性"改为"比较结果为 0"，逻辑等价但能力通用化为 `Comparator`。

## 小结

- **成效**：将位置删除过滤中针对文件路径的优化比较逻辑抽取为独立的 `FilePathComparator` 并通过 `Comparators.filePath()` 暴露，消除了 `Deletes` 中的私有重复实现，提升了代码复用性和可维护性，为后续按文件路径排序/比较的场景提供通用能力。
- **影响范围**：涉及 `api` 模块的 `Comparators.java`（新增工厂方法+比较器类，+41 行）和 `core` 模块的 `Deletes.java`（删私有方法+改调用，-27/+2 行）。
- **回迁到 1.4.x 的注意事项**：属于纯重构，行为等价（比较结果 0 即相等），无功能变更，回迁风险低。回迁时需确保 `api` 模块的 `Comparators` 类在 1.4.x 中结构匹配，且新增的公共方法 `filePath()` 不会与 1.4.x 已有 API 冲突。由于新增了公共 API（`Comparators.filePath()`），回迁会引入新公开方法，需评估是否符合 1.4.x 的 API 兼容策略。如仅需修复内部逻辑可考虑不暴露公共方法。
