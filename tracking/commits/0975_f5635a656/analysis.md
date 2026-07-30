# 提交 0975：API: Fix typo in RewriteManifestFiles java doc (#10778)

## 提交信息

- **序号**：0975 / 4088
- **哈希**：f5635a65676df12b00f2c8365357d6026202b397
- **短哈希**：f5635a656
- **日期**：2024-07-25 09:02:56 +0200
- **作者**：Amogh Jahagirdar
- **提交说明**：API: Fix typo in RewriteManifestFiles java doc (#10778)
- **PR/Issue**：#10778

## 总体目的

`RewriteManifests` 接口（位于 `api` 模块）定义了重写清单文件（manifest）的动作 API，其中 `rewriteIf(Predicate<ManifestFile>)` 方法的 Javadoc 用于说明该谓词的作用：当谓词返回 true 时对应的 manifest 会被纳入重写，返回 false 时则保持原样。

该 Javadoc 中存在一处拼写错误：将 "If false then then manifest is kept as-is." 中的 "then" 重复了一次（"then then"）。本提交修正这一重复词，恢复为 "If false then the manifest is kept as-is."。同时注意到原句中 "then manifest" 也在修正中改为 "then the manifest"（补充定冠词 the），使语句更通顺。

## 如何达成设计目的

直接修改 `api/src/main/java/org/apache/iceberg/RewriteManifests.java` 中 `rewriteIf` 方法 Javadoc 的一行文本，将重复的 "then then" 改为 "then the"。无任何代码逻辑变更。

## 修改详情

### `api/src/main/java/org/apache/iceberg/RewriteManifests.java`

**修改目的**：修正 `rewriteIf` 方法 Javadoc 中的重复词拼写错误。

**工作逻辑**：单行修改，diff 如下：

```diff
-   *     manifest file will be included for rewrite. If false then then manifest is kept as-is.
+   *     manifest file will be included for rewrite. If false then the manifest is kept as-is.
```

将 "If false then then manifest" 改为 "If false then the manifest"，去除多余的 "then" 并补上定冠词 "the"。

## 小结

- **成效**：修正了 `RewriteManifests.rewriteIf` Javadoc 中的拼写错误，提升文档准确性。
- **影响范围**：仅 `api` 模块的一个 Javadoc 注释，1 行改动，无任何代码或行为影响。
- **回迁到 1.4.x 的注意事项**：纯文档拼写修正，无风险，可安全回迁到 1.4.x。但此类小修正常规上无需回迁，除非 1.4.x 分支特别关注文档质量。
