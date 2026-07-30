# 提交 1648：Core: Check referencedDataFile existence for DV (#12088)

## 提交信息

- **序号**：1648 / 4088
- **哈希**：491d9068bc23dec8bc54a16532e2f61398db7779
- **短哈希**：491d9068b
- **日期**：2025-01-28（Wed Jan 29 00:28:25 2025 +0900）
- **作者**：Yuya Ebihara <ebyhry@gmail.com>
- **提交说明**：Core: Check referencedDataFile existence for DV (#12088)
- **PR/Issue**：#12088

## 总体目的

DV（deletion vector）是 Iceberg format v3 中存储在 puffin 文件内的删除向量 blob。一个 DV 通过 `referencedDataFile` 字段明确指向它所删除的目标数据文件——这是 DV 区别于普通位置删除文件的关键属性，使得读取端可以精确地把 DV 应用到对应数据文件，也使得 `ContentFileUtil.isFileScoped` 等逻辑能据此判断删除文件是否为文件级作用域。

`FileMetadata.Builder` 是构造 `DeleteFile` 元数据（包括 DV）的构建器。当 `format == PUFFIN`（即 DV）时，构建器已校验 `contentOffset` 与 `contentSizeInBytes` 必填（DV blob 在 puffin 文件中的定位信息），但**漏校验了 `referencedDataFile`**。这意味着可以构造出一个格式为 puffin、有 offset/size、却缺少 `referencedDataFile` 的"残缺 DV"——这种 DV 在读取时无法定位目标数据文件，会导致 `ContentFileUtil.referencedDataFile` 返回 null、`isFileScoped` 误判为 false，进而引发后续 delete 应用、重写（如提交 1628 的删除比例计算）等逻辑的错误行为。

本提交在 DV 构建校验中补上 `referencedDataFile != null` 的断言，从源头杜绝残缺 DV 的产生。

## 如何达成设计目的

在 `FileMetadata.Builder` 的 `build` 路径中，puffin 格式分支已有 `contentOffset` 与 `contentSizeInBytes` 的必填校验，紧接其后新增 `Preconditions.checkArgument(referencedDataFile != null, "Referenced data file is required for DV")`。这是与既有校验风格一致的最小修复——在同一条件块内补齐第三个必填字段的检查。

## 修改详情

### `core/src/main/java/org/apache/iceberg/FileMetadata.java`（修改，+2）

**修改目的**：DV 构建时强制校验 `referencedDataFile` 必填。

**工作逻辑**：在 `if (format == FileFormat.PUFFIN)` 分支内，原两句 `Preconditions.checkArgument`（contentOffset、contentSizeInBytes）之后新增：

```java
Preconditions.checkArgument(
    referencedDataFile != null, "Referenced data file is required for DV");
```

该校验与既有的 `contentOffset`/`contentSizeInBytes` 校验同处一个分支，仅对 puffin（DV）生效；非 puffin 格式的 else 分支维持原有"这些字段只能用于 DV"的反向校验不变。

### `core/src/test/java/org/apache/iceberg/TestDeleteFiles.java`（修改，+26）

**修改目的**：验证 DV 三个必填字段的校验顺序与错误信息。

**工作逻辑**：新增 `@Test testRequiredFieldsForDV`：构造一个 puffin 格式的位置删除文件 builder，设置 path/size/recordCount 但不设必填字段，逐步补字段并逐次断言 `build()` 抛 `IllegalArgumentException`：

1. 初始（缺 contentOffset/contentSizeInBytes/referencedDataFile）→ 抛 "Content offset is required for DV"；
2. `withContentOffset(1)` 后（缺 contentSizeInBytes/referencedDataFile）→ 抛 "Content size is required for DV"；
3. `withContentSizeInBytes(10)` 后（缺 referencedDataFile）→ 抛 "Referenced data file is required for DV"。

测试覆盖了校验顺序，确认新校验在 contentOffset/contentSizeInBytes 之后触发。新增 `import org.junit.jupiter.api.Test`（原类用 `@TestTemplate`）。

## 小结

- **成效**：补齐 DV 构建时 `referencedDataFile` 必填校验，防止产生缺少目标数据文件引用的残缺 DV，避免后续 `ContentFileUtil.isFileScoped`/`referencedDataFile` 等逻辑误判。
- **影响范围**：仅 `FileMetadata.Builder.build` 的 puffin 分支增加一个断言，影响所有 DV 写入路径。对合规的 DV（已有 referencedDataFile）无影响；对遗漏该字段的写入会提前抛 `IllegalArgumentException` 而非生成残缺文件。
- **回迁到 1.4.x 的注意事项**：纯防御性校验增强，回迁安全。依赖 1.4.x 已有 DV/puffin 支持（`FileFormat.PUFFIN`、`referencedDataFile` 字段、`withContentOffset`/`withContentSizeInBytes` 方法）。回迁后若有第三方测试构造 puffin DV 但未设 referencedDataFile，会因新校验失败，需修正测试数据。建议与提交 1628（依赖 `isFileScoped` 的删除比例重写）一并回迁，因为本提交修复的正是 1628 所依赖的 `referencedDataFile` 不变量。
