# 提交 2945：Core: Align ContentFile partition JSON with REST spec (#14702)

## 提交信息

- **序号**：2945 / 4088
- **哈希**：9896e8cccac2b245ed20bfae602b29fc640dcd36
- **短哈希**：9896e8ccc
- **日期**：2025-12-02
- **作者**：Drew Gallardo
- **提交说明**：Core: Align ContentFile partition JSON with REST spec
- **PR/Issue**：#14702

## 总体目的

本提交修复 `ContentFileParser` 在序列化/反序列化分区数据（partition）时与 Iceberg REST 规范不一致的问题。

原本 `ContentFileParser` 把 `ContentFile` 的 partition 字段序列化为一个 JSON **对象**，以字段 ID 为键，例如 `"partition":{"1000":0}`（field-id 1000 → 值 0），无分区时写 `"partition":{}`。但 Iceberg REST 规范规定 partition 应序列化为一个按 spec 字段顺序排列的 JSON **数组**，例如 `"partition":[0]`，无分区时写 `"partition":[]`。这一不一致会导致：

1. **REST 客户端互操作问题**：REST 规范是 Iceberg 跨语言互操作的核心契约。Java 端 `ContentFileParser` 产生的对象格式若与 REST spec 定义（数组格式）不符，会让遵循 spec 的客户端在解析 Java 端产出的 JSON（或反向）时出错。虽然 REST 服务端通常用独立的序列化路径，但 `ContentFileParser` 是 core 内部通用的 ContentFile JSON 工具，被 `ScanTaskParser`、`DataTaskParser` 等复用，格式不一致会污染整个序列化链路。
2. **格式冗余**：对象格式携带字段 ID，但 partition 值的含义已由 `PartitionSpec` 的字段顺序唯一确定，数组格式更紧凑且无歧义。

本提交把序列化改为数组格式以对齐 REST spec，同时在反序列化侧保留对旧对象格式的兼容读取（commit message 中的 "Handle backwards compat"），避免读取既有历史元数据/JSON 时失败。整体设计是一次"写出新格式、读入兼容新旧两种格式"的演进。

## 如何达成设计目的

