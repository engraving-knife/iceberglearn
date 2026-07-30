# 提交 0202：API: add StructTransform base class for PartitionKey and SortKey. add SortOrderComparators (#7798)

## 提交信息

- **序号**：0202 / 4088
- **哈希**：b21a8ce2497eb560feb9ed771a1e28ca57d1cf60
- **短哈希**：b21a8ce24
- **日期**：2023-11-28 10:41:38 -0800
- **作者**：Steven Zhen Wu
- **提交说明**：API: add StructTransform base class for PartitionKey and SortKey. add SortOrderComparators (#7798)
- **PR/Issue**：#7798

## 总体目的

本提交是 `api` 模块一次重要的结构性重构 + 新能力补齐：把原本散落在 `PartitionKey` 里的"按一组字段变换把输入行转成一个扁平元组"的机制抽到一个新的公共基类 [`StructTransform`](api/src/main/java/org/apache/iceberg/StructTransform.java) 中，再让 `PartitionKey` 继承它；同时新增一个对称的 [`SortKey`](api/src/main/java/org/apache/iceberg/SortKey.java)（针对 `SortOrder` 而非 `PartitionSpec`）和一组配套的 [`SortOrderComparators`](api/src/main/java/org/apache/iceberg/SortOrderComparators.java)，使 Iceberg 在 API 层具备了"按表的 `SortOrder` 直接比较两个 `StructLike` 行"的能力。

背景是：Iceberg 表既可能有 `PartitionSpec`（分区规约），也可能有 `SortOrder`（排序规约）。两者在数据结构上极为相似——都是一组 `(源字段 id, Transform)` 元组的有序集合，都是把输入行投影成一组"变换后的值"，差别只在语义（分区 vs 排序）和来源（`PartitionSpec.fields()` vs `SortOrder.fields()`）。但在重构前，只有 `PartitionKey` 实现了这套机制：它内部维护 `accessors[]`、`transforms[]`、`partitionTuple[]`，并实现了 `StructLike` 的 `size/get/set` 和 `equals/hashCode/toString`。如果想给 `SortOrder` 也提供类似的"把一行数据转成一组排序键"的能力，最直接的做法就是把 `PartitionKey` 的逻辑复制一份，但这显然是反模式。

作者 Steven Zhen Wu 选择了正确的方式：抽取基类。新基类 `StructTransform` 封装了 `accessors`、`transforms`、`transformedTuple` 的构造、复制、wrap（应用所有 transform 写入元组）、以及 `StructLike` 接口和 `equals/hashCode/toString` 的实现；`PartitionKey` 改为继承它，并保留自己的领域字段（`spec`、`inputSchema`）和入口方法 `partition(row)`（内部调 `wrap(row)`）；新增的 `SortKey` 同样继承 `StructTransform`，对 `SortOrder` 做对称封装。

第二个新增能力 `SortOrderComparators` 是这次重构的另一面收益——一旦有了 `SortKey`，构造一个"按 `SortOrder` 比较两个 `StructLike` 的 `Comparator`"就水到渠成：内部维护两个可复用的 `SortKey` 实例（左侧/右侧），预计算每个 `SortField` 的 `Comparator`（含方向反转和 null 顺序处理）和变换结果类型，`compare` 时分别 `wrap` 两边，按字段顺序返回第一个非零比较结果。这对任何需要"按 Iceberg 表的排序规约排数据"的下游（如 Flink/Spark source 在 split/记录层做水印或顺序对齐）都是直接可用的 API 层基础设施。

## 如何达成设计目的

整体设计分三层：(1) 抽出 `StructTransform` 作为"变换后扁平元组"的通用载体，承担 `StructLike` + `Serializable` 的全部实现；(2) `PartitionKey`、`SortKey` 各自变成 `StructTransform` 的薄子类，只保留领域字段和把规约对象转换成 `List<FieldTransform>` 的静态工厂；(3) 在此之上构建 `SortOrderComparators`，用两个复用的 `SortKey` 实例和预计算的 per-field comparator 实现行级比较。改动共 5 个文件、814 行新增、82 行删除，其中测试占 489 行。

## 修改详情

### `api/src/main/java/org/apache/iceberg/StructTransform.java`（新增）

**修改目的**：抽出 `PartitionKey` 中"按一组字段变换把行转成元组"的公共机制，作为 `PartitionKey` 和 `SortKey` 的共同基类。

**工作逻辑**：包级可见（无 `public` 修饰），实现 `StructLike, Serializable`。核心字段：
- `int size`：字段数；
- `Accessor<StructLike>[] accessors`：每个字段在输入 schema 上的取值器；
- `SerializableFunction[] transforms`：每个字段绑定了类型的 transform 函数（`transform.bind(accessor.type())`）；
- `Object[] transformedTuple`：存放 transform 后的结果，作为 `StructLike` 的"行数据"。

主构造器 `StructTransform(Schema schema, List<FieldTransform> fieldTransforms)` 接受输入 schema 和一组 `(源字段 id, Transform)` 描述，对每个 `FieldTransform`：用 `schema.accessorForField(sourceFieldId)` 取 accessor（非空校验），用 `transform.bind(accessor.type())` 得到绑定后的函数。注意此处 schema 是"输入行 schema"——这与原 `PartitionKey` 用 `inputSchema` 取 accessor 的行为一致。

复制构造器 `StructTransform(StructTransform toCopy)` 深拷贝 `transformedTuple`，但 `accessors` 和 `transforms` 直接共享引用——这两个数组在构造后是不可变的（只读），共享是安全的，省一份内存。

关键方法 `public void wrap(StructLike row)` 是整套机制的引擎：遍历每个字段，对 `accessors[i].get(row)` 应用 `transforms[i]`，把结果写回 `transformedTuple[i]`。这等价于原 `PartitionKey.partition(row)` 的循环体。`StructLike` 的 `size/get/set` 直接落到 `transformedTuple`；`equals/hashCode/toString` 也基于 `transformedTuple`（`equals` 用 `Arrays.equals`，`hashCode` 用 `Arrays.hashCode`）。

内部静态类 `FieldTransform` 是一个简单 POJO，封装 `int sourceFieldId` 和 `Transform<?,?> transform`。注释明确说明：之所以不用 `Pair`，是因为 `Pair` 在 `core` 模块且带 Avro 依赖，无法在 `api` 模块使用——这是 Iceberg 严格的模块依赖分层（`api` 不依赖 `core`）下的一个细节妥协。

### `api/src/main/java/org/apache/iceberg/PartitionKey.java`（重构）

**修改目的**：让 `PartitionKey` 继承 `StructTransform`，删除已被基类吸收的重复代码，把 `partition(row)` 改为薄包装。

**工作逻辑**：类签名由 `implements StructLike, Serializable` 改为 `extends StructTransform`。删除了字段 `size`、`partitionTuple`、`transforms`、`accessors`，以及 `size()`、`get()`、`set()`、`toString()`、`equals()`、`hashCode()` 五个方法（全部由基类提供）。

构造器变为：
```java
public PartitionKey(PartitionSpec spec, Schema inputSchema) {
  super(inputSchema, fieldTransform(spec));
  this.spec = spec;
  this.inputSchema = inputSchema;
}
```
即把 `(inputSchema, List<FieldTransform>)` 传给 `StructTransform`。新增静态工厂：
```java
private static List<FieldTransform> fieldTransform(PartitionSpec spec) {
  return spec.fields().stream()
      .map(pf -> new FieldTransform(pf.sourceId(), pf.transform()))
      .collect(Collectors.toList());
}
```
把 `PartitionSpec.fields()`（即 `List<PartitionField>`）转换成基类需要的 `List<FieldTransform>`。

复制构造器：
```java
private PartitionKey(PartitionKey toCopy) {
  super(toCopy);          // 深拷贝 transformedTuple
  this.spec = toCopy.spec;
  this.inputSchema = toCopy.inputSchema;
}
```
原 `partition(StructLike row)` 的循环体删除，方法体只剩 `wrap(row);`。行为完全等价——`wrap` 就是原来那个循环。

需要注意一个微妙的语义保持点：原构造器用 `spec.schema()` 调 `findField`（仅用于错误信息），用 `inputSchema` 调 `accessorForField`；新基类统一用传入的 schema（即 `inputSchema`）做两件事，因此 `findField` 也基于 `inputSchema`。这是合理的——partition 字段的 source id 必须能在 input schema 中被解析为 accessor，否则本就无法 partition；用 input schema 的 findField 给出错误信息更贴近真实失败点。

### `api/src/main/java/org/apache/iceberg/SortKey.java`（新增）

**修改目的**：提供 `SortOrder` 对应的"变换元组"载体，与 `PartitionKey` 完全对称。

**工作逻辑**：`public class SortKey extends StructTransform`，结构几乎是 `PartitionKey` 的镜像：持有 `Schema schema` 和 `SortOrder sortOrder`，构造器调 `super(schema, fieldTransform(sortOrder))`，复制构造器调 `super(toCopy)` 并复制两个领域字段，提供 `copy()`。静态工厂 `fieldTransform(SortOrder)` 把 `sortOrder.fields()`（`List<SortField>`）映射成 `List<FieldTransform>`，取 `sortField.sourceId()` 和 `sortField.transform()`。这就是 `PartitionKey` 模式在排序域的直接复用——`StructTransform` 抽象的价值正体现在这里：新增 `SortKey` 几乎是零成本的模板复制。

### `api/src/main/java/org/apache/iceberg/SortOrderComparators.java`（新增）

**修改目的**：基于 `SortKey` 提供一个 `Comparator<StructLike>` 工厂，按表的 `SortOrder`（含方向和 null 顺序）比较两行数据。

**工作逻辑**：公开静态入口 `forSchema(Schema schema, SortOrder sortOrder)`：先 `Preconditions.checkArgument(sortOrder.isSorted(), ...)`，再 `SortOrder.checkCompatibility(sortOrder, schema)` 校验兼容性，最后返回一个 `new SortOrderComparator(schema, sortOrder)`。

私有静态工具 `sortFieldComparator(Comparator<Object> original, SortField sortField)` 把"原始类型比较器"链上方向和 null 顺序：
- 若 `sortField == null`：返回 `Comparators.nullsFirst().thenComparing(comparator)`（防御性默认 nulls first）；
- 若 `direction() == DESC`：`comparator = comparator.reversed()`；
- 若 `nullOrder() == NULLS_FIRST`：`Comparators.nullsFirst().thenComparing(comparator)`；
- 若 `nullOrder() == NULLS_LAST`：`Comparators.nullsLast().thenComparing(comparator)`。

内部类 `SortOrderComparator implements Comparator<StructLike>` 持有：
- 两个可复用的 `SortKey`：`leftKey`、`rightKey`——每次比较复用同一对，避免在热路径上分配对象；
- `Comparator<Object>[] comparators` 和 `Type[] transformResultTypes`：构造时对每个 `SortField` 计算 `transform.getResultType(field.type())`，校验其必须是 primitive type，从 `Comparators.forType(...)` 取基础比较器，再用 `sortFieldComparator` 链上方向/null 顺序。

`compare(StructLike left, StructLike right)`：先 `left == right` 短路返回 0；分别 `leftKey.wrap(left)`、`rightKey.wrap(right)`（这就是真正"做变换"的地方）；然后按字段顺序循环，用 `transformResultTypes[i].typeId().javaClass()` 取出每一边的变换后值，调 `comparators[i].compare(...)`，遇到第一个非零结果立即返回；全部相等返回 0。这正是 SQL 排序语义的多字段字典序实现，且每个字段的比较都已带好 ASC/DESC 和 NULLS FIRST/LAST 语义。

### `api/src/test/java/org/apache/iceberg/TestSortOrderComparators.java`（新增）

**修改目的**：为 `SortOrderComparators` 提供全面覆盖的单元测试。

**工作逻辑**：489 行测试，核心是 `assertComparesCorrectly(schema, sortOrder, less, greater, lessCopy, nullValue)` 辅助方法——它先断言 `less==less`、`greater==greater`、`less==lessCopy` 都返回 0，再按 `SortDirection` 分支断言 `less vs greater`、`greater vs less`、以及 `nullValue` 在 NULLS_FIRST/LAST 下的符号。覆盖范围：
- 所有 primitive 类型（Boolean、Int、Long、Float、Double、Date、Time、Timestamp with/without zone、String、UUID、Fixed、Binary、Decimal）的 identity 排序，每个类型测 ASC + DESC；
- Transform 排序：`day` 作用于 timestamp、`bucket(4)` 作用于 string/uuid、`truncate(2)` 作用于 binary；
- 结构体字段排序（`location.lat` / `location.long`），含单字段 ASC/DESC 与双字段排序；
- 结构体字段 + transform 组合（`struct.left`/`struct.right` 配 `truncate(2)`）；
- 嵌套两层结构体（`user.location.lat`/`user.location.long`）；
- 多字段排序下，分别测试"只在第一个字段有差异"、"只在第二个字段有差异"、"两个字段都有差异"、"两个都为 null"等情形。

测试既验证了 `SortOrderComparators` 本身的正确性，也间接验证了 `SortKey`/`StructTransform` 这条新链路在多种 schema 和 transform 组合下都能正确产出可比较的元组。

## 小结

这次提交通过抽取 `StructTransform` 基类把"变换后扁平元组"机制从 `PartitionKey` 中解耦出来，消除了 `PartitionKey` 与（新增的）`SortKey` 之间的重复，并顺势补齐了 `SortOrderComparators` 这一 API 层基础设施，让 Iceberg 任何组件都能按表的 `SortOrder` 直接比较两行数据，为后续 Flink/Spark source 的排序与水印对齐场景打下基础。
