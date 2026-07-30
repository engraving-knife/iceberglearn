# 提交 0062：Core: Do not use a lazy split offset list in manifests (#8834)

## 提交信息

- **序号**：0062 / 4088
- **哈希**：46cad6ddaeff8104d96defab25206a4ff7e01629
- **短哈希**：46cad6dda
- **日期**：2023-10-16
- **作者**：Bryan Keller
- **提交说明**：Core: Do not use a lazy split offset list in manifests (#8834)
- **PR/Issue**：#8834

## 总体目的

该提交修复了 `BaseFile` 中 split offset 列表懒加载缓存的一个失效缺陷。提交说明直言："The list was not correctly invalidated when reusing the file."——即当 `BaseFile` 实例被复用时（例如从 Avro 反序列化得到、或在不同 manifest 读取上下文之间共享同一个对象），缓存的 `splitOffsetList` 不会被刷新，从而可能返回与当前 `splitOffsets` 数组不一致的旧数据。

`BaseFile` 是 Iceberg manifest 文件中每条数据/删除文件元数据的载体，`splitOffsets()` 返回该数据文件的可切分偏移列表（用于 Spark/Flink 等引擎做 split 划分、并行读取）。如果这个列表返回了陈旧或与实际文件不符的偏移量，引擎会按错误的偏移去 seek 数据文件，轻则读取失败、报 `EOFException`，重则读到错误的行（偏移落在记录中间），是潜在的数据正确性风险。

该缺陷之所以现在才暴露，是因为某些场景（例如 manifest 复用、或在测试中多次读取同一 manifest）会让同一个 `BaseFile` 对象的 `splitOffsets` 字段被重新填充（通过 Avro 反射的 `put` 路径，见下文），而懒缓存的 `splitOffsetList` 仍指向第一次填充时构建的不可变列表，二者脱节。修复方案是直接去掉这层懒缓存，每次调用都从底层数组现算一个不可变视图——`ArrayUtil.toUnmodifiableLongList` 本身已是轻量操作（基于 `AbstractList` 的装箱视图，不复制数组），去掉缓存的性能损失可忽略，但换来正确性上的确定性。

## 如何达成设计目的

整体思路是"删除缓存，回归直算"：

1. 在 `BaseFile` 中删除 `splitOffsetList` 字段与懒加载逻辑，`splitOffsets()` 直接调用 `ArrayUtil.toUnmodifiableLongList(splitOffsets)` 返回。
2. 在测试侧补强覆盖：给 `TableTestBase` 中的几个删除文件常量（`FILE_A_DELETES`、`FILE_B_DELETES`、`FILE_C2_DELETES`、`FILE_D2_DELETES`）补上 `withSplitOffsets(...)`，让测试 fixture 真实带有 split offset 数据。
3. 把 `TestManifestReader#readManifestWithFilter` 测试从"只比较文件路径字符串"升级为"递归比较整个 `DataFile` 对象"（用 AssertJ 的 `usingRecursiveComparison`），这样 split offset 字段也进入比较范围，从而能捕获到本缺陷这类字段级偏差。

## 修改详情

### [core/src/main/java/org/apache/iceberg/BaseFile.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/BaseFile.java)

**修改目的**：移除有缺陷的懒加载缓存，让 `splitOffsets()` 每次都从底层数组现算不可变列表。

**工作逻辑**：原本在类中有一段"lazy variables"区：

```java
// lazy variables
private transient volatile List<Long> splitOffsetList = null;
```

`splitOffsets()` 方法在第一次被调用时把 `long[] splitOffsets` 装箱成不可变 `List<Long>` 并缓存到 `splitOffsetList`，后续直接返回缓存：

```java
@Override
public List<Long> splitOffsets() {
  if (splitOffsetList == null && splitOffsets != null) {
    this.splitOffsetList = ArrayUtil.toUnmodifiableLongList(splitOffsets);
  }
  return splitOffsetList;
}
```

问题在于：`BaseFile` 通过 Avro 反射读取 manifest，`splitOffsets` 字段可能在对象生命周期中被重新填充（例如 manifest 复用、或通过 `put(int pos, Object value)` 路径——见 case 14 处 `return splitOffsets()`，以及 `case 14` 之外的写入路径 `this.splitOffsets = ArrayUtil.toLongArray((List<Long>) value)`）。但懒缓存 `splitOffsetList` 仅在为 `null` 时才重建，一旦第一次填充后就再也不更新，于是会返回与当前 `splitOffsets` 不一致的数据。

修复删除了字段声明，并把方法体改为直算：

```java
@Override
public List<Long> splitOffsets() {
  return ArrayUtil.toUnmodifiableLongList(splitOffsets);
}
```

`ArrayUtil.toUnmodifiableLongList(long[])` 是一个轻量的装箱视图（基于 `AbstractList`，不复制底层数组），每次调用开销极小，且天然保证与 `splitOffsets` 数组当前内容一致，消除了缓存一致性问题。`volatile` 关键字也随字段一起删除，因为不再有跨线程可见的共享可变状态。

### [core/src/test/java/org/apache/iceberg/TableTestBase.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/test/java/org/apache/iceberg/TableTestBase.java)

**修改目的**：为测试 fixture 中的删除文件补上 split offset 数据，使后续 manifest 读取测试能验证 split offset 字段。

**工作逻辑**：为 `FILE_A_DELETES`、`FILE_B_DELETES`、`FILE_C2_DELETES`、`FILE_D2_DELETES` 四个静态常量各加一行 `.withSplitOffsets(...)`：

- `FILE_A_DELETES`：`ImmutableList.of(1L)`
- `FILE_B_DELETES`：`ImmutableList.of(2L, 2_000_000L)`
- `FILE_C2_DELETES`：`ImmutableList.of(3L, 3_000L, 3_000_000L)`
- `FILE_D2_DELETES`：`ImmutableList.of(3L, 3_000L, 3_000_000L)`（与 C2 相同）

这些值本身没有特殊含义，关键是要让 fixture 携带非空 split offset，这样读取 manifest 后再做对象级比较时，split offset 才是被验证的字段之一。

### [core/src/test/java/org/apache/iceberg/TestManifestReader.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/test/java/org/apache/iceberg/TestManifestReader.java)

**修改目的**：把 `readManifestWithFilter` 测试升级为对整个 `DataFile` 对象的递归比较，让 split offset 等所有字段都被纳入校验。

**工作逻辑**：

1. 新增一个 `RecursiveComparisonConfiguration FILE_COMPARISON_CONFIG`，忽略 `dataSequenceNumber`、`fileOrdinal`、`fileSequenceNumber`、`fromProjectionPos` 这几个与序列号/投影位置相关、在 manifest 读取测试中不稳定的字段。
2. 把测试中收集结果的方式从 `map(file -> file.path().toString())` 改为直接收集 `DataFile` 对象：
   ```java
   List<DataFile> files = Streams.stream(reader).collect(Collectors.toList());
   ```
3. 把断言从 JUnit 的 `Assert.assertEquals(...)`（仅比较路径字符串列表）改为 AssertJ 的递归比较：
   ```java
   assertThat(files)
       .usingRecursiveComparison(FILE_COMPARISON_CONFIG)
       .isEqualTo(Lists.newArrayList(FILE_A, FILE_B, FILE_C));
   ```
   这样除被忽略的序列号/投影字段外，所有字段（包括 `splitOffsets`）都会被逐字段比较。这正是能捕获本缺陷的关键：如果 `splitOffsets()` 因缓存失效返回了错误数据，递归比较会立刻失败；而旧的"只比 path"断言根本看不到这个字段。

同时引入了 `import static org.assertj.core.api.Assertions.assertThat;` 与 `RecursiveComparisonConfiguration` 的 import，体现从 JUnit 风格向 AssertJ 风格迁移的趋势。

## 小结

该提交通过移除 `BaseFile.splitOffsets()` 中失效的懒加载缓存，修复了 manifest 复用场景下 split offset 列表可能返回陈旧数据的正确性缺陷，并以递归对象比较强化了测试覆盖，是 Iceberg 元数据读取链路上一次重要的健壮性修复。
