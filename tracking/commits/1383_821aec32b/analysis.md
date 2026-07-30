# 提交 1383：API: Add Variant data type (#11324)

## 提交信息

- **序号**：1383 / 4088
- **哈希**：821aec32bb9be28d9c1905f772d9e3101cc98d9e
- **短哈希**：821aec32b
- **日期**：2024-11-15（Fri Nov 15 12:52:23 2024 -0600）
- **作者**：Aihua Xu <aihuaxu@gmail.com>
- **提交说明**：API: Add Variant data type
- **PR/Issue**：#11324

## 总体目的

Variant 是一种半结构化数据类型，可以容纳任意 JSON 风格的值（对象、数组、字符串、数字、布尔、null 等），类似于 Snowflake 的 VARIANT、Databricks/Spark 的 Variant 类型。它弥补了 Iceberg 现有类型系统只能表示固定 schema 的局限：当数据字段的结构不稳定或高度嵌套时，用 Variant 比反复改 schema 或用 `STRING` 存 JSON 更高效（Variant 保留值的类型信息，支持部分路径查询与统计）。

本提交在 Iceberg API 层引入 Variant 类型，作为表格式 v3 的新能力之一（与 `TIMESTAMP_NANO` 并列的 v3-only 类型）。具体完成：

1. **新增 `Types.VariantType`**：单例 Type 实现，`TypeID.VARIANT`，Java 类映射为 `Object.class`（因为 Variant 可承载任意值）。
2. **扩展类型系统基础设施**：在 `Type.TypeID` 枚举、`TypeUtil.SchemaVisitor`、`FindTypeVisitor`、`IndexByName`、`IndexParents` 等所有类型访问器中为 Variant 添加分支。
3. **约束 v3-only**：在 `Schema.MIN_FORMAT_VERSIONS` 中登记 `VARIANT → 3`，v1/v2 表使用 Variant 字段会在 schema 校验时被拒绝。
4. **限制不可用场景**：Variant 不可用作分区字段（非 primitive）、不可 bucket、不可 identity sort、不可 identity transform——这些都是合理的限制，因为 Variant 是半结构化类型，没有自然的 hash/order 语义。
5. **表达式 sanitization**：`ExpressionUtil` 中 Variant 字面量的 sanitization 走字符串路径（与 binary/uuid 一致）。
6. **改进错误信息**：`TableMetadata` 的 format version 校验错误信息增加"supported: vN"提示。

本提交只做 API 层的类型定义与约束，不涉及 Variant 的实际读写（Parquet/ORC/Avro 的 Variant 读写、metrics 收集等由后续提交完成）。

## 如何达成设计目的

通过以下层次实现：

1. **类型定义层**：在 `Type.TypeID` 枚举新增 `VARIANT(Object.class)`，在 `Types` 新增 `VariantType` 内部类（单例，无参数）。
2. **visitor 层**：在 `TypeUtil.SchemaVisitor` 新增 `variant()` 默认方法（返回 null），并在 `visit(Type, SchemaVisitor)` 的 switch 中添加 `case VARIANT: return visitor.variant();`。所有具体 visitor（`FindTypeVisitor`、`IndexByName`、`IndexParents`）覆写 `variant()` 提供实际逻辑。
3. **schema 校验层**：`Schema.MIN_FORMAT_VERSIONS` 添加 `VARIANT → 3`，复用已有的 `Schema` 校验逻辑（`assertValidSchema` 等）自动拒绝 v1/v2 表的 Variant 字段。
4. **transform 限制层**：`Identity.get(Type)` 显式拒绝 Variant；bucket transform 通过 `canTransform` 返回 false 拒绝 Variant；分区校验通过"非 primitive 不可分区"间接拒绝。
5. **大小估算层**：`TypeUtil.estimateSize` 给 VARIANT 分配 80 字节（与 BINARY 一致），用于内存估算。
6. **测试层**：覆盖序列化往返、不可分区、不可 bucket、不可 identity sort、不可 identity transform、v3-only 校验等。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Type.java`（修改，+2/-1 行）

**修改目的**：在 `TypeID` 枚举新增 `VARIANT`。

**工作逻辑**：

```java
DECIMAL(BigDecimal.class),
STRUCT(StructLike.class),
LIST(List.class),
MAP(Map.class),
VARIANT(Object.class);
```

`VARIANT` 映射到 `Object.class` 作为 Java 表示类，因为 Variant 值可能是任意类型（String、Long、List、Map 等），没有更精确的公共父类。每个 `TypeID` 关联一个 `javaClass` 用于反射与类型检查。

### `api/src/main/java/org/apache/iceberg/types/Types.java`（修改，+35 行）

**修改目的**：新增 `VariantType` 实现。

**工作逻辑**：

```java
public static class VariantType implements Type {
  private static final VariantType INSTANCE = new VariantType();

