# 提交 3533：Core, API, Spark: Add FileContent.fromId (#15953)

## 提交信息

- **序号**：3533 / 4088
- **哈希**：dfe16c1abc188ebb09d956976b4eb87f00cdc477
- **短哈希**：dfe16c1ab
- **日期**：2026-04-15 08:03:16 -0700
- **作者**：Anoop Johnson
- **提交说明**：Core, API, Spark: Add FileContent.fromId (#15953)
- **PR/Issue**：#15953

## 总体目的

`FileContent` 是 Iceberg 中表示文件内容类型的枚举（DATA=0, POSITION_DELETES=1, EQUALITY_DELETES=2, DATA_MANIFEST=3, DELETE_MANIFEST=4），其 id 与枚举序数对应。在多处代码中需要根据整数 id 反查 `FileContent` 枚举值，例如读取 manifest 文件时把序列化的整数 content id 转回 `FileContent`。

此前这个「id → 枚举」的查找逻辑被复制在多个地方：`BaseFile` 中有 `private static final FileContent[] FILE_CONTENT_VALUES = FileContent.values()`，4 个 Spark 版本的 `SparkContentFile` 中各有一份相同的缓存数组，并直接用数组下标访问 `FILE_CONTENT_VALUES[(Integer) value]`。这种重复代码不便于维护，也违反了「封装」原则——id 到枚举的映射逻辑应属于 `FileContent` 自身。

本提交在 `FileContent` 中新增 `fromId(int id)` 静态方法，集中提供 id→枚举的查找能力，并清理各模块中的重复缓存数组，统一调用 `FileContent.fromId(...)`。

## 如何达成设计目的

在 `FileContent` 枚举中：
1. 新增 `private static final FileContent[] VALUES = FileContent.values();` 缓存枚举数组（避免每次调用 `values()` 都克隆数组）
2. 新增 `public static FileContent fromId(int id) { return VALUES[id]; }`，通过数组下标 O(1) 查找

由于 `FileContent` 的 id 与 `ordinal()` 一致（DATA=0, POSITION_DELETES=1, ...），可以直接用 id 作数组下标。这是一种高效的查找方式，但要求 id 连续且从 0 开始，当前枚举定义满足此条件。

然后在 `BaseFile` 和 4 个 Spark 版本的 `SparkContentFile` 中删除各自的 `FILE_CONTENT_VALUES` 数组，把 `FILE_CONTENT_VALUES[(Integer) value]` 替换为 `FileContent.fromId((Integer) value)`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/FileContent.java` (+6/-0 lines)

**修改目的**：新增 `fromId` 静态方法和缓存数组。

**工作逻辑**：
```java
private static final FileContent[] VALUES = FileContent.values();
...
public static FileContent fromId(int id) {
  return VALUES[id];
}
```
`VALUES` 在类加载时初始化一次，`fromId` 直接按下标访问，O(1) 复杂度。

### `api/src/test/java/org/apache/iceberg/TestFileContent.java` (+48/-0 lines, new file)

**修改目的**：为 `fromId` 添加单元测试。

**工作逻辑**：
```java
@ParameterizedTest
@EnumSource(FileContent.class)
void fromId(FileContent content) {
  assertThat(FileContent.fromId(content.id())).isEqualTo(content);
}

static IntStream invalidContentTypeIds() {
  return IntStream.of(-1, FileContent.values().length);
}

@ParameterizedTest
@MethodSource("invalidContentTypeIds")
void fromIdInvalid(int id) {
  assertThatThrownBy(() -> FileContent.fromId(id))
      .isInstanceOf(ArrayIndexOutOfBoundsException.class)
      .hasMessageContaining(String.valueOf(id));
}
```
正向测试：每个枚举值的 id 都能正确反查。反向测试：-1 和越界 id 抛出 `ArrayIndexOutOfBoundsException`（这是数组下标访问的自然行为，明确文档化）。

### `core/src/main/java/org/apache/iceberg/BaseFile.java` (+1/-3 lines)

**修改目的**：用 `FileContent.fromId` 替换本地缓存数组。

**工作逻辑**：
- 删除 `private static final FileContent[] FILE_CONTENT_VALUES = FileContent.values();`
- `this.content = value != null ? FILE_CONTENT_VALUES[(Integer) value] : FileContent.DATA;` 改为 `this.content = value != null ? FileContent.fromId((Integer) value) : FileContent.DATA;`

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java` (+1/-3 lines)

**修改目的**：Spark 3.4 版本替换为 `FileContent.fromId`。

**工作逻辑**：删除本地 `FILE_CONTENT_VALUES` 数组，`FILE_CONTENT_VALUES[wrapped.getInt(fileContentPosition)]` 改为 `FileContent.fromId(wrapped.getInt(fileContentPosition))`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java` (+1/-3 lines)

**修改目的**：Spark 3.5 版本同步替换（与 v3.4 相同改动）。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java` (+1/-3 lines)

**修改目的**：Spark 4.0 版本同步替换。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java` (+1/-3 lines)

**修改目的**：Spark 4.1 版本同步替换。

## 总结

本提交在 `FileContent` 枚举中新增 `fromId(int)` 静态方法，集中提供「整数 id → 枚举值」的查找能力，消除了 `BaseFile` 和 4 个 Spark 版本 `SparkContentFile` 中重复的缓存数组和查找逻辑。属于 API 收敛与代码去重复的小型重构，配有完整的参数化单元测试覆盖正向和反向用例。
