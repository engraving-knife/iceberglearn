# 提交 1552 fc923b3af 分析

## 提交信息

- **哈希**：fc923b3af65b0e3cb28a9afb69f7fd05c88f62ca
- **短哈希**：fc923b3af
- **日期**：2025-01-06（Mon Jan 6 15:32:06 2025 -0600）
- **作者**：abharath9 <abharath9@gmail.com>（Co-authored-by: Bharath Kumar Avusherla <bavusherla@expediagroup.com>）
- **消息**：replace legacy converter with new (#11838)

## 总体目的

Iceberg 的 Flink v1.20 集成模块中，`RowConverter` 类负责把 Flink 内部二进制优化的 `RowData` 转换为外部 `Row` 对象（供下游 non-Table API 用户使用）。该转换器在构造时需要根据 Iceberg schema 生成 `RowTypeInfo`（Flink 用于序列化/分发 `Row` 的类型信息）。

之前的实现使用 Flink `TableSchema.getFieldTypes()` 直接获取 `TypeInformation[]`。这是 Flink 早期 API 的产物——`getFieldTypes()` 内部只是把 schema 的 `DataType` 列表降级转换成旧的 `TypeInformation`。在 Flink 较新版本中，`getFieldTypes()` 已经被标记为 `@Deprecated`，并且推荐使用 `getFieldDataTypes()` 拿到 `DataType[]` 后通过新的桥接 API 转换到 `TypeInformation`。这条迁移路径既避免使用废弃 API，也能保留更完整的类型信息（例如 nullable、conversion class 等附加属性），减少信息损失。

本次提交就是把 `RowConverter.fromIcebergSchema` 中的 `RowTypeInfo` 构造逻辑从"使用废弃的 `getFieldTypes()`"切换到"使用 `getFieldDataTypes()` + `ExternalTypeInfo.of(DataType)`"，从而对齐 Flink 推荐的类型信息构造方式，规避后续 Flink 升级带来的 API 移除风险。

## 如何达成设计目的

修改集中在 `RowConverter.fromIcebergSchema` 方法。原实现一步到位地从 `tableSchema.getFieldTypes()` 获取 `TypeInformation[]`，再传给 `RowTypeInfo` 构造器。新实现拆成两步：先用 `tableSchema.getFieldDataTypes()` 拿到 `DataType[]`，再用 `Stream` 把每个 `DataType` 通过 `ExternalTypeInfo.of(DataType)` 转回 `TypeInformation`，最后装配成 `RowTypeInfo`。

`ExternalTypeInfo` 是 Flink 提供的、专门用于把"外部"（即非 Flink 内部紧凑表示）的 `DataType` 桥接到 `TypeInformation` 的适配器。它保留了 `DataType` 上的可空性等附加属性，比旧的 `getFieldTypes()` 路径更精确。`apply` 方法本身的转换逻辑没有改变，仍是 `DataStructureConverters.getConverter(...).toExternal(rowData)`——只是 `TypeInformation` 的来源换成了新 API。

### 修改详情

#### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowConverter.java`

**修改目的**：替换 Flink 已废弃的 `TableSchema.getFieldTypes()` 调用，改用 `getFieldDataTypes()` + `ExternalTypeInfo.of(DataType)` 桥接路径。

**新增 import**：
- `java.util.stream.Stream`：用于把 `DataType[]` 流式映射成 `TypeInformation[]`。
- `org.apache.flink.table.runtime.typeutils.ExternalTypeInfo`：新桥接器类型。

**工作逻辑**：

```java
// 旧实现
RowTypeInfo rowTypeInfo =
    new RowTypeInfo(tableSchema.getFieldTypes(), tableSchema.getFieldNames());

// 新实现
TypeInformation[] typeInformations =
    Stream.of(tableSchema.getFieldDataTypes())
        .map(ExternalTypeInfo::of)
        .toArray(TypeInformation[]::new);
RowTypeInfo rowTypeInfo = new RowTypeInfo(typeInformations, tableSchema.getFieldNames());
```

`Stream.of(tableSchema.getFieldDataTypes())` 把 `DataType[]` 包装为流，`.map(ExternalTypeInfo::of)` 把每个 `DataType` 转为 `ExternalTypeInfo`（它本身是 `TypeInformation` 的子类），最后 `.toArray(TypeInformation[]::new)` 收集成数组。最终传给 `RowTypeInfo` 的构造器，与字段名数组对齐，行为与旧实现等价，但不再依赖已废弃的 API。

## 小结

- **成效**：`RowConverter` 不再使用 Flink 已废弃的 `TableSchema.getFieldTypes()`，迁移到 `getFieldDataTypes()` + `ExternalTypeInfo` 路径，避免未来 Flink 升级时该 API 被移除带来的编译失败；同时保留更完整的类型信息。
- **影响范围**：仅 `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowConverter.java` 一个文件，新增 7 行删除 2 行，运行时行为等价。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支配套的 Flink 版本范围一般低于 v1.20，且 `RowConverter` 是 Flink v1.20 才引入的 source reader 代码路径，1.4.x 不一定包含此文件。即便包含，只要 1.4.x 对应的 Flink 版本中 `getFieldTypes()` 尚未被废弃，迁移就只是"可选的代码质量改进"而非"必须修复的兼容性问题"。因此**回迁价值有限**，建议按 1.4.x 实际匹配的 Flink 版本评估；若 1.4.x 的 Flink 依赖中 `ExternalTypeInfo` 已存在且 `getFieldTypes()` 已废弃，则可顺带回迁以保持代码一致性。
