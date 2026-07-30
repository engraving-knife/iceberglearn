# 提交 0041：Core: Support view metadata compression (#8552)

## 提交信息

- **序号**：0041 / 4088
- **哈希**：b7e5d685c00a0b57d7b363146e1c15546a1ac1d1
- **短哈希**：b7e5d685c
- **日期**：2023-10-11
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Support view metadata compression (#8552)
- **PR/Issue**：#8552

## 总体目的

该提交为 Iceberg 的 View（视图）元数据引入压缩支持。在此之前，View 的元数据文件（`vN-uuid.metadata.json`）以纯 JSON 文本形式落盘，与 Table 元数据已有的压缩能力不对称。Table 元数据早已通过 [TableMetadataParser](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/TableMetadataParser.java) 支持 `none`/`gzip` 两种 codec，并通过 `write.metadata.compression-codec` 属性控制；本提交将相同机制对齐到 View 侧。

引入此能力的动机主要有三：(1) View 元数据随版本历史增长可能逐步变大，启用 gzip 可显著降低存储占用；(2) 在对象存储上，读取压缩元数据可减少 IO 字节数，对元数据加载延迟有正向作用；(3) 与 Table 元数据的行为保持一致，降低用户认知与运维成本。

值得注意的是，该提交把 View 元数据的默认压缩 codec 直接设为 `gzip`（而非 `none`），这意味着新建视图的元数据默认就是压缩的。这是与历史行为的一个变化——之前所有 view metadata 文件均为未压缩的 `.metadata.json`。读取侧通过文件名后缀自动判别 codec，因此对老元数据（无 `.gz` 后缀）保持向后兼容。

## 如何达成设计目的

整体设计复用了 Table 元数据压缩的成熟模式：在 [BaseViewOperations](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/view/BaseViewOperations.java) 生成新元数据文件路径时，根据视图属性的 codec 名查表得到文件扩展名；在 [ViewMetadataParser](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/view/ViewMetadataParser.java) 读写时，根据文件名后缀决定是否包装 `GZIPInputStream`/`GZIPOutputStream`。新增 [ViewProperties](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/view/ViewProperties.java) 中的 `METADATA_COMPRESSION` 常量与默认值，使压缩行为可由视图属性配置。整个改动 4 个文件、约 88 行新增、3 行删除，结构紧凑且最小化扩散。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewProperties.java`

**修改目的**：新增 view 元数据压缩相关属性常量。

**工作逻辑**：新增两个公开常量：`METADATA_COMPRESSION = "write.metadata.compression-codec"` 与 `METADATA_COMPRESSION_DEFAULT = "gzip"`。属性键名与 Table 侧完全一致（Table 的同名属性即 `write.metadata.compression-codec`），默认值同样取 `gzip`，确保两侧行为对齐。这一选择也意味着该提交合并后，新写入的 View 元数据默认走 gzip，除非调用方显式把属性覆盖为 `none`。

### `core/src/main/java/org/apache/iceberg/view/BaseViewOperations.java`

**修改目的**：在生成新元数据文件路径时根据压缩 codec 决定文件扩展名。

**工作逻辑**：`newMetadataFilePath(ViewMetadata, int)` 之前硬编码使用 `String.format("%05d-%s%s", newVersion, UUID.randomUUID(), ".metadata.json")`，扩展名固定为 `.metadata.json`。修改后改为：先从 `metadata.properties()` 用 `getOrDefault(METADATA_COMPRESSION, METADATA_COMPRESSION_DEFAULT)` 取 codec 名，再调用 `TableMetadataParser.getFileExtension(codecName)` 得到对应的文件扩展名（gzip 对应 `.gz.metadata.json`，none 对应 `.metadata.json`），最后用该扩展名拼接路径。同时新增了对 `TableMetadataParser` 的 import。这一改动直接复用了 Table 侧已有的 codec→扩展名映射逻辑，避免在 View 模块重复实现。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadataParser.java`

**修改目的**：在 read/write 路径上根据文件名 codec 包装 GZIP 流。

**工作逻辑**：分两处。

读路径 `read(InputFile)`：原实现直接 `try (InputStream is = file.newStream())`。修改后先用 `Codec codec = Codec.fromFileName(file.location())` 从文件名判别 codec，再根据 `codec == Codec.GZIP` 决定是否把原始流包装为 `GZIPInputStream`。`Codec.fromFileName` 沿用 Table 元数据的判别逻辑（通过文件名是否含 `.gz.` 判定），保证对历史未压缩文件名（无 `.gz.`）也能正常读取。

写路径 `internalWrite(ViewMetadata, OutputFile, boolean)`：原实现直接用 `outputFile.create()/createOrOverwrite()` 拿到的流构造 `OutputStreamWriter`。修改后先 `boolean isGzip = Codec.fromFileName(outputFile.location()) == Codec.GZIP` 判别，再在 `isGzip` 为真时把流包装为 `GZIPOutputStream`，否则使用原始流。这样写入的文件即为 gzip 压缩格式。

这里值得强调一个设计细节：codec 的判别统一从"文件名"出发，而非从"视图属性"出发。属性仅参与生成文件路径（决定后缀），一旦路径确定，后续读写均依据文件名解析。这与 Table 侧的实现方式一致，避免了"属性变了但文件没动"等不一致场景，也天然支持老版本未压缩文件的读取（向后兼容）。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadataParser.java`

**修改目的**：为压缩能力新增参数化测试，覆盖 none 与 gzip 两种 codec。

**工作逻辑**：新增 `@TempDir Path tmp` 临时目录与 `metadataCompression(String fileName)` 参数化测试，参数为 `"v1.metadata.json"` 与 `"v1.gz.metadata.json"`。测试逻辑：根据文件名前缀推断期望 codec（`v1.gz` → `GZIP`，否则 `NONE`），构造 `localOutput(location)` 与一份 `ViewMetadata`（其中显式将 `ViewProperties.METADATA_COMPRESSION` 设为 `codec.name()`），调用 `ViewMetadataParser.write` 写出，然后用辅助方法 `isCompressed(path)` 验证物理文件是否真为 gzip——该方法尝试用 `GZIPInputStream` 打开文件，若抛出 `ZipException("Not in GZIP format")` 即判定为未压缩。随后用 `ViewMetadataParser.read` 读回并断言内容等价（`usingRecursiveComparison().ignoringFieldsOfTypes(Schema.class)`）。这一测试同时验证了：(1) codec 属性确实能驱动文件格式；(2) 写入并读回的内容一致；(3) 历史未压缩格式仍可被读取（none 分支）。

## 小结

该提交为 View 元数据补齐了 Table 侧早已具备的 gzip 压缩能力，并默认启用，使 View 元数据在存储与读取上更省、更一致，同时通过文件名驱动的 codec 判别保持了对旧版未压缩元数据的向后兼容。
