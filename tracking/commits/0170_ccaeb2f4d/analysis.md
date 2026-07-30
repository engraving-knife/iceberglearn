# 提交 0170：Core: Disallow setting equality field IDs for data (#8970)

## 提交信息

- **序号**：0170 / 4088
- **哈希**：ccaeb2f4d5be44b4ab177b8fb790194eff8070a5
- **短哈希**：ccaeb2f4d
- **日期**：2023-11-16
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Disallow setting equality field IDs for data (#8970)
- **PR/Issue**：#8970

## 总体目的

在 Iceberg 的 v2 表格式规范中，`equalityFieldIds`（等值删除字段 ID 列表）是 **equality delete 文件**独有的属性——它声明了某个等值删除文件按哪些字段来匹配并删除已有数据行（例如按主键删除）。这部分元数据在 manifest v2 的 `ADDITIONAL_EQUALITY_IDS` 列上承载，由 `GenericDeleteFile` 持有，并通过 `FileMetadata.deleteFileBuilder(...)` 在构造等值删除文件时设置（见 `FileMetadata.Builder.equalityFieldIds` 字段及其 `GenericDeleteFile` 构造路径）。

然而改造前，`DataFiles.Builder`（用于构造 `DataFile` 即数据文件的 builder）也暴露了一个 `withEqualityFieldIds(List<Integer>)` 方法，并把该值通过 `GenericDataFile` 全字段构造透传到 `BaseFile.equalityFieldIds` 字段。这在语义上是错误的：数据文件永远不会是等值删除文件，`equalityFieldIds` 对 `DataFile` 应当恒为 `null`。但旧 API 允许调用方给数据文件设置 equality field IDs，结果会写入 manifest 文件中数据文件条目的 `equality_ids` 列，产生与规范不符、且可能在读取端被误判为“等值删除语义”的元数据。这是一个潜在的隐患——引擎如果错误地为 `DataFile` 设置 equality field IDs，写入的 manifest 文件元数据会偏离 spec。

这个提交的目的是把“数据文件不能设置 equality field IDs”这一约束从“约定”提升为“强制”：移除 `DataFiles.Builder` 上对 equality field IDs 的实际设置能力（保留方法但抛 `UnsupportedOperationException` 并标记 `@Deprecated`，计划 1.6.0 移除），同时移除 `GenericDataFile` 全字段构造与 `ContentFileParser` 数据文件解析路径上对 `equalityFieldIds` 参数的透传，让 `BaseFile.equalityFieldIds` 在数据文件上恒为 `null`。这样既纠正了 API 语义、防止误用，又为后续把 `equalityFieldIds` 字段从 `BaseFile` 上提到仅 `DeleteFile` 体系扫清道路，是 Iceberg core 元数据模型向规范对齐的清理性改动。

## 如何达成设计目的

整体设计思路是在多个构造入口一致地把数据文件路径上的 `equalityFieldIds` “短路”为 `null`：
- `DataFiles.Builder`：删除 `equalityFieldIds` 字段及其在 `build()` 中 `ArrayUtil.toIntArray(equalityFieldIds)` 的传参；`withEqualityFieldIds(...)` 方法保留为 API 兼容垫片，但 `@Deprecated`（since 1.5.0, removed in 1.6.0）并直接抛 `UnsupportedOperationException`，从而在编译期保留兼容、运行期阻止误用。
- `GenericDataFile`：全字段构造删除 `int[] equalityFieldIds` 形参，向 `BaseFile` 透传 `null /* no equality field IDs */`。
- `ContentFileParser`：解析数据文件 JSON 时不再把 `equalityFieldIds` 传给 `GenericDataFile` 构造。
- 测试侧：`TestContentFileParser` 与 `TestManifestWriterVersions` 同步去掉对 `equality-ids`/`withEqualityFieldIds` 的引用，保持测试与新的构造签名一致。

`BaseFile` 基类本身仍保留 `equalityFieldIds` 字段（因为 `GenericDeleteFile` 仍需要它），只是数据文件侧不再赋值。这是渐进式清理：先切断数据文件这条赋值路径，把字段真正下放到 `DeleteFile` 体系可留待后续提交。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DataFiles.java`

**修改目的**：在数据文件 builder 层禁止设置 equality field IDs。

**工作逻辑**：
- 删除 `import org.apache.iceberg.util.ArrayUtil;`（不再需要把 `List<Integer>` 转为 `int[]`）。
- 删除 builder 字段 `private List<Integer> equalityFieldIds = null;`。
- `withEqualityFieldIds(List<Integer> equalityIds)` 方法体由 `if (equalityIds != null) { this.equalityFieldIds = ImmutableList.copyOf(equalityIds); } return this;` 改为 `throw new UnsupportedOperationException("Equality field IDs must not be set for data files");`，并加上 `@Deprecated` 注解和 javadoc `@deprecated since 1.5.0, will be removed in 1.6.0; must not be set for data files.`。这种“保留方法签名但抛异常 + 标记废弃”的策略是为了对存量调用方保持源码兼容（编译能过），同时在新版本里把误用变为运行期失败，给后续 1.6.0 真正删方法留出迁移窗口。
- `build()` 方法中 `new GenericDataFile(...)` 调用删除 `ArrayUtil.toIntArray(equalityFieldIds)` 这一参数，与 `GenericDataFile` 构造签名的变化对齐。

### `core/src/main/java/org/apache/iceberg/GenericDataFile.java`

**修改目的**：让 `GenericDataFile` 全字段构造不再接收 equality field IDs，固定为 null。

**工作逻辑**：构造方法形参列表删除 `int[] equalityFieldIds`，调用 `super(...)` 时把原先传入的 `equalityFieldIds` 替换为 `null /* no equality field IDs */`。`super` 调用对应 `BaseFile` 的全字段构造，该构造仍接受 `equalityFieldIds` 形参（因为 `GenericDeleteFile` 也走同一基类构造），这里显式传 `null` 即可保证数据文件上该字段恒为空。这与 spec 一致：数据文件的 manifest 条目 `equality_ids` 列必须为 null。

### `core/src/main/java/org/apache/iceberg/ContentFileParser.java`

**修改目的**：JSON 解析数据文件时不再传入 equality field IDs。

**工作逻辑**：构造 `GenericDataFile` 的参数列表中删除 `equalityFieldIds` 一项（位于 `splitOffsets` 与 `sortOrderId` 之间）。`ContentFileParser` 是把 JSON（rest catalog 序列化格式）反序列化为 `ContentFile` 的解析器，这里数据文件分支不再读 `equality-ids` 字段；与之对应，测试侧期望 JSON 也去掉 `"equality-ids":[1]`。注意 `GenericDeleteFile` 分支不受影响，等值删除文件仍可正确解析 equality field IDs。

### `core/src/test/java/org/apache/iceberg/TestContentFileParser.java`

**修改目的**：让数据文件解析测试与新解析行为一致。

**工作逻辑**：
- 删除 `import java.util.Collections;`（不再用 `Collections.singletonList(1)`）。
- 数据文件期望 JSON 字符串中删除 `"equality-ids":[1],` 这一段（两处：v1/v2 分支各一处，均为把 `"split-offsets":[128,256],"equality-ids":[1],"sort-order-id":1}` 改为 `"split-offsets":[128,256],"sort-order-id":1}`）。
- 构造期望 `DataFile` 时删除 `.withEqualityFieldIds(Collections.singletonList(1))` 这一行，避免触发新抛出的 `UnsupportedOperationException`。这同时验证了“数据文件 JSON 不再含 equality-ids、builder 不再允许设置 equality field IDs”两个方向的契约。

### `core/src/test/java/org/apache/iceberg/TestManifestWriterVersions.java`

**修改目的**：同步 `GenericDataFile` 构造签名的变化。

**工作逻辑**：测试用静态常量 `DATA_FILE = new GenericDataFile(0, PATH, FORMAT, PARTITION, 150972L, METRICS, null, OFFSETS, null, SORT_ORDER_ID);` 中删除 `null`（原对应 `equalityFieldIds` 形参），改为 `new GenericDataFile(0, PATH, FORMAT, PARTITION, 150972L, METRICS, null, OFFSETS, SORT_ORDER_ID);`。这只是构造签名收紧后的机械调整，不改变测试语义（原本就传 `null`）。

## 小结

这个提交通过让 `DataFiles.Builder.withEqualityFieldIds(...)` 改为抛 `UnsupportedOperationException` 并 `@Deprecated`、移除 `GenericDataFile` 构造与 `ContentFileParser` 解析路径上对 equality field IDs 的透传，把“数据文件不应有 equality field IDs”这一规范要求从约定提升为强制约束，纠正了 API 语义并防止误写入与 spec 不符的 manifest 元数据，为后续把 `equalityFieldIds` 字段从 `BaseFile` 收敛到 `DeleteFile` 体系奠定了基础。
