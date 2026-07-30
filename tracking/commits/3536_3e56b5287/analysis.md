# 提交 3536：Core: Add fromId to EntryStatus and ManifestEntry.Status (#15983)

## 提交信息

- **序号**：3536 / 4088
- **哈希**：3e56b5287c24806f42dd1835cb8ffe94159a36c5
- **短哈希**：3e56b5287
- **日期**：2026-04-15 10:58:17 -0700
- **作者**：Anoop Johnson
- **提交说明**：Core: Add fromId to EntryStatus and ManifestEntry.Status (#15983)
- **PR/Issue**：#15983

## 总体目的

这是继 #15953（提交 3533，为 `FileContent` 添加 `fromId`）之后的代码清理延续。`EntryStatus` 和 `ManifestEntry.Status` 两个枚举同样存在「整数 id → 枚举值」的反查需求，但此前反查逻辑分散在调用方：`GenericManifestEntry` 中有 `private static final Status[] STATUS_VALUES = Status.values()` 并用 `STATUS_VALUES[(Integer) v]` 查找，`GenericManifestFile` 中有 `MANIFEST_CONTENT_VALUES` 数组（虽然 `ManifestContent.fromId` 已存在，但 `GenericManifestFile` 仍用本地数组而非调用 `fromId`）。

本提交将 id→枚举的查找能力下沉到枚举自身：为 `EntryStatus` 和 `ManifestEntry.Status` 新增 `fromId(int)` 静态方法（用缓存的 `values()` 数组按下标查找），并更新调用方使用这些方法，删除本地的重复缓存数组。同时顺手让 `GenericManifestFile` 改用已有的 `ManifestContent.fromId` 而非本地数组。这是封装与去重复的清理。

## 如何达成设计目的

在每个枚举中：
1. 新增 `private static final XxxStatus[] VALUES = XxxStatus.values();` 缓存数组
2. 新增 `static XxxStatus fromId(int id) { return VALUES[id]; }` 方法（package-private，因为枚举本身是 package-private）

由于这些枚举的 id 与 `ordinal()` 一致（`EntryStatus`: EXISTING=0, ADDED=1, DELETED=2, REPLACED=3；`ManifestEntry.Status`: EXISTING=0, ADDED=1, DELETED=2），可以用 id 作数组下标 O(1) 查找。

然后更新 `GenericManifestEntry` 和 `GenericManifestFile` 删除本地 `STATUS_VALUES`/`MANIFEST_CONTENT_VALUES` 数组，改为调用 `Status.fromId(...)`/`ManifestContent.fromId(...)`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/EntryStatus.java` (+6/-0 lines)

**修改目的**：为 `EntryStatus` 枚举新增 `fromId` 方法和缓存数组。

**工作逻辑**：
```java
private static final EntryStatus[] VALUES = EntryStatus.values();
...
static EntryStatus fromId(int id) {
  return VALUES[id];
}
```
`EntryStatus` 是 package-private 枚举，故 `fromId` 也为 package-private（无 `public` 修饰符）。

### `core/src/main/java/org/apache/iceberg/ManifestEntry.java` (+6/-0 lines)

**修改目的**：为内嵌枚举 `ManifestEntry.Status` 新增 `fromId` 方法和缓存数组。

**工作逻辑**：
```java
private static final Status[] VALUES = Status.values();
...
static Status fromId(int id) {
  return VALUES[id];
}
```
`Status` 是 `ManifestEntry` 接口内的内嵌枚举。

### `core/src/main/java/org/apache/iceberg/GenericManifestEntry.java` (+1/-2 lines)

**修改目的**：删除本地 `STATUS_VALUES` 数组，改用 `Status.fromId`。

**工作逻辑**：
- 删除 `private static final Status[] STATUS_VALUES = Status.values();`
- `this.status = STATUS_VALUES[(Integer) v];` → `this.status = Status.fromId((Integer) v);`

### `core/src/main/java/org/apache/iceberg/GenericManifestFile.java` (+1/-3 lines)

**修改目的**：删除本地 `MANIFEST_CONTENT_VALUES` 数组，改用已有的 `ManifestContent.fromId`。

**工作逻辑**：
- 删除 `private static final ManifestContent[] MANIFEST_CONTENT_VALUES = ManifestContent.values();`
- `MANIFEST_CONTENT_VALUES[(Integer) value]` → `ManifestContent.fromId((Integer) value)`
- 注：`ManifestContent.fromId` 此前已存在（用 switch 实现），只是 `GenericManifestFile` 没有使用它，本提交顺手统一。

### `core/src/test/java/org/apache/iceberg/TestEntryStatus.java` (+48/-0 lines, new file)

**修改目的**：为 `EntryStatus.fromId` 添加单元测试。

**工作逻辑**：
```java
@ParameterizedTest
@EnumSource(EntryStatus.class)
void fromId(EntryStatus status) {
  assertThat(EntryStatus.fromId(status.id())).isEqualTo(status);
}

static IntStream invalidIds() {
  return IntStream.of(-1, EntryStatus.values().length);
}

@ParameterizedTest
@MethodSource("invalidIds")
void fromIdInvalid(int id) {
  assertThatThrownBy(() -> EntryStatus.fromId(id))
      .isInstanceOf(ArrayIndexOutOfBoundsException.class)
      .hasMessageContaining(String.valueOf(id));
}
```
正向验证每个枚举值的 id 反查，反向验证 -1 和越界 id 抛 `ArrayIndexOutOfBoundsException`。

### `core/src/test/java/org/apache/iceberg/TestManifestEntryStatus.java` (+48/-0 lines, new file)

**修改目的**：为 `ManifestEntry.Status.fromId` 添加单元测试。

**工作逻辑**：与 `TestEntryStatus` 结构完全相同，针对 `ManifestEntry.Status` 枚举。

## 总结

本提交是 #15953（`FileContent.fromId`）清理工作的延续，为 `EntryStatus` 和 `ManifestEntry.Status` 两个枚举新增 `fromId` 静态方法，将 id→枚举的查找逻辑下沉到枚举自身，并清理 `GenericManifestEntry` 和 `GenericManifestFile` 中的重复缓存数组。同时让 `GenericManifestFile` 改用已有的 `ManifestContent.fromId`。属于一致的封装性代码清理，配有参数化单元测试覆盖正反向用例。