  public static VariantType get() {
    return INSTANCE;
  }

  @Override
  public TypeID typeId() {
    return TypeID.VARIANT;
  }

  @Override
  public String toString() {
    return "variant";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (!(o instanceof VariantType)) {
      return false;
    }
    VariantType that = (VariantType) o;
    return typeId() == that.typeId();
  }

  @Override
  public int hashCode() {
    return Objects.hash(VariantType.class, typeId());
  }
}
```

设计要点：

- **单例**：`VariantType` 无参数（不像 `DecimalType` 有 precision/scale），所以用 `INSTANCE` 单例 + `get()` 工厂，避免重复创建。
- **不继承 `PrimitiveType`**：Variant 不是 primitive（它是半结构化），因此直接 `implements Type` 而非 `extends PrimitiveType`。这导致 `type.asPrimitiveType()` 对 Variant 返回 null，间接使分区校验（要求 primitive source field）拒绝 Variant。
- `toString()` 返回 `"variant"`，用于 JSON/字符串表示。
- `equals`/`hashCode` 与其他无参单例类型（`StringType`、`BinaryType`）模式一致：先比引用、再比类型、再比 typeId。

### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java`（修改，+8 行）

**修改目的**：在类型 visitor 框架中支持 Variant。

**工作逻辑**：

1. `SchemaVisitor<T>` 新增默认方法：
   ```java
   public T variant() {
     return null;
   }
   ```
   与 `primitive()`、`struct()` 等并列。默认返回 null，子类按需覆写。
2. `visit(Type, SchemaVisitor<T>)` 的 switch 新增：
   ```java
   case VARIANT:
     return visitor.variant();
   ```
   在 `MAP` 分支之后、`default` 之前，确保 Variant 不会走到 `visitor.primitive(type.asPrimitiveType())`（因为 `asPrimitiveType()` 对 Variant 返回 null，会 NPE）。
3. `estimateSize(Type)` 的 switch 中 `BINARY` 分支新增 `case VARIANT:` fall-through：
   ```java
   case BINARY:
   case VARIANT:
     return 80;
   ```
   Variant 的内存估算取 80 字节（与 BINARY 一致），这是一个保守的默认值，实际 Variant 值大小可能差异巨大（小到几个字节，大到几 MB），但 estimateSize 只用于粗略内存规划。

### `api/src/main/java/org/apache/iceberg/types/FindTypeVisitor.java`（修改，+9 行）

**修改目的**：让 `FindTypeVisitor` 能匹配 Variant 类型。

**工作逻辑**：

```java
@Override
public Type variant() {
  if (predicate.test(Types.VariantType.get())) {
    return Types.VariantType.get();
  }
  return null;
}
```

`FindTypeVisitor` 用 predicate 在 schema 中查找满足条件的类型。覆写 `variant()` 后，若 predicate 匹配 VariantType 则返回实例，否则返回 null（不匹配）。这与 `primitive()` 的实现模式一致。

### `api/src/main/java/org/apache/iceberg/types/IndexByName.java`（修改，+5 行）

**修改目的**：让 `IndexByName`（构建字段名 → ID 映射）能遍历 Variant 字段。

**工作逻辑**：

```java
@Override
public Map<String, Integer> variant() {
  return nameToId;
}
```

`IndexByName` 维护一个累积的 `nameToId` 映射，每个 visitor 方法返回当前累积结果。Variant 是叶节点（无子字段），直接返回当前 `nameToId`，与 `primitive()` 实现一致。

### `api/src/main/java/org/apache/iceberg/types/IndexParents.java`（修改，+5 行）

**修改目的**：让 `IndexParents`（构建字段 ID → 父字段 ID 映射）能遍历 Variant 字段。

**工作逻辑**：

```java
@Override
public Map<Integer, Integer> variant() {
  return idToParent;
}
```

与 `IndexByName` 对称，Variant 是叶节点，直接返回当前 `idToParent`。

### `api/src/main/java/org/apache/iceberg/Schema.java`（修改，+1/-1 行）

**修改目的**：登记 Variant 为 v3-only 类型。

**工作逻辑**：

```java
static final Map<Type.TypeID, Integer> MIN_FORMAT_VERSIONS =
    ImmutableMap.of(Type.TypeID.TIMESTAMP_NANO, 3, Type.TypeID.VARIANT, 3);
```

