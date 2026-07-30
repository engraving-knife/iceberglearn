# 提交 0302：Avro: Add Avro-assisted name mapping (#7392)

## 提交信息

- **序号**：0302 / 4088
- **哈希**：19b24b3f6f2e08bc95e929e75ea218eff90b5a87
- **短哈希**：19b24b3f6
- **日期**：2023-12-22 15:28:15 -0800
- **作者**：Walaa Eldin Moustafa
- **提交说明**：Avro: Add Avro-assisted name mapping (#7392)
- **PR/Issue**：#7392

## 总体目的

本提交为 Iceberg 的 Avro 模块引入"Avro-assisted name mapping"能力——即从一个 Avro schema（可能包含 Iceberg 不原生支持的构造，例如复杂 union）反推出对应的 Iceberg `NameMapping`。这是 Iceberg 在读取非 Iceberg 写入的 Avro 数据时进行字段 ID 解析的关键基础设施。

背景是：Iceberg 用字段 ID 而非字段名来标识列，从而支持字段重命名等演进。但 Avro 原生 schema 只有字段名、没有 Iceberg 字段 ID。当读取这类外部 Avro 文件时，需要 `NameMapping`（名字→ID 的映射）来补全 ID。传统上 `NameMapping` 由 Iceberg 表 schema 构造（`MappingUtil.create(schema)`），但当一个 Avro schema 包含 Iceberg 不直接支持的类型（最典型的就是 Avro 复杂 union，即不止 `null+T` 的 option 形式）时，Iceberg 需要先把 Avro schema 转成 Iceberg 类型，而这个转换本身又会"分配"字段 ID（通过 `SchemaToType` 的 `allocateId`）。本提交解决的问题就是：在把 Avro schema 转成 Iceberg 类型的同时，记录下"Avro 字段名/union 分支名 → 分配出的 Iceberg 字段 ID"的对应关系，从而构造出一份 `NameMapping`，供后续读取时把同名 Avro 字段映射回这些 ID。

核心难点在于 Avro 的复杂 union。Avro 允许 union 包含多个命名类型（record/enum/fixed）或非命名类型，而 Iceberg 类型系统没有 union，只能用 struct（带一个 `tag` 整型字段标识分支）来表示复杂 union。`SchemaToType` 在此前只支持 option union（`null+T`），遇到复杂 union 会直接抛 `Unsupported type: non-option union`。本提交一方面扩展 `SchemaToType` 让其能把复杂 union 转成带 tag 的 struct 并分配 ID，另一方面扩展 `AvroWithPartnerByStructureVisitor` 让其在遍历 union 时能按"struct 字段索引"对齐 union 分支，最终通过新增的 `NameMappingWithAvroSchema` 访问器产出 `MappedFields`，完成"Avro schema → NameMapping"的映射。配套测试用一个包含嵌套 record、数组、option union、复杂 union（含 record/enum/fixed/嵌套 union）的复杂 Avro schema 验证整条链路。

## 如何达成设计目的

整体设计分三层。第一层是"Avro schema → Iceberg 类型"的扩展：修改 `SchemaToType.union` 让复杂 union 转成 `StructType`（首字段 `tag` 为 required int，后续每个非 null 分支为 `fieldN` optional 字段），并在此过程中通过 `allocateId()` 分配字段 ID。第二层是"按结构遍历 Avro union"的扩展：修改 `AvroWithPartnerByStructureVisitor.visitUnion`，当 union 不是 option 形式时，依据 partner struct 的字段索引（NULL 之前分支映射到 struct 索引 i+1，NULL 之后映射到 i）对齐访问每个 union 分支，从而能与 struct 字段一一对应。第三层是新增 `AvroWithTypeByStructureVisitor`（以 Iceberg `Type` 为 partner 的具体实现）和 `NameMappingWithAvroSchema`（产出 `MappedFields` 的访问器），后者在 `record`/`union`/`array`/`map`/`primitive` 各方法中把"Iceberg 字段 ID + Avro 端名字"组装成 `MappedField`，从而产出完整 `NameMapping`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/avro/AvroWithPartnerByStructureVisitor.java`

**修改目的**：让按结构遍历 union 的逻辑支持复杂 union（非 option 形式），使其能配合 Iceberg struct 类型完成分支对齐访问。

**工作逻辑**：原 `visitUnion` 方法直接 `Preconditions.checkArgument(AvroSchemaUtil.isOptionSchema(union), ...)`，即只允许 option union（`[null, T]`）。修改后改为分支处理：若是 option union，保持原逻辑（null 分支访问 `visitor.nullType()`，非 null 分支按 partner type 访问）；若是复杂 union，则按"Avro 复杂 union 转 Iceberg struct"的约定对齐——struct 第 0 个字段是 `tag`（不对应 union 分支），因此 NULL 之前的非 null 分支 i 映射到 struct 字段索引 `i+1`，遇到 NULL 后分支 i 映射到 struct 字段索引 `i`（因为 struct 没有 NULL 对应的字段，索引要"回退"一位）。代码用一个 `encounteredNull` 标志跟踪是否已遇到 NULL 分支，对每个非 null 分支调用 `visitor.fieldNameAndType(type, structFieldIndex).second()` 取得对应 struct 字段类型作为 partner 继续递归访问。这层改动是纯结构对齐逻辑，不产出最终结果，结果由具体 visitor 的 `union` 方法决定。

### `core/src/main/java/org/apache/iceberg/avro/AvroWithTypeByStructureVisitor.java`（新增）

**修改目的**：提供一个以 Iceberg `Type` 为 partner 类型的 `AvroWithPartnerByStructureVisitor` 具体实现，作为后续 `NameMappingWithAvroSchema` 的基类，封装"Iceberg Type ↔ Avro Schema"的结构对接。

**工作逻辑**：继承 `AvroWithPartnerByStructureVisitor<Type, T>`，实现 6 个抽象方法：`isMapType`/`isStringType`/`arrayElementType`/`mapKeyType`/`mapValueType` 直接委托给 Iceberg `Type` 的相应判断与转换；`fieldNameAndType(Type structType, int pos)` 从 struct 的第 pos 个 `NestedField` 取出名字与类型返回 `Pair`；`nullType()` 返回 `null`（Iceberg 无独立 null 类型）。这个类本身不产出 name mapping，只是把"Iceberg Type 作为 partner"这一对接方式固化下来，供子类复用。

### `core/src/main/java/org/apache/iceberg/avro/NameMappingWithAvroSchema.java`（新增）

**修改目的**：实现"Avro schema → NameMapping"的核心访问器，遍历 Avro schema 同时借助 Iceberg partner 类型，产出每个字段的 `MappedField`（ID + 名字 + 子映射）。

**工作逻辑**：继承 `AvroWithTypeByStructureVisitor<MappedFields>`，各方法的职责是把 Iceberg 端分配的字段 ID 与 Avro 端的名字配对：

- `record(Type struct, Schema record, List<String> names, List<MappedFields> fieldResults)`：遍历 struct 字段，用 `field.fieldId()` 取 Iceberg ID、`field.name()` 取名字、对应位置 `fieldResults` 取子映射，组装 `MappedField` 列表，返回 `MappedFields.of(fields)`。
- `union(Type type, Schema union, List<MappedFields> optionResults)`：分两种情况。option union 时，找到第一个非 null 分支的 optionResults 返回（option 不引入新字段层级）。复杂 union 时，校验 partner 是 `StructType`，遍历 union 分支：对每个非 null 分支，依据 Avro 规范（union 内同类型不重复，命名类型按 record 名区分）选择映射 key——若分支是 RECORD/ENUM/FIXED 命名类型，用 `option.getName()` 作 key；否则用 `option.getType().getName()`（如 "string"、"int"）作 key。ID 取 struct 对应字段（跳过 tag 字段，故用独立 index 计数）的 `fieldId`。最终返回 `MappedFields`，把每个 union 分支名映射到其 Iceberg 字段 ID。注释详细说明了 Avro union 的命名/非命名类型规则，以及 `iStruct` 与 `optionResults` 都不含 NULL 条目故 index 仅在非 null 时自增。
- `array(Type list, Schema array, MappedFields elementResult)`：返回 `MappedField.of(list.asListType().elementId(), "element", elementResult)`，即把数组元素 ID 映射到名字 "element"。
- `map(Type sMap, Schema map, MappedFields keyResult, MappedFields valueResult)`（双参版本）：同时映射 key（"key"→keyId）和 value（"value"→valueId），key 带子映射 `keyResult`。
- `map(Type sMap, Schema map, MappedFields valueResult)`（单参版本）：key 无子映射（传 null），value 带 `valueResult`。
- `primitive`：返回 `null`，原语类型无嵌套字段。

### `core/src/main/java/org/apache/iceberg/avro/SchemaToType.java`

**修改目的**：扩展 Avro schema → Iceberg Type 的转换，使其支持复杂 union（转为带 tag 的 struct），从而让 Iceberg 能表达 Avro 复杂 union，并为 name mapping 提供 ID 分配来源。

**工作逻辑**：原 `union` 方法 `Preconditions.checkArgument(isOptionSchema(union))` 并在 option 时返回非 null 分支的类型。修改后：option union 时根据 null 在前还是在后返回另一分支类型（补全了原代码只处理 `options.get(0)==null` 的遗漏，新增 `else return options.get(0)` 处理 null 在后的情况）。复杂 union 时，构造一个 `StructType`：首字段 `Types.NestedField.required(allocateId(), "tag", IntegerType.get())` 作为分支标识，随后对每个非 null 选项分配 ID 并以 `fieldN`（N 从 0 递增）命名，类型为 `optional`。`allocateId()` 由 `SchemaToType` 内部计数器递增提供，保证整个 Avro schema 转换过程中每个字段都拿到唯一 ID。这套 ID 正是 `NameMappingWithAvroSchema` 在 partner struct 中读取的 `fieldId`。

### `core/src/test/java/org/apache/iceberg/avro/TestNameMappingWithAvroSchema.java`（新增）

**修改目的**：端到端验证"Avro schema → Iceberg schema → NameMapping"链路的正确性，覆盖嵌套 record、数组、option union、复杂 union（含嵌套 record/enum/fixed/嵌套 union）等场景。

**工作逻辑**：测试用 `Schema.createRecord`/`createArray`/`createUnion`/`createEnum`/`createFixed` 手工构造一个复杂 Avro schema（顶层 record 含 id/data/location 嵌套 record/friends 数组/simpleUnion option/complexUnion 复杂 union）。先用 `AvroSchemaUtil.toIceberg(schema)` 转成 Iceberg schema（触发 `SchemaToType` 分配 ID），再手工写出期望的 `MappedFields`（每个字段标明 ID 与名字，复杂 union 分支按命名类型用 record 名、非命名用类型名作 key），最后调用 `AvroWithPartnerByStructureVisitor.visit(icebergSchema.asStruct(), schema, new NameMappingWithAvroSchema())` 得到实际映射并 `Assert.assertEquals(expected, actual)`。期望结果中可看到：location 子字段 lat=6/long=7、friends element=8、complexUnion 各分支 ID 17~22（string/innerRecord1/innerRecord2/innerRecord3/timezone/bitmap），嵌套 innerUnion 内又有 string=13/int=14，验证了 ID 分配与名字映射的完整正确性。

## 小结

本提交为 Iceberg Avro 模块补上了"从 Avro schema 反推 NameMapping"的能力，核心是新增 `NameMappingWithAvroSchema` 访问器，并配套扩展 `SchemaToType`（支持复杂 union 转 struct 并分配 ID）和 `AvroWithPartnerByStructureVisitor`（支持按 struct 字段索引对齐复杂 union 分支）。这一能力使 Iceberg 能处理包含复杂 union 的外部 Avro 文件——把 Avro 复杂 union 映射成 Iceberg struct（带 tag 字段），并把 union 分支名（命名类型用 record 名、非命名用类型名）映射到分配出的字段 ID，从而在读取时能正确解析字段。这是 Iceberg 互操作性（读取非 Iceberg 写入的 Avro 数据）的重要一步，也为后续基于 Avro schema 的 name mapping 推断奠定基础。
