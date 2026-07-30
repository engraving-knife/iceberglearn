# 提交 1163：Core: Move internal struct projection to SupportsIndexProjection (#11132)

## 提交信息

- **序号**：1163 / 4088
- **哈希**：bbeadea75e2a5e6e9f2472960b76daaba42a4577
- **短哈希**：bbeadea75
- **日期**：2024-09-18（Wed Sep 18 08:18:41 2024 -0700）
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Core: Move internal struct projection to SupportsIndexProjection (#11132)
- **PR/Issue**：#11132

## 总体目的

Iceberg 在读取 manifest 文件时，会按照 Avro schema 创建 `BaseFile`（`GenericDataFile`/`GenericDeleteFile` 的父类）和 `GenericManifestFile` 实例。这些类需要在「投影 schema」（实际读到的字段集合）与「完整 schema」（类内部 switch case 的位置约定）之间做字段位置映射：Avro 写入端的字段顺序与类内部按 `case 0..N` 的硬编码顺序可能不一致，因此维护一张 `int[] fromProjectionPos` 映射表，在 `get`/`set`/`put` 时把外部传入的位置映射到内部位置。

重构前，这张映射表的构建与维护逻辑在 `BaseFile` 与 `GenericManifestFile` 中各写了一份，存在以下问题：

1. **重复代码**：两处都按 `fields.get(i).fieldId() == allFields.get(j).fieldId()` 的双重循环构建映射，并在找不到时抛 `IllegalArgumentException`，逻辑完全重复。
2. **耦合分散**：投影映射逻辑与业务字段读写逻辑（`get`/`set` 的 switch case）混在一个类里，不利于复用与扩展。
3. **接口不一致**：`BaseFile` 与 `GenericManifestFile` 各自实现 `StructLike` 的 `get`/`set`，且都暴露 `get(int)`、`get(int, Class)`、`set(int, T)`、`put(int, Object)` 多套入口，调用关系盘根错节（`set` 调 `put`，`put` 又调 `set`），容易出错。
4. **GenericManifestEntry 构造签名歧义**：原先 `GenericManifestEntry(Types.StructType partitionType)` 实际上把它当作「分区类型」并内部转换成 v1 entry schema，调用方很容易误传其他类型；同时只支持 v1 schema，无法支持 v2/v3 manifest entry。

本提交抽出公共基类 `SupportsIndexProjection`，把投影映射逻辑统一起来；并修正 `GenericManifestEntry` 构造签名，使其直接接收投影 schema（Iceberg `Schema` 类型）而非含糊的分区类型；同时改造 `TestBase` 让其在创建 `GenericManifestEntry` 时按表 format 版本选择对应的 entry schema。

## 如何达成设计目的

1. **新增抽象基类 `SupportsIndexProjection`**：实现 `StructLike`，内部维护 `int[] fromProjectionPos`，提供三个构造函数（按大小构造恒等映射、按 `baseType`+`projectionType` 构建映射、复制构造），并声明 `internalGet`/`internalSet` 两个抽象方法。`StructLike.get`/`set` 在基类中通过 `pos(basePos)` 映射后转发给 `internalGet`/`internalSet`，把投影映射与业务读写彻底解耦。
2. **改造 `BaseFile`**：继承 `SupportsIndexProjection`；删除自己的 `fromProjectionPos` 字段与构造映射逻辑；新增静态 `BASE_TYPE` 描述内部位置布局；把原来的 `get(int)` switch 改名为 `getByPos(int)` 并由 `internalGet` 调用；把 `put(int, Object)` 的 switch 改名为 `internalSet(int, T)` 并由 `set(int, T)`（基类转发）调用；原 `get(int, Class)` / `set(int, T)` / `put(int, Object)` 三个入口收敛到基类统一入口；构造函数全部改为通过 `super(...)` 完成映射初始化。
3. **改造 `GenericManifestFile`**：同样继承 `SupportsIndexProjection`，删除自己的 `fromProjectionPos` 与映射构建逻辑；构造函数通过 `super(ManifestFile.schema().asStruct(), AvroSchemaUtil.convert(avroSchema).asStructType())` 或 `super(ManifestFile.schema().columns().size())` 完成初始化；`get`/`set` switch 改名为 `getByPos`/`internalSet`，原 `get(int)` 直接转调 `internalGet(pos, Object.class)`。
4. **改造 `GenericManifestEntry`**：构造参数从 `Types.StructType partitionType` 改为 `Types.StructType schema`（实为 entry 的 struct 类型），并直接通过 `AvroSchemaUtil.convert(schema, "manifest_entry")` 转换为 Avro schema，不再硬编码使用 `V1Metadata.entrySchema`。这样既修正了语义，也为 v2/v3 metadata 留出空间。
5. **改造 `ManifestWriter`**：调用方相应改为 `new GenericManifestEntry<>(V1Metadata.entrySchema(spec.partitionType()).asStruct())`，确保 v1 写入路径行为不变。
6. **改造 `TestBase`**：在测试辅助方法 `newAppendFile` 等处根据表的 `formatVersion` 选择 `V1Metadata`/`V2Metadata`/`V3Metadata` 的 `entrySchema`，再经 `AvroSchemaUtil.convert(...)` 转换后传入 `GenericManifestEntry`，使测试覆盖 v2/v3 manifest entry 路径。
7. **为 `GenericDataFile`/`GenericDeleteFile` 新增投影构造函数**：`GenericDataFile(Types.StructType projection)` 与 `GenericDeleteFile(Types.StructType projection)` 直接转调 `super(projection)`，让内部 reader 能以纯投影 schema 构造实例（与已有的 `Schema avroSchema` 构造函数解耦）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/avro/SupportsIndexProjection.java`（新增）

**修改目的**：抽出公共的「按字段 ID 做位置投影」基类。

**工作逻辑**：
- `private final int[] fromProjectionPos`：维护「投影位置 → 内部位置」的映射。
- `SupportsIndexProjection(int size)`：构建恒等映射 `fromProjectionPos[i] = i`，用于不投影（即完整 schema）的场景。
- `SupportsIndexProjection(Types.StructType baseType, Types.StructType projectionType)`：核心构造函数。遍历 `projectionType.fields()`，对每个投影字段在 `baseType.fields()` 中按 `fieldId()` 找到对应位置，填入 `fromProjectionPos[i]`；若找不到则抛 `IllegalArgumentException("Cannot find projected field: ...")`。
- `SupportsIndexProjection(SupportsIndexProjection toCopy)`：拷贝构造，直接复用 `toCopy.fromProjectionPos`（数组引用共享，因不可变）。
- 抽象方法 `internalGet(int pos, Class<T>)` 与 `internalSet(int pos, T value)`：子类实现具体业务读写。
- `pos(int basePos)`：把外部位置映射到内部位置。
- `size()`：返回 `fromProjectionPos.length`，即投影后的字段数。
- `get(int basePos, Class<T>)`：调用 `internalGet(pos(basePos), javaClass)`。
- `set(int basePos, T value)`：调用 `internalSet(pos(basePos), value)`。

### `core/src/main/java/org/apache/iceberg/BaseFile.java`

**修改目的**：把投影映射交给基类管理，简化 `get`/`set`/`put` 入口。

**工作逻辑**：
- 类签名改为 `abstract class BaseFile<F> extends SupportsIndexProjection implements ...`。
- 新增静态字段 `BASE_TYPE`：以 `DataFile.CONTENT`、`FILE_PATH`、…、`MetadataColumns.ROW_POSITION` 顺序拼出 `Types.StructType`，描述类内部 switch case 的位置布局。
- 构造函数：
  - `BaseFile(Schema avroSchema)` 改为转调 `this(AvroSchemaUtil.convert(avroSchema).asStructType())`，再赋 `avroSchema`。
  - 新增 `BaseFile(Types.StructType projection)`：调用 `super(BASE_TYPE, projection)` 完成投影映射；并据此推导 `partitionType`；删除原双重循环映射逻辑；最后构造 `partitionData`。`avroSchema` 由 `AvroSchemaUtil.convert(projection, "data_file")` 反推得到。
  - 字段全构造函数 `BaseFile(int specId, ...)` 增加 `super(BASE_TYPE.fields().size())` 调用（恒等映射）。
  - 拷贝构造 `BaseFile(BaseFile<F> toCopy, boolean copyStats, Set<Integer> requestedColumnIds)` 改为 `super(toCopy)`，并删除 `this.fromProjectionPos = toCopy.fromProjectionPos;`。
  - 序列化空构造 `BaseFile()` 改为 `super(BASE_TYPE.fields().size())`，避免反序列化后 `fromProjectionPos` 为 null。
- 方法：
  - `put(int i, Object value)` 改为转调 `set(i, value)`（基类 `set`）。
  - 原 `put` 的 switch 改名为 `internalSet(int pos, T value)`，由基类 `set` 在做完位置映射后调用。
  - 原 `get(int)` switch 改名为 `private Object getByPos(int basePos)`，由新方法 `internalGet(int pos, Class<T>)` 调用。
  - 新增 `public Object get(int pos)` 直接转调 `get(pos, Object.class)`；原 `get(int, Class)` 转调 `internalGet(pos(basePos), javaClass)`（由基类完成，故子类不再需要实现）。
- 删除原 `fromProjectionPos` 字段及在 `get`/`put` 中所有「if (fromProjectionPos != null) pos = fromProjectionPos[i];」分支，因为基类 `pos()` 已统一处理。

### `core/src/main/java/org/apache/iceberg/GenericManifestFile.java`

**修改目的**：同样改造为继承 `SupportsIndexProjection`，去除重复映射代码。

**工作逻辑**：
- 类签名改为 `public class GenericManifestFile extends SupportsIndexProjection implements ...`。
- 删除 `private int[] fromProjectionPos;`。
- `GenericManifestFile(Schema avroSchema)`：改为 `super(ManifestFile.schema().asStruct(), AvroSchemaUtil.convert(avroSchema).asStructType())`，删除原本 25 行左右的双重循环映射代码。
- 其他构造函数（`GenericManifestFile(InputFile, int)`、`GenericManifestFile(InputFile, int, ...)`、`GenericManifestFile(String, ...)`、私有拷贝构造、空构造）全部改为通过 `super(ManifestFile.schema().columns().size())` 或 `super(toCopy)` 初始化，并删除 `this.fromProjectionPos = null;` 等行。
- `get(int pos, Class)` 改为转调 `internalGet`；原 `get(int)` switch 改名为 `private Object getByPos(int basePos)`，由 `internalGet` 调用。
- 原 `set(int, T)` switch 改名为 `internalSet(int basePos, T value)`，由基类 `set` 转发。
- 删除 `get(int)` 中 `if (fromProjectionPos != null) pos = fromProjectionPos[i];` 分支。
- 移除 `import org.apache.iceberg.types.Types;`（不再直接使用），新增 `import org.apache.iceberg.avro.SupportsIndexProjection;`。

### `core/src/main/java/org/apache/iceberg/GenericManifestEntry.java`

**修改目的**：修正构造签名歧义，使其直接接收 entry schema 的 struct 类型。

**工作逻辑**：
- 原 `GenericManifestEntry(Types.StructType partitionType)` 改为 `GenericManifestEntry(Types.StructType schema)`，方法体内不再调用 `V1Metadata.entrySchema(partitionType)`，而是直接 `AvroSchemaUtil.convert(schema, "manifest_entry")`。
- 该改动使得 `GenericManifestEntry` 既能接受 v1 entry schema，也能接受 v2/v3 entry schema，对应不同 format 版本的 manifest 文件。

### `core/src/main/java/org/apache/iceberg/ManifestWriter.java`

**修改目的**：适配 `GenericManifestEntry` 构造签名变化。

**工作逻辑**：
- 把 `this.reused = new GenericManifestEntry<>(spec.partitionType());` 改为：
  ```java
  this.reused =
      new GenericManifestEntry<>(V1Metadata.entrySchema(spec.partitionType()).asStruct());
  ```
- 即手动调用 `V1Metadata.entrySchema(...)` 拿到 entry schema，再 `.asStruct()` 转为 struct 类型传入。`ManifestWriter` 仅写入 v1 manifest（v2/v3 在其他写入器中处理），所以仍使用 `V1Metadata`。

### `core/src/main/java/org/apache/iceberg/GenericDataFile.java`、`GenericDeleteFile.java`

**修改目的**：为内部 reader 新增直接以投影 struct 类型构造的入口。

**工作逻辑**：各新增一个构造函数：
```java
GenericDataFile(Types.StructType projection) {
  super(projection);
}
```
`GenericDeleteFile` 同。这对应 `BaseFile(Types.StructType projection)` 新构造函数，让 reader 在已有 struct 类型时无需先转 Avro schema 再转回来。

### `core/src/test/java/org/apache/iceberg/TestBase.java`

**修改目的**：测试基础设施按 format 版本生成正确的 manifest entry schema。

**工作逻辑**：在创建 `GenericManifestEntry` 的位置改为按 `table.ops().current().formatVersion()` 分支选择 `V1Metadata`/`V2Metadata`/`V3Metadata` 的 `entrySchema(table.spec().partitionType())`，再 `AvroSchemaUtil.convert(manifestEntrySchema, "manifest_entry")` 后传入 `GenericManifestEntry`。未知版本抛 `IllegalArgumentException`。新增 `import org.apache.iceberg.avro.AvroSchemaUtil;`。

这一改动让测试覆盖 v2/v3 表的 manifest entry 路径，提前发现版本相关问题。

## 小结

- **成效**：投影位置映射逻辑被收敛到 `SupportsIndexProjection` 单一基类，`BaseFile` 与 `GenericManifestFile` 不再重复实现；`get`/`set`/`put` 入口收敛到 `StructLike.get`/`set` + 子类 `internalGet`/`internalSet` 的两层结构，调用关系清晰；`GenericManifestEntry` 构造签名修正为直接接收 entry schema struct，支持 v2/v3 manifest；测试基础设施按表 format 版本生成 entry schema，扩大覆盖面。
- **影响范围**：core 模块 8 个文件，新增 85 行基类、删除约 94 行重复代码。属于内部重构，对外接口行为（`ContentFile`/`ManifestFile`/`StructLike` 公共 API）保持不变。
- **回迁到 1.4.x 的注意事项**：
  - 这是 main 分支为后续 v3 metadata 支持做的内部重构铺垫。1.4.x 已发布版本通常不会引入 v3 metadata 支持，**回迁必要性较低**。
  - 若 1.4.x 出现 manifest 投影相关的 bug（如字段顺序错乱、`Unknown field ordinal` 异常），可考虑回迁本提交以获得更稳健的投影机制；但需连同 `GenericManifestEntry` 签名变化、`ManifestWriter` 调用变化、`TestBase` 测试改造一并回迁，否则会编译失败。
  - 注意 `GenericManifestEntry` 构造签名从 `partitionType` 改为 `schema` 是不兼容变更，回迁时所有调用方（包括 1.4.x 自己的子项目测试代码）都需同步修改。
  - 建议默认不回迁，除非有明确的投影相关 bug 需要修复。
