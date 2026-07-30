# 提交 2091：Core: Disallow creation of invalid PartitionSpec (#12887)

## 提交信息

- **序号**：2091 / 4088
- **哈希**：9fb80b7167e58a2daef80f4a1a09f223b870c030
- **短哈希**：9fb80b716
- **日期**：2025-05-07 09:05:42 +0200
- **作者**：Devin Smith <devinsmith@deephaven.io>
- **提交说明**：Core: Disallow creation of invalid PartitionSpec (#12887)\n\n* Core: Disallow creation of invalid PartitionSpec\n\nCloses #12870\n\n* review response
- **PR/Issue**：#12887（关闭 #12870）

## 总体目的

Iceberg 的分区字段（`PartitionField`）通过 `sourceId` 引用 schema 中某个字段，并对其应用一个 `Transform`。问题在于：现有 `PartitionSpec.checkCompatibility` 只校验了"叶子字段类型是否与 transform 兼容"，却没有校验"从根到该叶子字段的路径上，所有父节点是否都是 struct 类型"。

这意味着，当用户对 list/map 内部的字段（例如 `MyList.element`、`MyMap.key`、`MyMap.value`，甚至 `MyMap.key.Foo`、`MyList.element.Foo` 这种嵌套结构里的字段）创建分区字段时，校验可以通过，但这样的分区 spec 实际上是无效的——Iceberg 的分区 transform 不支持对 list/map 元素进行分区，运行期会以更隐晦的方式失败。

本次提交在 `checkCompatibility` 中新增了"父节点类型校验"：从分区字段的 `sourceId` 出发，沿 schema 父子关系逐层向上，要求每一层父节点都是 `StructType`，否则抛出 `ValidationException`，从而在 spec 构建期就阻止无效分区 spec 的产生。

## 如何达成设计目的

1. 利用 `TypeUtil.indexParents(schema.asStruct())` 一次性构建 `Map<Integer, Integer>`（字段 id -> 父字段 id），用于在 O(1) 时间内向上查找父节点。
2. 在 `checkCompatibility` 循环中，对每个 `PartitionField`，从其 `sourceId` 开始，通过 `parents` map 逐层向上回溯父节点 id：
   - 用 `schema.findType(parentId)` 取得父类型；
   - 调用 `parentType.isStructType()` 判断；
   - 非结构类型则抛 `ValidationException`，提示 `Invalid partition field parent: <类型>`。
3. 递归向上直到 `parentId == null`（到达根），保证嵌套 struct（如 `Outer.Inner.id`）依然合法。
4. 新增多个测试覆盖：sourceId 不存在、struct/嵌套 struct 中字段合法、list/map 及其嵌套 struct 中字段非法等场景。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java` (修改, +11/-0 lines)

**修改目的**：在校验分区 spec 兼容性时，禁止分区字段位于 list/map 等非 struct 容器内部。

**工作逻辑**：
在 `checkCompatibility(PartitionSpec spec, Schema schema)` 方法开头，先调用 `TypeUtil.indexParents(schema.asStruct())` 得到字段 id 到父字段 id 的映射 `parents`。然后在每个 `PartitionField` 的循环体内，已有的"source 类型与 transform 兼容性"校验之后，新增一段父类型递归校验：
```java
Integer parentId = parents.get(field.sourceId());
while (parentId != null) {
  Type parentType = schema.findType(parentId);
  ValidationException.check(
      parentType.isStructType(), "Invalid partition field parent: %s", parentType);
  parentId = parents.get(parentId);
}
```
只要某层父节点不是 struct（例如 list、map），立即抛 `ValidationException`；若一路向上都是 struct，则校验通过，从而允许 `Outer.Inner.id` 这样的嵌套 struct 字段作为分区源。

### `api/src/test/java/org/apache/iceberg/TestPartitionSpecValidation.java` (修改, +101/-0 lines)

**修改目的**：覆盖新增的父类型校验逻辑。

**工作逻辑**：新增 6 个测试用例：
- `testSourceIdNotFound`：构造一个不存在的 sourceId，预期抛 `Cannot find source column for partition field`。
- `testPartitionFieldInStruct`：分区字段位于 struct 内（`MyStruct.id`），合法，应构建成功。
- `testPartitionFieldInStructInStruct`：分区字段位于嵌套 struct 内（`Outer.Inner.id`），合法，应构建成功——验证递归向上回溯不会误判。
- `testPartitionFieldInList`：分区字段为 list 的 element（`MyList.element`），父类型为 `list<int>`，预期抛 `Invalid partition field parent: list<int>`。
- `testPartitionFieldInStructInList`：list 元素是 struct，对 struct 内字段分区（`MyList.element.Foo`），父类型为 `list<struct<...>>`，预期抛错。
- `testPartitionFieldInMap`：对 map 的 key/value 分区，父类型为 `map<int, int>`，预期抛错。
- `testPartitionFieldInStructInMap`：map 的 key/value 为 struct，对 struct 内字段分区（`MyMap.key.Foo`、`MyMap.value.Bar`），父类型为 `map<struct<...>, struct<...>>`，预期抛错。

## 总结

本次提交修复了一个分区 spec 校验漏洞：此前对 list/map 内部字段创建分区字段能通过校验但运行期会出问题。通过在 `PartitionSpec.checkCompatibility` 中递归校验所有父节点必须是 struct 类型，把错误前置到 spec 构建期，并通过丰富的测试覆盖了合法（struct/嵌套 struct）与非法（list/map 及其嵌套）场景。改动小而精准，仅 11 行核心代码 + 101 行测试。
