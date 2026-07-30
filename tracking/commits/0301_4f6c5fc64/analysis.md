# 提交 0301：Core: Add ApplyNameMapping for Avro (#9347)

## 提交信息

- **序号**：0301 / 4088
- **哈希**：4f6c5fc6403de9a4843f54b58fcd371e1e206873
- **短哈希**：4f6c5fc64
- **日期**：2023-12-22 14:02:39 -0800
- **作者**：Ryan Blue
- **提交说明**：Core: Add ApplyNameMapping for Avro (#9347)
- **PR/Issue**：#9347

## 总体目的

Iceberg 读取 Avro 数据文件时，需要把 Avro 文件 schema 中的字段名"映射"回 Iceberg 字段 ID，这通过 `NameMapping` 完成。Iceberg 表演进过程中可能改字段名、删字段、加字段，而旧数据文件中的 Avro schema 仍保留原始字段名，因此读取时必须依赖 name mapping 把名字解析成 ID，再据此裁剪出要读取的列。

本次提交前，name mapping 的应用与列裁剪（prune）逻辑耦合在一起——`PruneColumns` 这个访问器同时接收 `selectedIds` 和 `nameMapping`，在裁剪过程中既要查 ID 又要做投影。这种耦合带来两个问题：一是单一访问器承担两件职责，逻辑变复杂；二是后续若要单独做 name mapping（例如为写路径或其他场景准备带 ID 的 schema），就必须重复实现一份相似逻辑。

本提交把"应用 name mapping"这一步独立出来，做成单独的 `ApplyNameMapping` Avro Schema 访问器：先在整个文件 schema 上把字段 ID 补齐，得到一份"带 ID 的 schema"，再让裁剪逻辑只关注投影，不再需要 name mapping 参数。这样读取流程被拆成清晰的两个阶段：apply name mapping → prune columns。

此外，该重构也为后续工作铺路。从同一批次的 0302 提交（Avro-assisted name mapping）可以看出，社区正在增强 name mapping 能力（支持复杂 union 等），把 name mapping 单独抽离成一步，便于复用与扩展，也让 `ProjectionDatumReader` 的读取流程更清晰。同时提交还在 `revapi.yml` 中为 `NameMapping` 类的序列化兼容性变更做了显式声明（跨版本序列化不保证兼容），反映此次重构对 `NameMapping` 内部结构有触及。

## 如何达成设计目的

核心设计是新增一个 `ApplyNameMapping` 访问器（继承 `AvroSchemaVisitor<Schema>`），它接收一个 `NameMapping`，遍历 Avro schema，对每个字段、数组元素、map key/value 通过 name mapping 查找对应的 Iceberg 字段 ID，并把 ID 写回到 schema 的属性中（`field-id`、`element-id`、`key-id`、`value-id`）。访问器在无变更时返回原对象，在有变更时构造新的 record/union/array/map，从而以最小代价产出一份"带 ID 的 Avro schema"。

随后 `AvroSchemaUtil` 暴露新的 `applyNameMapping(Schema, NameMapping)` 静态入口，并把 `pruneColumns` 拆成两个重载：新的两参版本 `pruneColumns(Schema, Set<Integer>)`（不带 name mapping）与原三参版本（标记 `@Deprecated`，计划 2.0.0 移除）。最后 `ProjectionDatumReader` 改为先 `applyNameMapping` 再 `pruneColumns`，完成读取流程的解耦。

## 修改详情

### `.palantir/revapi.yml`

**修改目的**：声明 `NameMapping` 类在 1.4.0 版本中的二进制/序列化兼容性破坏为可接受。

**工作逻辑**：在 `acceptedBreaks` 的 `1.4.0` → `org.apache.iceberg:iceberg-core` 下新增一条 `java.class.defaultSerializationChanged` 记录，old/new 均为 `class org.apache.iceberg.mapping.NameMapping`，理由为"Serialization across versions is not guaranteed"。这说明本次提交对 `NameMapping` 的内部结构有改动（见下文 `NameMapping.java` 新增方法及潜在字段变化），导致默认序列化 UID 改变，Palantir revapi API 兼容性检查会捕获此变化；维护者通过此条目显式豁免，明确 Iceberg 不保证 `NameMapping` 跨版本序列化兼容。

### `core/src/main/java/org/apache/iceberg/avro/ApplyNameMapping.java`（新增）

**修改目的**：新增一个独立的 Avro Schema 访问器，把 `NameMapping` 应用到 Avro schema 上，为每个字段补齐 Iceberg 字段 ID。

**工作逻辑**：

- 类签名 `ApplyNameMapping extends AvroSchemaVisitor<Schema>`，构造时接收 `NameMapping nameMapping`。访问器维护 `fieldNames()`（来自父类）作为当前字段的访问路径，用于在 name mapping 中查找。
- `record(Schema, List<String> names, List<Schema> fields)`：遍历 record 的每个字段，调用 `AvroSchemaUtil.getFieldId(field, nameMapping, fieldNames())` 查 ID。若 ID 存在且子 schema 非空，则用 `copyField(field, newSchema, fieldId)` 复制字段并写入 `FIELD_ID_PROP`；否则仍复制字段但不写 ID（"always copy because fields can't be reused"）。当没有任何字段发生变化时直接返回原 record 以避免无谓拷贝；否则用 `copyRecord` 重建 record。
- `union(Schema, List<Schema> options)`：当 options 与原 union 类型一致时返回原 union；否则过滤掉 null 选项后重建 union（处理被裁剪掉的分支）。
- `array(Schema, Schema element)`：先判断是否为 `LogicalMap`（Iceberg 用 `array<kv-struct>` 表示 map 的存储形式）或 key/value 都能命中的 key-value mapping 场景，是则按 array 复制元素 schema；否则尝试从 schema 属性读 `elementId`，没有再从 name mapping 查 `find(fieldNames(), "element")`，找到则用 `createArray(element, id)` 写入 `ELEMENT_ID_PROP`；都找不到则原样返回。
- `map(Schema, Schema value)`：先尝试从 schema 属性读 `keyId`/`valueId`，匹配则按需重建；否则从 name mapping 查 `key`/`value` 两个映射，都命中则用 `createMap(value, keyId, valueId)` 写入 `KEY_ID_PROP` 与 `VALUE_ID_PROP`；否则原样返回。
- `primitive`：直接返回，原语类型无需 ID。
- 私有辅助方法 `copyField` / `copyRecord` / `createMap` / `createArray` / `copyProps` 负责构造新的 Schema 对象并复制属性（含 logical type），保证不修改原 schema。`copyField` 在 `fieldId` 非空时额外写入 `AvroSchemaUtil.FIELD_ID_PROP`，并复制 aliases。

整体策略是"按需重建"：只有确实补到 ID 时才构造新对象，否则返回原对象，避免不必要的 schema 拷贝开销。

### `core/src/main/java/org/apache/iceberg/avro/AvroSchemaUtil.java`

**修改目的**：暴露 `applyNameMapping` 入口，新增不带 name mapping 的 `pruneColumns` 重载，并提取若干只读 schema 属性的辅助方法，重构 `getFieldId` 以复用新的 `NameMapping.find` 重载。

**工作逻辑**：

- 新增 `public static Schema pruneColumns(Schema schema, Set<Integer> selectedIds)`：直接调用 `new PruneColumns(selectedIds, null).rootSchema(schema)`，即不再传 name mapping。
- 给原三参版本 `pruneColumns(Schema, Set<Integer>, NameMapping)` 加 `@Deprecated` 注解，JavaDoc 说明"will be removed in 2.0.0; use applyNameMapping and pruneColumns(Schema, Set) instead"，引导调用方迁移到两阶段流程。
- 新增 `public static Schema applyNameMapping(Schema fileSchema, NameMapping nameMapping)`：当 name mapping 非空时，用 `AvroSchemaVisitor.visit(fileSchema, new ApplyNameMapping(nameMapping))` 应用映射，否则原样返回 schema。
- 新增三个包级辅助方法 `keyId(Schema)` / `valueId(Schema)` / `elementId(Schema)`：仅从 schema 的 `KEY_ID_PROP`/`VALUE_ID_PROP`/`ELEMENT_ID_PROP` 对象属性中读取 ID（不查 name mapping），供 `ApplyNameMapping` 在判断"schema 是否已带 ID"时使用。它们与已有的 `getKeyId`/`getValueId`/`getElementId`（要求 name mapping 非空且做参数校验）形成对照——前者只读属性、后者会回退到 name mapping。
- 新增 `static Integer fieldId(Schema.Field field)` 便捷方法，调用 `getFieldId(field, null, null)` 仅读属性。
- 重构 `getFieldId(Schema.Field, NameMapping, Iterable<String>)`：把原来"复制 parentFieldNames 列表再 add(field.name())"的逻辑替换为调用新的 `nameMapping.find(parentFieldNames, field.name())`，减少中间集合分配，并统一查找路径。

### `core/src/main/java/org/apache/iceberg/avro/ProjectionDatumReader.java`

**修改目的**：把读取流程从"一步裁剪"改为"先应用 name mapping 再裁剪"。

**工作逻辑**：原代码 `Schema prunedSchema = AvroSchemaUtil.pruneColumns(newFileSchema, projectedIds, nameMapping);` 改为两步：

```java
Schema schemaWithIds = AvroSchemaUtil.applyNameMapping(newFileSchema, nameMapping);
Schema prunedSchema = AvroSchemaUtil.pruneColumns(schemaWithIds, projectedIds);
```

即先用 `applyNameMapping` 把文件 Avro schema 补齐字段 ID，再调用不带 name mapping 的 `pruneColumns` 按 `projectedIds` 裁剪。后续 `buildAvroProjection` 与 `newDatumReader()` 不变。该改动是本次重构的"消费端"，把解耦后的两个 API 串起来。

### `core/src/main/java/org/apache/iceberg/mapping/NameMapping.java`

**修改目的**：为 `NameMapping` 增加更便于在访问器中调用的查找重载。

**工作逻辑**：

- 新增 `import Iterables`。
- 新增 `public MappedField find(Iterable<String> names)`：等价于已有 `find(List<String> names)`，但接受任意 `Iterable`，让调用方无需先把路径转为 `List`。
- 新增 `public MappedField find(Iterable<String> names, String name)`：用 `Iterables.concat(names, ImmutableList.of(name))` 拼出完整路径再 `DOT.join`，返回对应 `MappedField`。这一重载正是供 `AvroSchemaUtil.getFieldId` 重构后调用，避免在热路径上创建中间 `ArrayList`。

这两个重载是纯加法，不改变原有 `find(List<String>)` 行为，但让 `NameMapping` 的 API 更友好，也间接改变了类的序列化形态（新方法本身不影响序列化，但类结构变化触发了 revapi 报告，见 `.palantir/revapi.yml`）。

## 小结

本提交把 Avro 读取流程中"应用 name mapping"与"裁剪列"两个耦合的职责拆开，新增 `ApplyNameMapping` 访问器单独负责补字段 ID，`pruneColumns` 退化为只按 ID 投影，`ProjectionDatumReader` 改为两阶段调用。重构提升了代码清晰度与可复用性，为后续 name mapping 能力扩展（如 0302 提交的 Avro-assisted name mapping）打下基础，同时通过新增 `NameMapping.find` 重载简化查找路径，并在 `revapi.yml` 中显式声明序列化兼容性豁免。