在 `ContentFileParser` 中新增两个私有方法 `partitionToJson` 与 `partitionFromJson`，分别承担序列化与反序列化逻辑，替换原来直接调用 `SingleValueParser.toJson(spec.partitionType(), ...)` 与 `SingleValueParser.fromJson(...)` 的内联写法。序列化统一写数组；反序列化根据 JSON 节点是 array 还是 object 分流处理，object 走旧路径以兼容历史数据。其余测试文件同步把期望 JSON 字符串从对象格式改为数组格式，并新增针对兼容读取与错误场景的回归用例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ContentFileParser.java` (+58/-12 lines)

**修改目的**：把 partition 序列化为数组格式对齐 REST spec，反序列化兼容数组与对象两种格式。

**工作逻辑**：
改动分三处：

1. **新增 import**：`import org.apache.iceberg.types.Types;`，用于 `Types.StructType` 与 `Types.NestedField`。

2. **序列化路径**（`toJson` 方法内，约第 90 行）：
   ```java
   // 旧：
   SingleValueParser.toJson(spec.partitionType(), contentFile.partition(), generator);
   // 新：
   partitionToJson(spec.partitionType(), contentFile.partition(), generator);
   ```
   新增的 `partitionToJson` 方法：
   ```java
   private static void partitionToJson(
       Types.StructType partitionType, StructLike partitionData, JsonGenerator generator)
       throws IOException {
     generator.writeStartArray();
     List<Types.NestedField> fields = partitionType.fields();
     for (int pos = 0; pos < fields.size(); ++pos) {
       Types.NestedField field = fields.get(pos);
       Object partitionValue = partitionData.get(pos, Object.class);
       SingleValueParser.toJson(field.type(), partitionValue, generator);
     }
     generator.writeEndArray();
   }
   ```
   按 spec 字段顺序逐个写出值，外层用 `writeStartArray()`/`writeEndArray()` 包成 JSON 数组。每个值仍委托 `SingleValueParser.toJson(field.type(), ...)` 按字段类型序列化，保证 int/string/等类型的格式正确。无分区时写出空数组 `[]`。

3. **反序列化路径**（`fromJson` 方法内，约第 153 行）：
   ```java
   // 旧：内联构造 PartitionData 并逐字段拷贝
   // 新：
   partitionData = partitionFromJson(spec.partitionType(), jsonNode.get(PARTITION));
   ```
   新增的 `partitionFromJson` 方法按 JSON 节点类型分流：
   - **数组分支**（新格式）：`if (partitionNode.isArray())`，先 `checkArgument(partitionNode.size() == fields.size(), ...)` 校验数组长度与字段数一致，再逐位置 `SingleValueParser.fromJson(field.type(), partitionNode.get(pos))` 解析并 `partitionData.set(pos, ...)`。
   - **对象分支**（旧格式，向后兼容）：`else if (partitionNode.isObject())`，注释说明"按 field ID 序列化、跳过 null 分区值"。先 `checkState(partitionNode.size() <= fields.size(), ...)`（对象可能因跳过 null 而少于字段数），再用原逻辑 `SingleValueParser.fromJson(partitionType, partitionNode)` 解析为 `StructLike`，逐位置 `structLike.get(pos, javaClass)` 拷贝进 `PartitionData`。注意这里用 `<=` 而非 `==`，因为对象格式天然省略 null 字段。
   - **其他**：`throw new IllegalArgumentException("Invalid partition data for content file: expected array or object (...）")`。

   这样新写入的数组格式与历史对象格式都能被正确读回，实现"写出新格式、读入兼容新旧"的平滑迁移。

### `core/src/test/java/org/apache/iceberg/TestContentFileParser.java` (+118/-11 lines)

**修改目的**：更新期望 JSON 字符串为数组格式，并新增兼容性、错误处理与顺序保持的回归测试。

**工作逻辑**：
改动分两类：

1. **期望 JSON 字符串改为数组格式**：`dataFileJsonWithRequiredOnly`、`dataFileJsonWithAllOptional`、`deleteFileWithDataRefJson`、`dvJson`、`deleteFileJsonWithRequiredOnly`、`deleteFileJsonWithAllOptional` 等辅助方法中，把 `"partition":{"1000":1}` 改为 `"partition":[1]`、`"partition":{}` 改为 `"partition":[]`。同时把两处 `Map.of(TestBase.SPEC.specId(), spec)` / `Map.of(spec.specId(), TestBase.SPEC)` 修正为 `Map.of(spec.specId(), spec)`——这是一个 latent bug 修复：原本用 `TestBase.SPEC.specId()` 作为键但 value 是局部 `spec`，键值不匹配；修正为用 `spec` 自身的 specId 作为键。

2. **新增测试方法**：
   - `testPartitionJsonArrayWrongSize`：构造 `"partition":[]` 但 spec 有一个分区字段，断言抛 `IllegalArgumentException` 且消息含 "Invalid partition data size"——覆盖数组长度校验。
   - `testPartitionJsonInvalidType`：构造 `"partition":"invalid"`（既非数组也非对象），断言抛 `IllegalArgumentException` 且消息含 "expected array or object"——覆盖兜底错误分支。
   - `testParsesFieldIdPartitionMap`：构造旧对象格式 `"partition":{"1000":"foo"}`，断言能正确读回 `partition.get(0, String.class) == "foo"`——验证向后兼容。
   - `testPartitionStructObjectContainsExtraField`：构造对象格式但含多余字段 `"partition":{"1000":"foo","9999":"bar"}`（9999 不在 spec 中），断言抛 `IllegalStateException` 含 "Invalid partition data size"——覆盖对象格式的 `<=` 校验（多余字段触发失败）。
   - `testPartitionStructObjectEmptyIsNull`：构造空对象 `"partition":{}`，断言读回后 `partition.get(0, String.class) == null`——验证对象格式省略字段对应 null 值的行为。
   - `testPartitionArrayRespectsSpecOrder`：构造双字段 spec（identity `id` + identity `data`），设值 `(4, "foo")`，断言序列化结果含 `"partition":[4,"foo"]`（顺序与 spec 字段一致），反序列化后 `get(0)==4`、`get(1)=="foo"`——验证数组格式按 spec 字段顺序写入与读回。

### `core/src/test/java/org/apache/iceberg/TestDataTaskParser.java` (+18/-3 lines)

**修改目的**：把 data task 期望 JSON 中 partition 从对象改为数组，并新增旧格式兼容读取测试。

**工作逻辑**：
- 两处 `createDataTask` 与 missingTableRows 用例的 JSON 中，`"partition":{}` 改为 `"partition":[]`。
- 新增 `testDataTaskParsesFieldIdPartitionMap`：用旧对象格式 `"partition":{}`（空对象，无分区字段）反序列化，断言 `metadataFile().partition().size() == 0`——验证 DataTask 路径下旧格式仍可读。

### `core/src/test/java/org/apache/iceberg/TestFileScanTaskParser.java` (+33/-5 lines)

**修改目的**：把 file scan task 期望 JSON 中 partition 改为数组格式，并新增旧格式兼容读取测试。

**工作逻辑**：
- 原 `fileScanTaskJson()` 方法中的期望 JSON 把 `"partition":{"1000":0}` 改为 `"partition":[0]`（data-file 与两个 delete-file 共三处）。
- 把原 `fileScanTaskJson()` 方法体拆分：新 `fileScanTaskJson()` 返回数组格式期望 JSON，新增 `fileScanTaskFieldIdPartitionMapJson()` 返回旧对象格式 JSON（`"partition":{"1000":0}`）。
- 新增 `testFileScanTaskParsesFieldIdPartitionMap`：用 `fileScanTaskFieldIdPartitionMapJson()`（旧格式）反序列化，并与 `createFileScanTask(...)` 比对，验证旧格式仍能正确读回——覆盖 FileScanTask 路径的向后兼容。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestPlanTableScanResponseParser.java` (+6/-34 lines)

