# 提交 0309：Core: Optimize manifest evaluation for super wide tables (#9147)

## 提交信息

- **序号**：0309 / 4088
- **哈希**：197b61e756155b95f6c889e7718bf1c2af70af7f
- **短哈希**：197b61e75
- **日期**：2023-12-25 12:52:48 +0100
- **作者**：Irshad CC
- **提交说明**：Core: Optimize manifest evaluation for super wide tables (#9147)
- **PR/Issue**：#9147

## 总体目的

本提交针对"超宽表"（super wide tables，列数极多，例如成百上千列）场景下 manifest 评估的性能问题做了一处微小但关键的优化：把 `NamedReference.bind()` 中"每次都新建一个 `Schema`"的昂贵调用，替换为对 `Types.StructType.asSchema()` 的延迟缓存调用，避免在每次绑定表达式时重复构造 Schema 及其内部的多张索引表（按名、按大小写不敏感名、按 id 等）。

背景是 `NamedReference.bind(Types.StructType struct, boolean caseSensitive)` 是 Iceberg 表达式体系的核心方法之一——任何"按列名引用"的未绑定 `NamedReference`（例如 `Expressions.equal("col_x", 100)`）在求值前都需要先绑定到一个具体的 `StructType`，按列名查到对应的 `NestedField`，再拿到字段 id 与 `Accessor` 构造 `BoundReference`。原实现是 `Schema schema = new Schema(struct.fields());` —— 这行代码本身看起来人畜无害，但每次调用都会：先让 `struct.fields()` 返回一个 `List<NestedField>`（已是 `lazyFieldList()` 缓存的不可变视图，代价小），然后 `new Schema(List<NestedField>)` 会触发 Schema 自身一组 lazy 字段的初始化（包括 `lazyNameToField`、`lazyNameToFieldCaseInsensitive`、`lazyAliasToField`、`lazyIdToField` 等多张 Map），对于宽表而言这些 Map 都很大、构造一次的开销相当可观。问题在于：如果同一个 `StructType` 被反复 `bind`（manifest 评估、谓词投影、扫描规划等场景下都会触发），每次都白做一次相同的 Schema 构造，纯属浪费 CPU。

manifest 评估是触发该路径的典型场景：`ManifestEvaluator` 在扫描规划阶段会把行级过滤条件投影到分区 spec，再 `Binder.bind` 绑定到分区 `StructType`，然后对每个 manifest 调用 `eval` 决定是否跳过该 manifest 的文件清单。对于一张列数极多的超宽表（分区字段或表 schema 字段数很多），每次绑定都重建一次 Schema 的代价会被放大到用户可感知的延迟级别。修复方式很自然：让 `StructType` 把它对应的 `Schema` 缓存起来，反复 `bind` 时复用同一份 Schema，省掉重复索引构造。

## 如何达成设计目的

整体思路是"延迟缓存 + 复用"：在 `Types.StructType` 上新增一个 `transient Schema schema = null` 字段（与该类已有的 `fieldList`、`fieldsByName`、`fieldsByLowerCaseName`、`fieldsById` 等 transient lazy 字段完全同一风格），并提供 `public Schema asSchema()` 方法——首次调用时 `new Schema(Arrays.asList(this.fields))` 构造并缓存，后续调用直接返回缓存值。`NamedReference.bind()` 改为调用 `struct.asSchema()` 而不是 `new Schema(struct.fields())`，从而把"每次 bind 重新构造 Schema"降为"每个 StructType 构造一次 Schema"。`transient` 关键字确保 Schema 不参与 StructType 的 Java 序列化——StructType 作为 `api` 模块的核心类型，其序列化形态应当保持稳定，Schema 只作为运行时缓存存在。注意此处新增了 `import org.apache.iceberg.Schema` 到 `Types.java`——`Schema` 与 `Types` 同在 `api` 模块，没有跨模块依赖问题。

值得注意的几个细节：(1) `Arrays.asList(this.fields)` 直接基于 StructType 内部的 `NestedField[]` 数组构造 List，比 `struct.fields()` 走 `lazyFieldList()` 路径更轻量（不会再次拷贝数组）；(2) 缓存是 lazy 的——只有真正被 `asSchema()` 调用过才会构造，不影响只用 StructType 做其它事的代码路径；(3) `Schema` 自身是不可变的（除了 lazy 字段的可见性约定），所以多线程并发读取是安全的（与 StructType 已有的 lazy 字段并发模型一致）；(4) `Schema` 不实现自定义 `equals/hashCode` 与 StructType 严格对应，但 `asSchema()` 构造的 Schema 字段 id、名字、类型完全来自 StructType 的 `NestedField[]`，与 `new Schema(struct.fields())` 在语义上等价。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Types.java`

**修改目的**：在 `Types.StructType` 上增加延迟缓存的 `asSchema()` 方法，缓存与该 struct 字段等价的 `Schema` 实例。

**工作逻辑**：

1. **新增 import**：`import org.apache.iceberg.Schema;`（`Types` 与 `Schema` 同在 `api` 模块，无循环依赖）。

2. **新增 lazy 字段**：在 `StructType` 已有的 lazy 字段块（`fieldList`、`fieldsByName`、`fieldsByLowerCaseName`、`fieldsById`）之上，再加一行：

   ```java
   // lazy values
   private transient Schema schema = null;
   private transient List<NestedField> fieldList = null;
   private transient Map<String, NestedField> fieldsByName = null;
   private transient Map<String, NestedField> fieldsByLowerCaseName = null;
   private transient Map<Integer, NestedField> fieldsById = null;
   ```

   `transient` 标记保证 Schema 不参与 Java 序列化（避免 Schema 内部状态污染 StructType 的序列化形态）。

3. **新增 `asSchema()` 方法**：

   ```java
   /**
    * Returns a schema which contains the columns inside struct type. This method can be used to
    * avoid expensive conversion of StructType containing large number of columns to Schema during
    * manifest evaluation.
    *
    * @return the schema containing columns of struct type.
    */
   public Schema asSchema() {
     if (this.schema == null) {
       this.schema = new Schema(Arrays.asList(this.fields));
     }
     return this.schema;
   }
   ```

   方法体只在 `schema == null` 时构造一次 `Schema`，构造时直接基于内部 `fields` 数组（`Arrays.asList` 包了一层 `Arrays$ArrayList` 视图，不拷贝元素），后续调用直接返回缓存。Javadoc 明确点出该方法是为了避免"含大量列的 StructType 在 manifest 评估期间反复转 Schema 的昂贵代价"。

### `api/src/main/java/org/apache/iceberg/expressions/NamedReference.java`

**修改目的**：把 `bind` 中每次都 `new Schema(struct.fields())` 的调用替换为 `struct.asSchema()`，复用缓存的 Schema。

**工作逻辑**：

```java
@Override
public BoundReference<T> bind(Types.StructType struct, boolean caseSensitive) {
-  Schema schema = new Schema(struct.fields());
+  Schema schema = struct.asSchema();
   Types.NestedField field =
       caseSensitive ? schema.findField(name) : schema.caseInsensitiveFindField(name);

   ValidationException.check(
       field != null, "Cannot find field '%s' in struct: %s", name, schema.asStruct());

   return new BoundReference<>(field, schema.accessorForField(field.fieldId()), name);
}
```

下游对 `schema` 的所有调用（`schema.findField(name)`、`schema.caseInsensitiveFindField(name)`、`schema.asStruct()`、`schema.accessorForField(field.fieldId())`）都不变——它们都基于 Schema 内部已构造好的 lazy 索引，因此调用 `asSchema()` 复用 Schema 后这些索引也只构造一次。对于超宽表场景，原本每次 bind 都触发 Schema 内部 `lazyNameToField`/`lazyNameToFieldCaseInsensitive`/`lazyIdToField` 等多张大 Map 的重建，现在变成首次构造、后续复用——这是本提交的收益点。

## 小结

本次提交针对超宽表 manifest 评估的性能问题，在 `Types.StructType` 上新增了 `asSchema()` 方法（lazy 缓存对应的 `Schema`），并把 `NamedReference.bind()` 中每次 `new Schema(struct.fields())` 的调用改为 `struct.asSchema()`，使同一 StructType 在多次 bind 时复用一份 Schema（连同其内部按名/大小写不敏感名/id 的多张索引 Map）。改动只涉及 2 个文件、净增 16 行（17 增 1 删），但消除了 manifest 评估期间对超宽表（列数极多）的重复 Schema 构造热点，是典型的"小改动、大收益"性能优化。`asSchema()` 的 lazy 缓存设计与 `StructType` 既有的 `fieldList`/`fieldsByName` 等 lazy 字段完全一致，无新增并发风险，且 `transient` 保证序列化形态稳定。值得注意的是该 PR 编号 #9147 远小于周边同期合并的 #9365/#9368/#9372，说明此优化 PR 经历了较长的评审周期才合并进 main，但修复本身专注于 manifest 评估这一具体性能路径。