`MIN_FORMAT_VERSIONS` 在 schema 校验时被 `Schema.assertValidSchema(Schema)` 检查：若某字段的 `typeId()` 在此 map 中且对应的最小版本 > 当前表 format version，则抛 `IllegalStateException("... is not supported until v3")`。Variant 与 `TIMESTAMP_NANO` 一样要求 v3。

### `api/src/main/java/org/apache/iceberg/transforms/Identity.java`（修改，+3 行）

**修改目的**：禁止 Variant 用作 identity transform。

**工作逻辑**：

在已废弃的静态工厂 `Identity.get(Type type)` 中添加：

```java
Preconditions.checkArgument(
    type.typeId() != Type.TypeID.VARIANT, "Unsupported type for identity: %s", type);
```

注意是加在 `@Deprecated` 的 `get(Type)` 方法中，因为 `Transforms.identity(Type)` 与 `Transforms.fromString(Type, "identity")` 都会经过此方法。新代码应使用 `Transforms.identity().bind(type)` 路径，bind 时也会通过 `canTransform` 检查拒绝 Variant。

### `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java`（修改，+3/-2 行）

**修改目的**：Variant 字面量的 sanitization 走字符串路径。

**工作逻辑**：

两处 switch 的 `case BINARY:` 分支新增 `case VARIANT:` fall-through，并在注释中补充 "variant"。Variant 值在表达式 sanitization 时调用 `value.toString()` 转字符串，然后用 `sanitizeSimpleString` 处理（截断/转义），与 binary/uuid/decimal 一致。这保证包含 Variant 字面的过滤条件在日志/错误信息中不会泄露敏感数据。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`（修改，+4/-3 行）

**修改目的**：改进 format version 校验错误信息。

**工作逻辑**：

```java
// before
Preconditions.checkArgument(
    formatVersion <= SUPPORTED_TABLE_FORMAT_VERSION,
    "Unsupported format version: v%s",
    formatVersion);
// after
Preconditions.checkArgument(
    formatVersion <= SUPPORTED_TABLE_FORMAT_VERSION,
    "Unsupported format version: v%s (supported: v%s)",
    formatVersion,
    SUPPORTED_TABLE_FORMAT_VERSION);
```

错误信息增加"(supported: vN)"提示，让用户知道当前 Iceberg 支持的最高 format version。这与 Variant 本身无直接关系，是顺带改进——因为 Variant 引入后用户可能更频繁遇到 v3 相关的版本错误。

### 测试文件

#### `api/src/test/java/org/apache/iceberg/TestPartitionSpecValidation.java`（修改，+15/-1 行）

- SCHEMA 新增 `NestedField.required(7, "v", Types.VariantType.get())`；
- 新增 `testVariantUnsupported`：尝试对 Variant 字段做 bucket(5) 分区，断言抛 `ValidationException("Cannot partition by non-primitive source field: variant")`。验证 Variant 因非 primitive 不可分区。

#### `api/src/test/java/org/apache/iceberg/TestSchema.java`（修改，+4/-1 行）

- `TEST_TYPES` 列表新增 `Types.VariantType.get()`，让 schema 相关的参数化测试自动覆盖 Variant。

#### `api/src/test/java/org/apache/iceberg/transforms/TestBucketing.java`（修改，+14 行）

- 新增 `testVariantUnsupported`：
  - `Transforms.bucket(VariantType, 3)` 抛 `IllegalArgumentException("Cannot bucket by type: variant")`；
  - `Transforms.bucket(3).bind(VariantType)` 抛同样异常；
  - `bucket.canTransform(VariantType)` 返回 false。

#### `api/src/test/java/org/apache/iceberg/transforms/TestIdentity.java`（修改，+18 行）

- 新增 `testVariantUnsupported`：
  - `Transforms.identity().bind(VariantType)` 抛 `IllegalArgumentException("Cannot bind to unsupported type: variant")`；
  - `Transforms.fromString(VariantType, "identity")` 抛 `IllegalArgumentException("Unsupported type for identity: variant")`；
  - `Transforms.identity(VariantType)` 抛同样异常；
  - `identity.canTransform(VariantType)` 返回 false。

#### `api/src/test/java/org/apache/iceberg/types/TestSerializableTypes.java`（修改，+10/-1 行）

- 新增 `testVariant`：`Types.VariantType.get()` 经 Java 序列化往返后仍 `equals` 原实例。验证单例的序列化兼容性（反序列化后应仍是同一逻辑类型）。
- 顺手把 `BinaryType.get()` 后的逗号去掉（代码风格修正）。

#### `core/src/test/java/org/apache/iceberg/TestSortOrder.java`（修改，+16 行）

- 新增 `testVariantUnsupported`：构造含 `struct.v`（VariantType）的 schema，尝试 `SortOrder.builderFor(v3Schema).asc("struct.v").build()`，断言抛 `IllegalArgumentException("Unsupported type for identity: variant")`。验证 Variant 不可用作 sort order 的 identity 排序字段。

#### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`（修改，+54/-46 行）

