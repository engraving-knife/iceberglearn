# 提交 1241：Core: Fix version number in deprecation note for invalidateAll (#11325)

## 提交信息

- **序号**：1241 / 4088
- **哈希**：32b1ab6ea833f4067b92f7943cbfc0057faba4c7
- **短哈希**：32b1ab6ea
- **日期**：2024-10-16（Wed Oct 16 08:46:16 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Core: Fix version number in deprecation note for invalidateAll (#11325)
- **PR/Issue**：#11325

## 总体目的

紧随其后的修复型提交。前一个提交 #10494（短哈希 6fdc69a22）把 `ContentCache.invalidateAll()` 标记为 `@Deprecated`，并在 Javadoc 中写明：

```
@deprecated since 1.6.0, will be removed in 1.7.0
```

但该版本号与 Iceberg 实际发布节奏不符：`invalidateAll()` 真正被弃用的版本是 1.7.0，计划移除版本是 2.0.0（而非 1.6.0 → 1.7.0）。本提交把 Javadoc 中的版本号修正为：

```
@deprecated since 1.7.0, will be removed in 2.0.0
```

避免对用户产生错误的弃用承诺（用户原本可能依据"1.7.0 移除"做升级规划，修正后应按"2.0.0 移除"规划）。

## 如何达成设计目的

只修改 `ContentCache.java` 一行 Javadoc 文本，把 `1.6.0` 改为 `1.7.0`、`1.7.0` 改为 `2.0.0`。无任何代码逻辑变更。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/ContentCache.java`

**修改目的**：修正 `invalidateAll()` 弃用 Javadoc 中的版本号。

**工作逻辑**：

```diff
   /**
-   * @deprecated since 1.6.0, will be removed in 1.7.0; This method does only best-effort
+   * @deprecated since 1.7.0, will be removed in 2.0.0; This method does only best-effort
    *     invalidation and is susceptible to a race condition. If the caller changed the state that
    *     could be cached (perhaps files on the storage) and calls this method, there is no guarantee
    *     that the cache will not contain stale entries some time after this method returns.
    */
   @Deprecated
   public void invalidateAll() {
     cache.invalidateAll();
   }
```

方法体与注解均不变，仅 Javadoc 内的版本字符串更正。

## 小结

- **成效**：`ContentCache.invalidateAll()` 的弃用 Javadoc 版本号修正为 "since 1.7.0, will be removed in 2.0.0"，与实际发布节奏一致。
- **影响范围**：仅 `ContentCache.java` 一个文件、1 行 Javadoc 改动；无代码逻辑变更，对运行时无任何影响。
- **回迁到 1.4.x 的注意事项**：
  - 这是文档级别的版本号修正，安全且无副作用。
  - 与 #10494 配套回迁到 1.4.x 时，应直接采用修正后的版本号 "1.7.0/2.0.0"（与 main 对齐），不要回退到错误的 "1.6.0/1.7.0"。
  - 若 1.4.x 决定更早移除 `invalidateAll()`，需另行评估；但通常维护分支会跟随 main 的弃用节奏。
  - 若 1.4.x 中暂未回迁 #10494，则本提交也可暂不回迁（无对象可修）；建议两者一同回迁或一同不回迁。