**修改目的**：把 REST 响应解析测试中所有 partition 期望 JSON 从对象改为数组。

**工作逻辑**：
多处期望 JSON 字符串把 `"partition":{"1000":0}` 改为 `"partition":[0]`、`"partition":{"1000":1}` 改为 `"partition":[1]` 等（涉及 delete-file 与 data-file 的 partition 字段），包括紧凑 JSON 与多行 pretty-print 两种格式（多行格式从 `"partition" : {\n "1000" : 0\n}` 简化为 `"partition" : [ 0 ]`）。这些测试验证 REST 响应解析器对数组格式 partition 的处理，与 `ContentFileParser` 的新输出对齐。无新测试方法，仅更新期望值。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestFetchPlanningResultResponseParser.java` 与 `TestFetchScanTasksResponseParser.java`（各 +3/-3 lines）

**修改目的**：把 Fetch 系列 REST 响应测试中 partition 期望 JSON 从对象改为数组。

**工作逻辑**：
两个文件中各三处（共六处）把 `"partition":{"1000":0}` 改为 `"partition":[0]`，涉及 delete-file 与 data-file 的 partition 字段。与 `TestPlanTableScanResponseParser` 同理，仅更新期望值以匹配新序列化格式，验证 REST 响应解析器与新格式一致。无新测试方法。

## 总结

本次提交把 `ContentFileParser` 的 partition 序列化从"字段 ID 为键的对象格式"改为"按 spec 字段顺序的数组格式"，与 Iceberg REST 规范对齐，解决了内部序列化与跨语言互操作契约的不一致。反序列化侧同时支持数组（新）与对象（旧）两种格式以保留向后兼容，并补充了长度校验、类型校验、空对象=null、顺序保持、旧格式读取等多维度回归测试，同步修正了若干测试中 specId 键值不匹配的 latent bug。这是一次面向互操作正确性的重要格式对齐，对 REST 客户端与历史元数据读取均保持平滑过渡。
