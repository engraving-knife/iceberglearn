# 提交 1445：Default to `overwrite` when operation is missing (#11421)

## 提交信息

- **序号**：1445
- **哈希**：3a04257e49b555b6013f1b984e1c1802e53e1956
- **短哈希**：3a04257e4
- **日期**：2024-11-28（Thu Nov 28 20:44:49 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Default to `overwrite` when operation is missing (#11421)
- **PR/Issue**：#11421
- **协同作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>

## 总体目的

Iceberg 快照元数据 JSON 中，`summary` 对象里必须包含 `operation` 字段（取值为 `append`/`overwrite`/`replace`/`delete` 等，定义在 `DataOperations` 中），用于描述该快照对应的操作类型。但现实中有一些非规范写入方（包括早期版本或第三方工具）会写出缺少 `operation` 字段或 `summary` 为空对象 `{}` 的快照 JSON。当 `SnapshotParser.fromJson` 解析此类异常快照时：

1. 若 `summary` 为空对象 `{}`，原代码会构造一个空 `ImmutableMap`，但 `operation` 仍为 `null`，下游使用 `operation` 的逻辑可能抛 NPE 或写出错误的 JSON。
2. 若 `summary` 非空但缺少 `operation` 字段，原代码同样会让 `operation` 保持 `null`，违反 Iceberg 规范。

本提交让 `SnapshotParser` 在解析时对这两种"越规"情况做容错处理：

- **缺少 `operation` 字段**：打 WARN 日志说明该快照缺少必需的 `operation` 字段，并将 `operation` 默认置为 `DataOperations.OVERWRITE`（即 `"overwrite"`），保证后续读取与重新序列化不会因 `operation=null` 而异常。
- **空 summary 对象 `{}`**：把 `summary` 直接置为 `null`（而非空 map），这样重新序列化时会跳过 `summary` 字段写出，避免输出一个无意义的空对象。

这是一种"读时容错"策略——不修改规范、不改变正常路径行为，只在反序列化时对越规输入兜底，确保 Iceberg 能读取并修复这些异常快照。

## 如何达成设计目的

1. **包裹解析逻辑到非空判断**：把原先直接进入 ` ImmutableMap.builder()` 的逻辑改为先判断 `sNode.size() > 0`，仅当 summary 对象里有字段时才构建 map；空对象则保持 `summary=null`。
2. **缺失 operation 时默认 overwrite**：在构建完 `summary` map 后，若 `operation == null`，通过 `LOG.warn(...)` 记录一条警告（包含 `snapshotId`），然后将 `operation` 设为 `DataOperations.OVERWRITE`。
3. **新增 Logger**：在 `SnapshotParser` 中引入 `slf4j` Logger 用于警告输出。
4. **新增测试**：在 `TestSnapshotJson` 中新增两个测试用例 `testJsonConversionSummaryWithoutOperation` 与 `testJsonConversionEmptySummary`，分别覆盖"summary 非空但缺 operation"和"summary 为空对象"两种场景，断言反序列化后再序列化的 JSON 符合预期（前者会把 `operation` 补为 `overwrite`，后者会完全省略 `summary` 字段）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotParser.java`

**修改目的**：在反序列化快照 JSON 时对缺少 `operation` 或空 `summary` 做容错。

**工作逻辑**：

1. 新增 Logger 字段：
   ```java
   private static final Logger LOG = LoggerFactory.getLogger(SnapshotParser.class);
   ```
   并新增 import `org.slf4j.Logger`、`org.slf4j.LoggerFactory`。

2. `fromJson(JsonNode node)` 中 summary 解析逻辑修改。修改前：
   ```java
   ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
   Iterator<String> fields = sNode.fieldNames();
   while (fields.hasNext()) {
     String field = fields.next();
     if (field.equals(OPERATION)) {
       operation = JsonUtil.getString(OPERATION, sNode);
     } else {
       builder.put(field, JsonUtil.getString(field, sNode));
     }
   }
   summary = builder.build();
   ```
   修改后：
   ```java
   if (sNode.size() > 0) {
     ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
     Iterator<String> fields = sNode.fieldNames();
     while (fields.hasNext()) {
       String field = fields.next();
       if (field.equals(OPERATION)) {
         operation = JsonUtil.getString(OPERATION, sNode);
       } else {
         builder.put(field, JsonUtil.getString(field, sNode));
       }
     }
     summary = builder.build();

     // When the operation is not found, default to overwrite
     // to ensure that we can read the summary without raising an exception
     if (operation == null) {
       LOG.warn(
           "Encountered invalid summary for snapshot {}: the field 'operation' is required but missing, setting 'operation' to overwrite",
           snapshotId);
       operation = DataOperations.OVERWRITE;
     }
   }
   ```
   即：仅当 summary 对象非空时才构建 map；构建后若 operation 仍为 null，则记录警告并默认为 `overwrite`。空 summary 对象则让 `summary` 保持初始值 `null`。

### `core/src/test/java/org/apache/iceberg/TestSnapshotJson.java`

**修改目的**：覆盖两种越规 summary 的解析行为。

**工作逻辑**：

- `testJsonConversionSummaryWithoutOperation`：构造一个 `summary` 只有 `files-added`/`files-deleted` 而没有 `operation` 字段的快照 JSON，调用 `SnapshotParser.fromJson` 后再 `SnapshotParser.toJson`，断言输出 JSON 中 `summary` 会补上 `"operation" : "overwrite"` 并保留其他字段。
- `testJsonConversionEmptySummary`：构造一个 `summary` 为空对象 `{}` 的快照 JSON，调用 `SnapshotParser.fromJson` 后再 `SnapshotParser.toJson`，断言输出 JSON 中完全省略 `summary` 字段（因为 `summary` 被置为 `null`，序列化时不会写出该字段）。

两个测试在注释中明确说明"This behavior is out of spec, but we don't want to fail on it."。

## 小结

- **成效**：让 `SnapshotParser` 对缺少 `operation` 字段或 `summary` 为空对象的越规快照 JSON 具备容错能力——前者默认补为 `overwrite` 并打 WARN 日志，后者直接置 `summary=null` 避免输出空对象。这提升了 Iceberg 读取老快照或第三方写入快照的健壮性，避免 NPE 或下游解析异常。
- **影响范围**：仅修改 `core/src/main/java/org/apache/iceberg/SnapshotParser.java`（新增 Logger、调整 summary 解析逻辑）和 `core/src/test/java/org/apache/iceberg/TestSnapshotJson.java`（新增两个测试）。共 2 个文件、96 行新增、9 行删除。无 API 签名变更。
- **回迁到 1.4.x 的注意事项**：这是一个对老快照/第三方快照的兼容性修复，对 1.4.x 这种维护分支非常有价值，**建议回迁**。回迁风险很低：① 不改变正常路径（有 operation 字段的快照）的解析结果；② 仅在异常路径上多一条 WARN 日志和默认值设置；③ `DataOperations.OVERWRITE` 是早已存在的常量，1.4.x 上一定可用；④ slf4j 在 Iceberg core 中已普遍使用，无需新增依赖。回迁时直接 cherry-pick 即可，注意 1.4.x 上 `SnapshotParser.fromJson` 的代码结构是否与 main 一致，若 1.4.x 上有其他差异需手动合并。回迁后建议同步带上两个测试用例以避免回归。需要提醒的是，本修复仅作用于"读取"路径——它能避免解析失败，但不会主动重写已有异常快照的 metadata 文件；如有大量异常快照需修正，仍需通过其他手段（如 rewrite_metadata）处理。
