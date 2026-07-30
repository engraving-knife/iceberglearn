# 提交 2388：Core: Implement Map comparator (#13626)

## 提交信息

- **序号**：2388 / 4088
- **哈希**：8dfd9de791ac28d2f349aa016f1846a95576b2c3
- **短哈希**：8dfd9de79
- **日期**：2025-07-23 12:56:23 +0200
- **作者**：pvary
- **提交说明**：Core: Implement Map comparator (#13626)
- **PR/Issue**：#13626

## 总体目的

本提交为 Iceberg 的类型系统实现了 Map 类型的比较器（Comparator）。Iceberg 的 `Comparators` 工具类此前已经支持基本类型、StructType 和 ListType 的比较器，但缺少对 MapType 的支持。

Map 类型比较器的实现使得 Iceberg 能够对包含 Map 字段的记录进行排序和比较，这对于 Map 类型数据的正确处理（如排序、合并、去重等操作）是必要的。此前，如果尝试获取 MapType 的比较器会抛出 `UnsupportedOperationException`，因为 `forType` 方法中没有处理 MapType 的分支。

## 如何达成设计目的

设计思路是新建 `MapComparator` 内部类实现 Map 的比较逻辑，并在 `Comparators` 的工厂方法中注册 MapType 的处理分支。Map 比较的核心挑战是 Map 是无序的键值对集合，需要确定一种一致的比较方式。关键设计点如下：

1. **键排序后比较**：将两个 Map 的键分别提取并排序，先比较键列表（按字典序），如果键列表相同，再按排序后的键顺序逐一比较对应的值。
2. **值可选性处理**：当 Map 的值类型是可选的（nullable）时，使用 `nullsFirst` 策略处理 null 值。
3. **递归类型支持**：Map 的键和值比较器通过 `internal()` 方法递归获取，支持嵌套复杂类型。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Comparators.java` (+53/-0 lines)

**修改目的**：实现 Map 类型的比较器。

**工作逻辑**：
- 新增 `forType(Types.MapType mapType)` 工厂方法，返回 `MapComparator` 实例。
- 在泛型 `forType(Type type)` 方法中增加 `type.isMapType()` 分支，委托给 `forType(type.asMapType())`。
- 新增 `MapComparator<K, V>` 私有静态内部类：
  - 构造函数：从 MapType 获取键类型和值类型的比较器。如果值是可选的，使用 `nullsFirst().thenComparing()` 包装值比较器。键列表比较器通过 `internal(ListType)` 获取。
  - `compare` 方法：首先处理引用相等（`o1 == o2` 返回 0）。然后将两个 Map 的键提取为 List 并排序。先比较排序后的键列表，如果不同直接返回结果。如果键列表相同，按排序后的键顺序逐一比较两个 Map 中对应键的值，返回第一个不相等的比较结果。如果所有键值对都相等，返回 0。

### `api/src/test/java/org/apache/iceberg/types/TestComparators.java` (+63/-0 lines)

**修改目的**：为 Map 比较器添加测试用例。

**工作逻辑**：新增两个测试方法：
- `testMap`：测试 Map 比较器的各种场景：
  - 值不同时按值比较（`{a:1,b:2}` vs `{a:1,b:3}`）
  - 键不同时按键比较（`{a:1,b:2}` vs `{a:1,c:2}`）
  - 键数量不同时按键数量比较（`{a:1}` vs `{a:1,b:2}`）
  - 键顺序不同但内容相同时正确处理（`{a:1,c:3,b:2}` vs `{a:1,b:2,c:4}`，按键排序后比较）
  - 值可选时 null 值使用 nullsFirst 策略（`{a:1,b:null,c:2}` vs `{a:1,b:2,c:3}`）
- `testNested`：测试包含 Map 字段的嵌套 StructType 比较器，验证 Map 比较器在复杂嵌套结构中正确工作（Struct 包含 String、嵌套 Struct、List 和 Map 字段）。

## 总结

本提交为 Iceberg 类型系统实现了 Map 类型的比较器，填补了 Comparators 工具类中 MapType 支持的空白。Map 比较器采用"键排序后逐一比较"的策略，支持可选值（nullsFirst）和嵌套复杂类型。修改涉及 2 个文件，116 行新增代码，包含完整的实现和测试覆盖。该实现使 Iceberg 能够正确比较和排序包含 Map 字段的记录，对于数据处理操作（如排序、合并）具有重要意义。
