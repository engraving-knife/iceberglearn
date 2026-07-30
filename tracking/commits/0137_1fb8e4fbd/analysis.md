# 提交 0137：Core: Add a constructor to StaticTableOperations (#8996)

## 提交信息

- **序号**：0137 / 4088
- **哈希**：1fb8e4fbd11dea3c16e7c16d56d543bee2b53464
- **短哈希**：1fb8e4fbd
- **日期**：2023-11-08 10:41:44 +0100
- **作者**：Wonjae Lee
- **提交说明**：Core: Add a constructor to StaticTableOperations (#8996)
- **PR/Issue**：#8996

## 总体目的

这个提交为 `StaticTableOperations` 增加了一组新的构造函数，允许调用方在已经持有 `TableMetadata` 对象的情况下直接传入，而不必再通过元数据文件路径让 `StaticTableOperations` 自己重新读取一遍。这是一处针对现有 API 的能力补强，同时伴随着两处调用方的迁移，用于验证新构造函数的可用性并消除旧用法中"重复读盘"的浪费。

背景与动机：`StaticTableOperations` 原有的两个构造函数都以 `String metadataFileLocation` 作为入参，内部在 `current()` 首次被调用时通过 `TableMetadataParser.read(io, metadataFileLocation)` 懒加载元数据。这种模式适合"只有一个 metadata.json 路径"的场景。但在许多实际用法里，调用方其实已经通过其他途径拿到过 `TableMetadata` 对象（例如先调用某个 `TableOperations.current()` 得到 `metadata`，再想用静态视图去读取可达到的文件）。

问题在于：旧 API 下，调用方为了构造 `StaticTableOperations` 不得不把已经解析过的元数据"重新转成路径字符串"，然后让 `StaticTableOperations` 在 `current()` 时再去磁盘读一次并反序列化一遍。这不仅是一次多余的 I/O 和反序列化开销，还存在语义风险——如果调用方拿到的 `TableMetadata` 与磁盘上 metadata.json 不一致（例如内存中被修改过、或指向不同快照），重新读盘得到的对象未必就是调用方想要的那个版本。提交里 `TestReachableFileUtil` 的改动就是一个典型例子：原代码先用 `ops.current()` 拿到 `metadata`，却又从 `metadata.metadataFileLocation()` 取路径再传给 `StaticTableOperations`，让后者重新读盘，绕了一圈。

新增的构造函数直接接受 `TableMetadata`，等于把 `StaticTableOperations` 的初始化从"路径 + 懒加载"扩展为"路径 + 懒加载"或"现成元数据 + 立即可用"两种模式，让 API 更贴合实际调用形态，避免重复 I/O，也保证了静态视图与调用方持有的元数据完全一致。这对 Iceberg 演进的意义是让 core 层只读元数据访问的 API 更完整，也为后续基于已有 `TableMetadata` 构建静态表的工具类（如 Spark actions、`ReachableFileUtil` 等）铺平了路径。

## 如何达成设计目的

设计思路是在不破坏既有构造函数的前提下，新增一个接受 `TableMetadata`、`FileIO` 的便捷构造函数，以及一个完整版（额外带 `LocationProvider`）的构造函数；前者委托后者，把 `locationProvider` 设为 `null`。新构造函数直接把传入的 `metadata` 赋给 `staticMetadata` 字段，并从 `metadata.metadataFileLocation()` 取出路径填到 `metadataFileLocation` 字段，使 `current()` 在被调用时不再需要走懒加载分支（因为 `staticMetadata != null`），直接返回已注入的元数据。随后将两处已知会触发重复读盘的调用点（`TestReachableFileUtil`、Spark 3.5 的 `BaseSparkAction.newStaticTable`）迁移到新构造函数，作为新 API 的首个使用样例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/StaticTableOperations.java`

**修改目的**：为 `StaticTableOperations` 增加"直接接受 `TableMetadata`"的构造函数，避免调用方重复读取并反序列化元数据文件。

**工作逻辑**：在原有以 `String metadataFileLocation` 为入参的两个构造函数之后，新增两个构造函数：

- `StaticTableOperations(TableMetadata staticMetadata, FileIO io)`：便捷构造，委托给完整版，传入 `null` 作为 `locationProvider`。
- `StaticTableOperations(TableMetadata staticMetadata, FileIO io, LocationProvider locationProvider)`：完整版，直接把 `staticMetadata`、`io`、`locationProvider` 赋值给字段，并把 `metadataFileLocation` 设为 `staticMetadata.metadataFileLocation()`。

由于 `staticMetadata` 不再为 `null`，`current()` 中的 `if (staticMetadata == null) { staticMetadata = TableMetadataParser.read(...); }` 分支将不会执行，从而彻底跳过磁盘读取。原有构造函数与原有行为保持不变，向后兼容。

### `core/src/test/java/org/apache/iceberg/util/TestReachableFileUtil.java`

**修改目的**：把测试中构造 `StaticTableOperations` 的方式从"传路径、由其重新读盘"迁移到"直接传已经拿到的 `TableMetadata`"，避免重复 I/O，并验证新构造函数的正确性。

**工作逻辑**：原代码先 `TableMetadata metadata = ops.current();` 取出 `metadata`，再 `String metadataFileLocation = metadata.metadataFileLocation();`，然后 `new StaticTableOperations(metadataFileLocation, table.io())` 让静态操作对象在 `current()` 时重新解析同一个 metadata.json。改动后直接 `new StaticTableOperations(metadata, table.io())`，复用已加载的 `metadata`，省掉一次冗余读盘，同时保证静态表视图与 `ops.current()` 指向的是同一份元数据对象。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java`

**修改目的**：迁移 Spark 3.5 `BaseSparkAction.newStaticTable` 到新构造函数，简化实现并消除重复读盘。

**工作逻辑**：原方法体为：

```java
String metadataFileLocation = metadata.metadataFileLocation();
StaticTableOperations ops = new StaticTableOperations(metadataFileLocation, io);
return new BaseTable(ops, metadataFileLocation);
```

改动后为：

```java
StaticTableOperations ops = new StaticTableOperations(metadata, io);
return new BaseTable(ops, metadata.metadataFileLocation());
```

省去中间变量 `metadataFileLocation`，直接用新构造函数注入已加载的 `metadata`，`BaseTable` 的名字仍取 `metadata.metadataFileLocation()`，行为等价但更直接，且不再让 `StaticTableOperations` 重复读盘。

## 小结

为 `StaticTableOperations` 增加直接接受 `TableMetadata` 的构造函数，消除了调用方重复读盘的开销并保证静态视图与已有元数据一致，使 core 层只读元数据访问的 API 更完整、更贴合实际使用形态。
