# 提交 1191：Core: Replace use of CharSequenceMap in DeleteFileIndex with String (#11199)

## 提交信息

- **序号**：1191 / 4088
- **哈希**：95497abe5579cf492f24ac8c470c7853d59332e9
- **短哈希**：95497abe5
- **日期**：2024-09-26（Thu Sep 26 11:44:14 2024 -0600）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Core: Replace use of CharSequenceMap in DeleteFileIndex with String (#11199)
- **PR/Issue**：#11199

## 总体目的

`DeleteFileIndex` 是 Iceberg 用于索引"位置删除文件按数据文件路径分组"的核心结构。在 `posDeletesByPath` 字段中，此前使用 `CharSequenceMap<PositionDeletes>`——以 `CharSequence`（可能是 `String`、`Utf8` 等不同实现）作为 key 来索引位置删除文件。

`CharSequenceMap` 的设计初衷是避免在以 `CharSequence` 为 key 的场景下反复调用 `toString()`（避免字符串拷贝）。但实际使用中，`DeleteFileIndex` 通过 `ContentFileUtil.referencedDataFile(deleteFile)` 获取的 `CharSequence` 来自文件元数据，而查询时通过 `dataFile.path()` 获取的也是 `CharSequence`。这两者的 `CharSequence` 实现可能不同（例如一个是 `String`、另一个是 Avro 的 `Utf8`），导致 `CharSequenceMap` 内部需要做归一化处理，反而引入复杂性与潜在的 hash/equals 不一致风险。

本提交的目的是把 `posDeletesByPath` 的 key 类型从 `CharSequence`（用 `CharSequenceMap`）改为 `String`（用普通 `Map<String, PositionDeletes>`），通过显式 `toString()` 归一化为 `String`。这样：
- 用标准 `HashMap` 替代自定义 `CharSequenceMap`，简化代码、减少抽象层；
- `String` 的 `hashCode`/`equals` 语义明确无歧义，避免 `CharSequence` 跨实现的潜在不一致；
- 同时把查询路径从 `dataFile.path()` 改为 `dataFile.location()`，与构建索引时使用的 `referencedDataFileLocation`（新增工具方法）保持一致，确保 key 来源统一。

## 如何达成设计目的

1. **新增 `ContentFileUtil.referencedDataFileLocation(DeleteFile)`**：返回 `String` 而非 `CharSequence`。内部调用已有的 `referencedDataFile(deleteFile)` 获取 `CharSequence`，再 `toString()` 转为 `String`（null 则返回 null）。
2. **`DeleteFileIndex` 字段类型变更**：`posDeletesByPath` 从 `CharSequenceMap<PositionDeletes>` 改为 `Map<String, PositionDeletes>`。
3. **构建索引时用 String key**：`Builder.add` 中用 `ContentFileUtil.referencedDataFileLocation(file)` 取 `String` 路径，`deletesByPath.computeIfAbsent(path, ignored -> new PositionDeletes())`。注意 `computeIfAbsent` 的第二个参数从方法引用 `PositionDeletes::new` 改为 lambda `ignored -> new PositionDeletes()`——因为 `Map<String, V>.computeIfAbsent` 的 function 接收 key（`String`），而 `PositionDeletes::new` 的构造函数不接受参数，需用 lambda 忽略 key。
4. **查询时用 String key**：`forDataFile` 方法从 `posDeletesByPath.get(dataFile.path())` 改为 `posDeletesByPath.get(dataFile.location())`。这里 `dataFile.location()` 返回 `String`（`ContentFile.location()` 返回 `String`），与构建时的 key 类型一致。
5. **初始化**：`posDeletesByPath = CharSequenceMap.create()` 改为 `posDeletesByPath = Maps.newHashMap()`。
6. **移除 import**：不再需要 `CharSequenceMap`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/ContentFileUtil.java`

**修改目的**：提供返回 `String` 的路径获取方法。

**工作逻辑**：新增方法：
```java
public static String referencedDataFileLocation(DeleteFile deleteFile) {
  CharSequence location = referencedDataFile(deleteFile);
  return location != null ? location.toString() : null;
}
```
内部委托给已有的 `referencedDataFile(deleteFile)`（返回 `CharSequence`），再 `toString()` 归一化为 `String`。这样调用方无需关心 `CharSequence` 的具体实现，统一拿到 `String`。

### `core/src/main/java/org/apache/iceberg/DeleteFileIndex.java`

**修改目的**：把 `posDeletesByPath` 的 key 从 `CharSequence` 改为 `String`。

**工作逻辑**：
- 移除 import：`org.apache.iceberg.util.CharSequenceMap`。
- 字段声明：`private final CharSequenceMap<PositionDeletes> posDeletesByPath;` → `private final Map<String, PositionDeletes> posDeletesByPath;`
- 构造函数参数：`CharSequenceMap<PositionDeletes> posDeletesByPath` → `Map<String, PositionDeletes> posDeletesByPath`。
- 查询方法 `forDataFile`：
  ```java
  PositionDeletes deletes = posDeletesByPath.get(dataFile.path());
  ```
  改为：
  ```java
  PositionDeletes deletes = posDeletesByPath.get(dataFile.location());
  ```
  关键点：`dataFile.path()` 返回 `CharSequence`（可能为 `Utf8`），而 `dataFile.location()` 返回 `String`。改用 `location()` 保证查询 key 类型与 map key 类型（`String`）一致，避免 `CharSequence` 跨实现的 `hashCode`/`equals` 不匹配导致查不到。
- `Builder.build` 中初始化：`CharSequenceMap<PositionDeletes> posDeletesByPath = CharSequenceMap.create();` → `Map<String, PositionDeletes> posDeletesByPath = Maps.newHashMap();`
- `Builder.add` 方法签名：`CharSequenceMap<PositionDeletes> deletesByPath` → `Map<String, PositionDeletes> deletesByPath`。
- `Builder.add` 方法体：
  ```java
  CharSequence path = ContentFileUtil.referencedDataFile(file);
  ...
  deletes = deletesByPath.computeIfAbsent(path, PositionDeletes::new);
  ```
  改为：
  ```java
  String path = ContentFileUtil.referencedDataFileLocation(file);
  ...
  deletes = deletesByPath.computeIfAbsent(path, ignored -> new PositionDeletes());
  ```
  两处变化：(1) 调用 `referencedDataFileLocation` 拿 `String`；(2) `computeIfAbsent` 的 mapping function 从 `PositionDeletes::new`（方法引用，无参构造）改为 `ignored -> new PositionDeletes()`（lambda，忽略 key 参数）。这是因为 `Map<K, V>.computeIfAbsent(K, Function<? super K, ? extends V>)` 要求 function 接收 key，而 `PositionDeletes` 的无参构造不接受参数，需用 lambda 包装。

## 小结

- **成效**：`DeleteFileIndex.posDeletesByPath` 的 key 从 `CharSequence`（`CharSequenceMap`）改为 `String`（标准 `HashMap`），消除了 `CharSequence` 跨实现（`String` vs `Utf8`）的 hash/equals 歧义风险，简化了数据结构。查询路径从 `dataFile.path()` 改为 `dataFile.location()`，与构建索引时的 key 来源统一为 `String`，保证查找一致性。新增 `ContentFileUtil.referencedDataFileLocation` 提供 `String` 返回值的便利方法。
- **影响范围**：核心层 2 个 Java 文件，约 12 行新增、8 行删除。属于内部数据结构优化，对外 API（`DeleteFileIndex` 的公共方法）不变，索引构建与查询行为语义不变（仍是"按数据文件路径索引位置删除文件"）。
- **回迁到 1.4.x 的注意事项**：这是一个防御性的代码清理与健壮性改进，**回迁价值中等偏高**。如果 1.4.x 中存在因 `CharSequence` 跨实现导致位置删除查找失败的潜在 bug（例如 `dataFile.path()` 返回 `Utf8` 而 map 中存的是 `String`，导致 `get` 返回 null 进而漏掉位置删除——这会导致**读到本应被删除的行**，是数据正确性问题），则**强烈建议回迁**。回迁时需注意：(1) 必须同时带回 `ContentFileUtil.referencedDataFileLocation` 新方法；(2) `forDataFile` 从 `path()` 改为 `location()` 是关键修复点——需确认 1.4.x 中 `ContentFile.location()` 与 `ContentFile.path()` 的语义差异（`location()` 是完整路径 `String`，`path()` 是 `CharSequence` 可能来自 Avro 反序列化为 `Utf8`），确保改用 `location()` 后 key 一致；(3) `computeIfAbsent` 的 lambda 改写是机械适配，无风险；(4) 回迁后建议在 1.4.x CI 上跑一遍 `DeleteFileIndex` 相关测试（特别是涉及 Avro `Utf8` 路径的场景）确认无回归。**如果 1.4.x 已知有位置删除相关的数据正确性问题，本提交应优先回迁。**
