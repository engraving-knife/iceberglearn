# 提交 3290：Spark 4.1: Simplify time travel option extraction in IcebergSource (#15375)

## 提交信息

- **序号**：3290 / 4088
- **哈希**：cb0c9ccd40f1989450782d95a55d5e6aee26cb23
- **短哈希**：cb0c9ccd4
- **日期**：2026-02-19
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Simplify time travel option extraction in IcebergSource (#15375)
- **PR/Issue**：#15375

## 总体目的

`IcebergSource` 是 Spark 4.1 的表数据源入口，实现了 Spark 的 `extractTimeTravelVersion` 与 `extractTimeTravelTimestamp` 两个钩子方法，用于从读取选项中提取"时间旅行"（time travel）参数——即 `version-as-of`（按快照 ID 查询）与 `timestamp-as-of`（按时间戳查询）。这两个方法决定了 Spark 在构建扫描时回溯到哪个历史快照。

重构前，这两个方法通过 Iceberg 的 `PropertyUtil.propertyAsString(options, key, null)` 来取值。`PropertyUtil.propertyAsString` 是一个通用的 Map 取值工具，内部对 `Map<String, String>` 调用 `map.get(key)` 并在缺失时返回默认值。然而这里传入的 `options` 实际类型是 Spark 的 `CaseInsensitiveStringMap`，它本身已经实现了大小写不敏感的键查找（Spark 官方契约：该 Map 的所有访问都是大小写不敏感的）。因此再套一层 Iceberg 的 `PropertyUtil` 既冗余又增加了一处不必要的依赖与间接层。

本提交将其简化为直接调用 `options.get(SparkReadOptions.VERSION_AS_OF)` 与 `options.get(SparkReadOptions.TIMESTAMP_AS_OF)`，因为 `CaseInsensitiveStringMap.get` 本身就是大小写不敏感的，与原先行为完全等价，同时移除了不再使用的 `PropertyUtil` 导入。这是 Spark 4.1 适配链中的一处小幅代码清理，目的是减少冗余工具调用、降低对 Iceberg 通用工具的依赖、让代码意图更直白。

## 如何达成设计目的

直接把 `Optional.ofNullable(PropertyUtil.propertyAsString(options, key, null))` 替换为 `Optional.ofNullable(options.get(key))`，并删除 `PropertyUtil` 的 import。利用 `CaseInsensitiveStringMap` 自身的大小写不敏感特性保证语义不变。改动仅涉及 `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java` 一个文件的两个方法。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java` (+2/-5 lines)

**修改目的**：简化时间旅行选项的提取，去除冗余工具调用。

**工作逻辑**：
- 移除 `import org.apache.iceberg.util.PropertyUtil;`。
- `extractTimeTravelVersion(options)`：从 `Optional.ofNullable(PropertyUtil.propertyAsString(options, SparkReadOptions.VERSION_AS_OF, null))` 改为 `Optional.ofNullable(options.get(SparkReadOptions.VERSION_AS_OF))`。`CaseInsensitiveStringMap.get` 已是大小写不敏感，与 `PropertyUtil` 行为一致，缺失时返回 null，由 `Optional.ofNullable` 包装为空 Optional。
- `extractTimeTravelTimestamp(options)`：同理，`options.get(SparkReadOptions.TIMESTAMP_AS_OF)`。

文件中另一处 `propertyAsLong` 私有方法仍保留（未在本次改动范围），因为它服务于其它逻辑且本次只聚焦时间旅行提取的简化。

## 总结

本提交通过直接利用 Spark `CaseInsensitiveStringMap` 的大小写不敏感特性，移除了 `IcebergSource` 时间旅行选项提取中对 `PropertyUtil` 的冗余调用，在保持行为完全等价的前提下简化了代码并减少依赖，属于 Spark 4.1 适配工作的细微清理。
