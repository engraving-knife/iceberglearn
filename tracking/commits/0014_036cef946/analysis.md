# 提交 0014：Spec: Inconsistency around files_count (#5338)

## 提交信息

- **序号**：0014 / 4088
- **哈希**：036cef946eb0f006a3d8c2e8397bf3c149d9ac0c
- **短哈希**：036cef949
- **日期**：2023-10-05 21:51:37 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Spec: Inconsistency around files_count (#5338)
- **PR/Issue**：#5338

## 总体目的

这个提交修复了 Iceberg 规范中 manifest 文件计数字段命名的不一致问题。在 Iceberg 的 manifest list（清单列表）中，每个 manifest 文件都附带若干统计字段，用于在不扫描整个 manifest 的情况下快速做扫描规划。这些统计字段包括：增加的文件数、已存在文件数、删除文件数，以及对应的行数统计。

问题在于：行数统计字段的命名是 `added_rows_count`、`existing_rows_count`、`deleted_rows_count`（不含 `data`），而文件数统计字段的命名却是 `added_data_files_count`、`existing_data_files_count`、`deleted_data_files_count`（含 `data`）。这两组字段描述的是同类信息（都是针对 manifest 中追踪的文件，v1 中只有 data files，v2 中可以是 data 或 delete files），却采用了不一致的命名风格——文件数带 `data` 限定词，行数却不带。这种不一致既增加了规范阅读与实现的认知负担，也潜在暗示文件计数字段只针对 data files，而实际上 v2 manifest 可能同时追踪 delete files，`data` 限定词在语义上已不精确。

Issue #5338 正是提出了这一不一致。本提交将三个文件计数字段重命名为 `added_files_count`、`existing_files_count`、`deleted_files_count`（去掉 `data`），使它们与行数统计字段（`*_rows_count`）的命名风格一致，并更准确地反映字段语义（涵盖所有被追踪的文件，而非仅 data files）。这是规范一致性维护的改动，同时同步修改 Java API 中的字段常量定义与对应测试，确保实现与规范一致。

## 如何达成设计目的

整体设计思路是把字段重命名落实到 Java API 的字段常量定义（`ManifestFile` 中的 `Types.NestedField` 常量），并同步更新测试代码中所有引用旧字段名的位置。由于 Iceberg 的字段是通过 field id（504/505/506）而非字段名来标识和序列化的，因此重命名字段名不会破坏已有元数据文件的兼容性——已写入的 manifest list 仍以 field id 读取，字段名的变更只影响人类可读的 schema 定义与基于字段名访问的测试。改动集中在 `ManifestFile.java`（常量定义）与 `TestManifestListVersions.java`（测试）两个文件。

## 修改详情

### `api/src/main/java/org/apache/iceberg/ManifestFile.java`

**修改目的**：将三个文件计数字段常量的名称从 `*_data_files_count` 改为 `*_files_count`，与规范及行数字段命名风格对齐。

**工作逻辑**：修改三处 `Types.NestedField` 常量定义，field id 与类型保持不变，仅改名字符串：
- `ADDED_FILES_COUNT`：`optional(504, "added_data_files_count", ...)` → `optional(504, "added_files_count", ...)`
- `EXISTING_FILES_COUNT`：`optional(505, "existing_data_files_count", ...)` → `optional(505, "existing_files_count", ...)`
- `DELETED_FILES_COUNT`：`optional(506, "deleted_data_files_count", ...)` → `optional(506, "deleted_files_count", ...)`

常量名本身（`ADDED_FILES_COUNT` 等）原本就不含 `data`，所以 Java 代码中通过常量名引用的地方无需改动，只有依赖字段名字符串的地方（如测试中按名取值）需要同步更新。field id（504/505/506）保持不变，保证序列化兼容性。

### `core/src/test/java/org/apache/iceberg/TestManifestListVersions.java`

**修改目的**：将测试中所有引用旧字段名字符串的位置改为新字段名，与 `ManifestFile` 常量定义变更对齐。

**工作逻辑**：测试中有多处通过字段名字符串从 generic record 中取值或构造 record，需同步改名。共涉及四个测试方法中的若干处：
1. 两处断言方法中，`generic.get("added_data_files_count")` → `generic.get("added_files_count")`，`existing_data_files_count` 与 `deleted_data_files_count` 同理改名。这些断言验证从 manifest list 读取的 generic record 中能按字段名取到正确的计数值。
2. `columnNamesWithoutRowStats` 列表（用于 `select` 投影）中，三个字符串元素从 `*_data_files_count` 改为 `*_files_count`，保证投影时按新字段名选取列。
3. 构造 withoutRowStats 测试数据的 `Record` 中，`.set("added_data_files_count", 2)` 等三处改为 `.set("added_files_count", 2)` 等，保证测试输入数据使用新字段名写入。

改动净减少 2 行（部分断言合并），属于机械式重命名同步，不改变测试逻辑。

## 小结

本提交通过将 manifest 文件计数字段从 `*_data_files_count` 重命名为 `*_files_count`，消除了规范中文件计数与行数统计字段命名风格的不一致，并使字段名更准确地覆盖 data 与 delete 文件，提升了 Iceberg 规范的一致性与可读性。