- `testVersionValidation` 扩展：除了原有的 `new TableMetadata(...)` 路径，新增 `TableMetadata.newTableMetadata(...)` 路径的 unsupported version 测试，并新增 positive 测试验证 supported version 可成功构造（通过 `new TableMetadata` 与 `newTableMetadata` 两条路径）。
- 删除 `testV3TimestampNanoTypeSupport`：原测试验证 `TimestampNanoType` 在 v1/v2 表上被拒绝、在 v3 上被允许。删除后，v3-only 类型的校验由 `Schema.MIN_FORMAT_VERSIONS` 机制保证，`TestSchema` 的参数化测试覆盖了 Variant 与 TimestampNano 的 schema 校验。
- 错误信息断言更新为 `"Unsupported format version: v%s (supported: v%s)"` 格式。

## 小结

- **成效**：在 Iceberg API 层引入 `VariantType` 半结构化数据类型，作为 v3 表格式的新能力。类型系统基础设施（TypeID、SchemaVisitor、FindTypeVisitor、IndexByName、IndexParents、TypeUtil.estimateSize）全部就绪，schema 校验约束 Variant 为 v3-only，transform 层禁止 Variant 用作分区/bucket/identity sort/identity。配套测试覆盖序列化往返、各场景的拒绝行为、v3 校验。为后续 Variant 的实际读写（Parquet/ORC/Avro 适配、metrics 收集、Spark/Flink 引擎集成）奠定基础。
- **影响范围**：`api` 模块 9 个生产文件（Type、Types、TypeUtil、FindTypeVisitor、IndexByName、IndexParents、Schema、ExpressionUtil、Identity）+ 4 个测试文件；`core` 模块 1 个生产文件（TableMetadata）+ 2 个测试文件。`VariantType` 是 public 类，外部用户可直接通过 `Types.VariantType.get()` 使用。无对现有类型的破坏性变更（纯新增 TypeID 与 visitor 方法，旧 visitor 子类不覆写 `variant()` 时默认返回 null，向后兼容）。
- **回迁到 1.4.x 的注意事项**：
  1. **强依赖 v3 表格式支持**：1.4.x 分支必须已支持 format version 3（`TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION >= 3`）。若 1.4.x 仍只支持 v2，则 `MIN_FORMAT_VERSIONS` 中登记 Variant 为 v3-only 会导致 v3 表创建失败（因为 v3 本身不被支持）。需先确认 1.4.x 的 v3 支持状态；
  2. **Type.TypeID 枚举新增值**：VARIANT 的加入是 ABI 变更——若有外部代码对 `TypeID` 做 exhaustive switch 且未处理 default 分支，编译可能失败。但 Iceberg 的 `TypeID` 是 public API，新增枚举值属于正常的版本演进；
  3. **SchemaVisitor 新增 `variant()` 方法**：这是 source 兼容但 binary 不兼容的变更。外部自定义 visitor 子类不需要改动（默认返回 null），但若重新编译会有新方法。1.4.x 上若有外部插件自定义 visitor，回迁后需重新编译；
  4. **Variant 的实际读写未包含**：本提交只在 API 层定义类型，Parquet/ORCAvro 的 Variant 读写器、metrics 收集、Spark/Flink 引擎集成均不在本提交范围。回迁后 1.4.x 上 Variant 字段只能定义在 schema 中但无法实际读写数据，需评估是否一并回迁后续读写支持提交；
  5. **测试删除**：`testV3TimestampNanoTypeSupport` 被删除，v3-only 类型校验的测试覆盖变薄。回迁后建议补一个参数化的 v3-only 类型校验测试，覆盖 TimestampNano 与 Variant 两类；
  6. **`Identity.get(Type)` 的检查**：加在 `@Deprecated` 方法上，1.4.x 若已移除该方法则需把检查移到 `Transforms.identity()` 链路的其他入口；
  7. **`estimateSize` 给 Variant 分配 80 字节**：这是粗略估算，若 1.4.x 上有内存敏感场景（如向量化读取批量大小计算），需评估该估算是否合理；
  8. 总体而言，本提交是 Variant 功能的第 1 步（API 层定义），回迁前需评估 1.4.x 是否计划完整支持 Variant，否则只回迁 API 层意义有限。
