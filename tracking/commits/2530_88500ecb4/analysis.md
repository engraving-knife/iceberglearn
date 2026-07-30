# 提交 2530：Core: Rewrite the Iceberg Arrow schema translation to use the visitor pattern (#13699)

## 提交信息

- **序号**：2530 / 4088
- **哈希**：88500ecb457299cd46c5e075d29556ffdd5eaad5
- **短哈希**：88500ecb4
- **日期**：2025-08-19 09:49:57 -0700
- **作者**：Anoop Johnson
- **提交说明**：Core: Rewrite the Iceberg Arrow schema translation to use the visitor pattern (#13699)
- **PR/Issue**：#13699

## 总体目的

本次提交重构了 `ArrowSchemaUtil` 中 Iceberg Schema 到 Arrow Schema 的转换逻辑。原来的实现采用一个巨大的 `switch` 语句直接根据 `field.type().typeId()` 分支处理所有类型（包括 primitive、struct、list、map），逻辑集中、可扩展性差，且对嵌套结构（例如 list 元素为 struct、map value 为 list 等）的处理依赖手工递归调用 `convert(nested)`，类型信息与字段信息耦合在一起，难以维护和扩展新类型。

新实现引入 Iceberg 通用的 `TypeUtil.SchemaVisitor` 访问者模式，将不同类型节点的转换拆分到 `struct`/`list`/`map`/`primitive` 等独立方法中。这种重构让类型转换路径更清晰、更容易添加新类型，并且让每种复杂类型的处理逻辑独立可读。

测试侧也做了重要增强：原 `convertComplex` 测试被拆分成 `convertMap`，并新增 `convertStruct` 与 `convertNestedStructInList` 测试，覆盖嵌套 struct 与 list-of-struct 场景，弥补了之前对嵌套结构验证不足的问题。

## 如何达成设计目的

- 引入内部类 `IcebergToArrowTypeConverter extends TypeUtil.SchemaVisitor<Field>`，将 Iceberg 类型树通过访问者遍历转换为 Arrow `Field`。
- `currentField` 字段携带当前正在转换的 `NestedField`，让 `struct`/`list`/`map` 节点可以拿到字段名和 nullable 属性来构造 Arrow `Field`。
- `primitive` 方法保留原 switch 逻辑，仅处理基础类型，不再混入复合类型分支。
- 复合类型（struct/list/map）通过 `convertChildren` 递归地为每个子字段创建新的 visitor 实例，保持上下文隔离。
- map 类型保持原有 `ORIGINAL_TYPE = MAP_TYPE` 元数据写入，确保下游（例如 reader）依旧能识别 map 语义。
- 公共 API `convert(Schema)` 与 `convert(NestedField)` 行为保持不变，调用方无感知。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/ArrowSchemaUtil.java` (+123/-82)

**修改目的**：用访问者模式重写 Iceberg→Arrow schema 转换。

**工作逻辑**：
- `convert(Schema)` 与 `convert(NestedField)` 现在都通过 `TypeUtil.visit(field.type(), new IcebergToArrowTypeConverter(field))` 完成转换，统一入口。
- `IcebergToArrowTypeConverter` 实现访问者接口：
  - `schema(Schema, Field)` 直接返回 struct 结果。
  - `struct(StructType, List<Field>)` 用 `currentField` 名字与 nullable 构造 `ArrowType.Struct` 字段，子字段通过 `convertChildren` 转换。
  - `field(NestedField, Field)` 直接返回子结果，因为子字段已经在 `convertChildren` 中各自完成转换。
  - `list(ListType, Field)` 类似 struct，构造 `ArrowType.List` 字段。
  - `map(MapType, Field, Field)` 构造 `ArrowType.Map`，并写入 `ORIGINAL_TYPE=MAP_TYPE` 元数据；通过 `convertChildren(map.fields())` 转换 key/value，再包装成单个 entry 子字段（与原实现保持一致）。
  - `primitive(Type.PrimitiveType)` 保留原 switch，针对每种基础类型生成对应 `ArrowType`，并返回一个不带子字段的 `Field`。
- `convertChildren(Collection<NestedField>)` 对每个子字段创建独立 visitor，保证 `currentField` 在递归过程中始终指向当前节点，避免状态串扰。

### `arrow/src/test/java/org/apache/iceberg/arrow/TestArrowSchemaUtil.java` (+78/-2)

**修改目的**：增强对转换结果的覆盖。

**工作逻辑**：
- 将原 `convertComplex` 重命名为 `convertMap`，并新增对 `m`（string→int 简单 map）与 `m2`（string→list<timestamp> 复杂 map）的逐层断言，验证 entry 子字段数量与类型 ID。
- 新增 `convertStruct`：验证 struct 字段及其两个内部字段（`inner_string`、`inner_int`）的转换。
- 新增 `convertNestedStructInList`：验证 `list<struct<nested_field:string>>` 这种深层嵌套场景，确保 list 子字段是 struct 且 struct 子字段名正确。

## 总结

通过引入 `TypeUtil.SchemaVisitor` 访问者模式重构了 Iceberg→Arrow schema 转换，将原本集中在单个 `switch` 中的逻辑拆分到 `struct`/`list`/`map`/`primitive` 各自的访问者方法中，提升了可读性与可扩展性，并通过新增的嵌套 struct/list 测试加强了对深层嵌套结构的验证。对外 API 行为保持不变。
