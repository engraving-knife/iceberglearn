# 提交 3419：Core: Fix useSnapshotSchema logic and projection in RESTTableScan (#15609)

## 提交信息

- **序号**：3419 / 4088
- **哈希**：0a73da119ff38ee3a98f248b42180caa51001cec
- **短哈希**：0a73da119f
- **日期**：2026-03-19 14:56:21 -0700
- **作者**：Prashant Singh
- **提交说明**：Core: Fix useSnapshotSchema logic and projection in RESTTableScan (#15609)
- **PR/Issue**：#15609

## 总体目的

修复 `RESTTableScan` 中两个问题：
1. `useSnapshotSchema` 的判断逻辑不正确——此前通过比较 snapshotId 与 currentSnapshotId 来判断是否使用快照 schema，但正确的判断标准是：branch 为 false，tag/直接 snapshotId 为 true
2. 投影代码只选择了顶层字段名，遗漏了嵌套字段——应使用 `TypeUtil.getProjectedIds()` 匹配 `SnapshotScan` 中的模式

## 如何达成设计目的

1. 新增 `useSnapshotSchema` 布尔字段，在重写的 `useRef()` 和 `useSnapshot()` 方法中设置
2. `useRef(name)`：检查 ref 是否为 tag，若为 tag 则 `useSnapshotSchema = true`
3. `useSnapshot(snapshotId)`：直接设置 `useSnapshotSchema = true`
4. `newRefinedScan` 中将 `useSnapshotSchema` 传播到新实例
5. 投影代码从 `schema().columns().stream().map(Types.NestedField::name)` 改为 `TypeUtil.getProjectedIds(schema())` 再映射为列名

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+51/-16 lines)

**修改目的**：修复 useSnapshotSchema 判断逻辑和投影字段选择。

**工作逻辑**：

**useSnapshotSchema 字段和设置**：
- 新增 `private boolean useSnapshotSchema = false` 字段
- 重写 `useRef(String name)`：`SnapshotRef ref = table().refs().get(name); this.useSnapshotSchema = ref != null && ref.isTag(); return super.useRef(name);`
- 重写 `useSnapshot(long snapshotId)`：`this.useSnapshotSchema = true; return super.useSnapshot(snapshotId);`

**newRefinedScan 传播**：
- 在创建新的 RESTTableScan 后设置 `scan.useSnapshotSchema = useSnapshotSchema`

**投影修复**：
- 原有代码：`schema().columns().stream().map(Types.NestedField::name).collect(Collectors.toList())` — 仅顶层字段
- 修复后：`Lists.newArrayList(TypeUtil.getProjectedIds(schema())).stream().map(schema()::findColumnName).collect(Collectors.toList())` — 包含嵌套字段

**useSnapshotSchema 使用**：
- 原有代码：`boolean useSnapShotSchema = snapshotId != table().currentSnapshot().snapshotId()` — 通过比较 snapshotId 判断
- 修复后：直接使用 `useSnapshotSchema` 字段

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+98 lines)

**修改目的**：新增 useSnapshotSchema 和嵌套投影的测试。

**工作逻辑**：
- 新增测试验证 useRef 为 tag 时 useSnapshotSchema 为 true
- 新增测试验证 useRef 为 branch 时 useSnapshotSchema 为 false
- 新增测试验证 useSnapshot 时 useSnapshotSchema 为 true
- 新增测试验证嵌套字段的投影正确包含在 PlanTableScanRequest 中

## 总结

本提交修复了 RESTTableScan 的两个问题：useSnapshotSchema 的判断从比较 snapshotId 改为基于 ref 类型（tag/snapshotId 为 true，branch 为 false）；投影代码从仅选顶层字段改为使用 `TypeUtil.getProjectedIds()` 包含嵌套字段。新增测试覆盖了各种 ref 类型和嵌套投影场景。
