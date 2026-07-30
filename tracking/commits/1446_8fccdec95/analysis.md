# 提交 1446：Core,API: Set `503: added_snapshot_id` as required (#11626)

## 提交信息

- **序号**：1446
- **哈希**：8fccdec9578edefc14af9563908fe37e645a2d04
- **短哈希**：8fccdec95
- **日期**：2024-11-28（Thu Nov 28 23:28:34 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Core,API: Set `503: added_snapshot_id` as required (#11626)
- **PR/Issue**：#11626

## 总体目的

Iceberg 规范中，`ManifestFile` 的字段 `503: added_snapshot_id`（即 `SNAPSHOT_ID`）应当是 **required** 字段——每个 manifest 文件都必定关联到某个添加它的 snapshot。然而 Java 参考实现中，`api/src/main/java/org/apache/iceberg/ManifestFile.java` 里该字段的定义却是 `optional(503, "added_snapshot_id", ...)`，与规范不一致。

此前为了在 `V2Metadata`/`V3Metadata` 构建 manifest list Avro schema 时让该字段表现为 required，代码在引用处显式调用 `ManifestFile.SNAPSHOT_ID.asRequired()` 来"强制升级"为 required。这是一种 workaround：基础定义是 optional，但在 manifest list schema 里临时升为 required。问题在于：

1. 参考实现应当尽量贴近规范——基础定义本身就该是 required。
2. 其他直接使用 `ManifestFile.SNAPSHOT_ID`（不带 `.asRequired()`）的地方会拿到 optional 字段，写出与规范不符的元数据。
3. 当 `added_snapshot_id` 变为 required 后，任何写 manifest list 的路径都必须保证 `snapshotId` 非空。但 `BaseSnapshot` 在解析 v1 内嵌 manifest 路径（`v1ManifestLocations`）时，用 `new GenericManifestFile(fileIO.newInputFile(location), 0)` 构造 `GenericManifestFile`，该构造器把 `snapshotId` 设为 `null`——如果该 manifest 后续被重新写出（例如表元数据重写），就会产生一个 required 字段为 null 的非法 manifest 元数据。

本提交做两件事：

1. **修正基础定义**：把 `ManifestFile.SNAPSHOT_ID` 从 `optional(...)` 改为 `required(...)`，让参考实现的字段定义与规范一致。同时移除 `V2Metadata`/`V3Metadata` 中冗余的 `.asRequired()` 调用（因为基础定义已经是 required）。
2. **保证 v1 manifest 路径下 snapshotId 非空**：在 `GenericManifestFile` 中新增一个三参构造器 `GenericManifestFile(InputFile file, int specId, long snapshotId)`，让调用方显式传入 snapshotId；`BaseSnapshot` 解析 v1 内嵌 manifest 时改用该构造器并传入 `this.snapshotId`。这样 v1 manifest 重新写出时 `added_snapshot_id` 不会是 null。
3. **拷贝构造器容错**：`GenericManifestFile` 的拷贝构造器原先直接复制 `toCopy.length` 字段（可能为 null，因为长度懒加载），改为调用 `toCopy.length()` 方法触发懒加载；但对 `DummyFileIO`（不支持 `.length()`）会抛 `UnsupportedOperationException`，故用 try/catch 兜底为 null。

## 如何达成设计目的

1. **修改基础字段定义**：`ManifestFile.SNAPSHOT_ID` 由 `optional(503, "added_snapshot_id", Types.LongType.get(), "Snapshot ID that added the manifest")` 改为 `required(503, "added_snapshot_id", Types.LongType.get(), "Snapshot ID that added the manifest")`。
2. **简化 V2/V3 Metadata 引用**：`V2Metadata`/`V3Metadata` 中 `ManifestFile.SNAPSHOT_ID.asRequired()` 改为 `ManifestFile.SNAPSHOT_ID`（直接引用，因为已是 required）。
3. **新增带 snapshotId 的构造器**：`GenericManifestFile(InputFile file, int specId, long snapshotId)`，与原有两参构造器逻辑相同，只是把 `this.snapshotId = null` 改为 `this.snapshotId = snapshotId`。
4. **BaseSnapshot 使用新构造器**：`BaseSnapshot.allManifests()` 中 v1 manifest 路径的 `new GenericManifestFile(fileIO.newInputFile(location), 0)` 改为 `new GenericManifestFile(fileIO.newInputFile(location), 0, this.snapshotId)`。
5. **拷贝构造器容错**：`this.length = toCopy.length` 改为 `try { this.length = toCopy.length(); } catch (UnsupportedOperationException e) { this.length = null; }`，并加注释"Can be removed when embedded manifests are dropped / DummyFileIO does not support .length()"。
6. **测试同步**：所有用 `new GenericManifestFile(localInput(...), 0)` 构造测试 manifest 的地方改为传入 `snapshotId`；新增 `TestReadProjection.testReadOptionalAsRequired` 测试，验证"写入时 optional、读取时 required"的兼容性（即读取端能把 optional 字段当 required 读，只要实际值非 null）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/ManifestFile.java`

**修改目的**：把字段 503 的基础定义改为 required，与规范对齐。

**工作逻辑**：
```java
Types.NestedField SNAPSHOT_ID =
    required(                       // 原为 optional(
        503, "added_snapshot_id", Types.LongType.get(), "Snapshot ID that added the manifest");
```

### `core/src/main/java/org/apache/iceberg/V2Metadata.java` / `core/src/main/java/org/apache/iceberg/V3Metadata.java`

**修改目的**：移除冗余的 `.asRequired()` 调用。

**工作逻辑**：manifest list Avro schema 定义中，`ManifestFile.SNAPSHOT_ID.asRequired()` 改为 `ManifestFile.SNAPSHOT_ID`。由于基础定义已是 required，二者产出的 schema 完全等价。

### `core/src/main/java/org/apache/iceberg/GenericManifestFile.java`

**修改目的**：新增带 snapshotId 的构造器，并让拷贝构造器对 DummyFileIO 容错。

**工作逻辑**：

1. 新增构造器：
   ```java
   GenericManifestFile(InputFile file, int specId, long snapshotId) {
     super(ManifestFile.schema().columns().size());
     this.avroSchema = AVRO_SCHEMA;
     this.file = file;
     this.manifestPath = file.location();
     this.length = null; // lazily loaded from file
     this.specId = specId;
     this.sequenceNumber = 0;
     this.minSequenceNumber = 0;
     this.snapshotId = snapshotId;   // 关键：显式传入，不再为 null
     this.addedFilesCount = null;
     // ... 其余字段同原两参构造器
     this.keyMetadata = null;
   }
   ```

2. 拷贝构造器修改：
   ```java
   // 原：this.length = toCopy.length;  （直接字段访问，可能为 null）
   // 新：
   try {
     this.length = toCopy.length();   // 调用方法触发懒加载
   } catch (UnsupportedOperationException e) {
     // Can be removed when embedded manifests are dropped
     // DummyFileIO does not support .length()
     this.length = null;
   }
   ```

### `core/src/main/java/org/apache/iceberg/BaseSnapshot.java`

**修改目的**：v1 内嵌 manifest 路径下传入 snapshotId，避免 required 字段为 null。

**工作逻辑**：`allManifests()` 中 v1 路径：
```java
// 原：location -> new GenericManifestFile(fileIO.newInputFile(location), 0)
// 新：
location -> new GenericManifestFile(fileIO.newInputFile(location), 0, this.snapshotId)
```

### `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java`
### `core/src/test/java/org/apache/iceberg/TestSnapshotJson.java`
### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：测试中构造 `GenericManifestFile` 时同步传入 `snapshotId`。

**工作逻辑**：三处 `new GenericManifestFile(localInput(...), 0)` 改为 `new GenericManifestFile(localInput(...), 0, snapshotId)`，其中 `snapshotId` 为对应测试方法中已有的快照 ID 变量。

### `core/src/test/java/org/apache/iceberg/avro/TestReadProjection.java`

**修改目的**：验证"写 optional / 读 required"的向后兼容性。

**工作逻辑**：新增 `testReadOptionalAsRequired` 测试：构造一个写 schema，其中 `data` 字段为 `optional`；写入一条 `data="test"` 的记录；再用一个 `data` 为 `required` 的读 schema 读取；断言能正确读出 `"test"`。这证明了"老快照（optional 字段）能被新代码（required 字段）读取"，只要实际值非 null。

## 小结

- **成效**：把 `ManifestFile` 字段 503 `added_snapshot_id` 的基础定义从 `optional` 修正为 `required`，使参考实现与 Iceberg 规范一致；同时保证 v1 内嵌 manifest 路径下 `snapshotId` 不再为 null，避免重写元数据时写出非法 manifest；拷贝构造器对 `DummyFileIO` 做 try/catch 容错，保持嵌入式 manifest 场景下的兼容性。新增测试验证了"写 optional / 读 required"的向后兼容性。
- **影响范围**：1 个 API 文件（`api/.../ManifestFile.java`）、4 个 core 主代码文件（`BaseSnapshot.java`、`GenericManifestFile.java`、`V2Metadata.java`、`V3Metadata.java`）、4 个测试文件。共 9 个文件、60 行新增、10 行删除。
- **回迁到 1.4.x 的注意事项**：这是一个规范对齐修复，**建议回迁**，但需谨慎评估兼容性。回迁要点：
  1. **API 变更**：`ManifestFile.SNAPSHOT_ID` 从 optional 变为 required 是 API 层面的 schema 定义变更，会影响所有读取/写入 manifest list 的路径。1.4.x 上若有第三方扩展直接引用 `ManifestFile.SNAPSHOT_ID` 并依赖其 optional 性质，需同步检查。
  2. **向后兼容性**：本提交新增的 `testReadOptionalAsRequired` 测试证明，老版本写出的 manifest（字段为 optional）仍能被新代码读取（只要值非 null）。但反过来，新代码写出的 manifest（字段为 required）若被老版本读取，理论上也能读取（因为 Avro 读取端通常能处理 required→optional 的差异）。因此回迁不会破坏对已有数据的读取。
  3. **v1 manifest 路径**：1.4.x 上 `BaseSnapshot.allManifests()` 的 v1 路径需同步改用三参构造器，否则在 v1 表上重写元数据会产生 `added_snapshot_id=null` 的非法 manifest。
  4. **拷贝构造器**：1.4.x 上 `GenericManifestFile` 的拷贝构造器需同步改为 try/catch 形式，否则在 DummyFileIO 场景下会抛 `UnsupportedOperationException`。
  5. **测试同步**：建议同步回迁测试改动，避免回归。
  6. **依赖关系**：本提交与 #1445（SnapshotParser 容错）无直接依赖，可独立回迁。但若 1.4.x 上还有其他依赖 `SNAPSHOT_ID` 为 optional 的代码（如自定义 ManifestFile 实现），需一并审查。
