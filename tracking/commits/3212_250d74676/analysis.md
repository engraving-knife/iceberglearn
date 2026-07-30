# 提交 3212：Core: Remove deprecation from ViewProperties.WRITE_METADATA_LOCATION (#15250)

## 提交信息

- **序号**：3212 / 4088
- **哈希**：250d74676caa6432ef61f9ddd7dea016a7f25d1a
- **短哈希**：250d74676
- **日期**：2026-02-06
- **作者**：gaborkaszab
- **提交说明**：Core: Remove deprecation from ViewProperties.WRITE_METADATA_LOCATION (#15250)
- **PR/Issue**：#15250

## 总体目的

`ViewProperties.WRITE_METADATA_LOCATION`（属性键 `write.metadata.path`）用于指定视图元数据文件的写入位置。它此前被标记为 `@Deprecated`，Javadoc 注明"将在 2.0.0 移除，改用 `ViewBuilder#withLocation`"。然而这一弃用判断是错误的。

`write.metadata.path` 与 `ViewBuilder#withLocation` 解决的是两个不同问题：`withLocation` 设定视图本身的基位置（view location），而 `write.metadata.path` 允许把元数据文件写到与视图位置不同的独立路径上（例如把元数据集中放到单独的目录或存储桶）。在 `BaseViewOperations.metadataFileLocation(...)` 中可见两者区别——若设置了 `WRITE_METADATA_LOCATION`，元数据文件写入该自定义位置；否则默认写入 `{view.location()}/metadata/`。这与表的对应属性 `TableProperties.WRITE_METADATA_LOCATION`（同为 `write.metadata.path`，且**未**被弃用、是受支持的正式属性）完全一致：表同样支持把元数据文件存储与表位置解耦。

此外该属性在生产代码中确有使用（`BaseViewOperations` 用它决定元数据文件路径），并在多个 Spark 版本与 `ViewCatalogTests` 的测试中被引用。继续保留弃用标记会误导用户以为它将在 2.0.0 被移除、应迁移到 `withLocation`，但后者并不能覆盖"元数据文件独立存放"这一用例。因此本提交移除 `@Deprecated` 注解与弃用说明，将其恢复为受支持的正式属性。

## 如何达成设计目的

改动极小：在 `ViewProperties.java` 中删除 `WRITE_METADATA_LOCATION` 常量上的 `@Deprecated` 注解及 `@deprecated` Javadoc 段落，保留常量定义本身不变。这向用户与下游引擎传递明确信号——该属性是视图属性 API 的正式组成部分，不计划移除，可放心使用；同时与 `TableProperties` 中的同名属性保持一致的待遇。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewProperties.java` (+1/-4)

**修改目的**：撤销对 `WRITE_METADATA_LOCATION` 的弃用标记，恢复其为正式支持属性。

**工作逻辑**：
原定义包含 Javadoc 弃用说明与注解：

```
/**
 * @deprecated will be removed in 2.0.0, use {@link ViewBuilder#withLocation} instead.
 */
@Deprecated public static final String WRITE_METADATA_LOCATION = "write.metadata.path";
```

改为去掉弃用说明与注解，仅保留常量：

```
public static final String WRITE_METADATA_LOCATION = "write.metadata.path";
```

常量值与属性键不变，因此 `BaseViewOperations.metadataFileLocation(...)` 中读取该属性决定元数据文件写入路径的逻辑不受影响，行为完全一致；变化的仅是该属性的 API 地位——由"待移除"转为"正式支持"。这与 `TableProperties.WRITE_METADATA_LOCATION`（同键 `write.metadata.path`、从未弃用）保持一致，体现视图与表在元数据存储位置可配置性上的对等。

## 总结

本提交纠正了对 `ViewProperties.WRITE_METADATA_LOCATION` 的误弃用：该属性用于将视图元数据文件存放路径与视图位置解耦，是 `ViewBuilder#withLocation` 无法替代的独立能力，且在生产代码中实际使用、并与表的同名属性对等。移除 `@Deprecated` 标记后，它恢复为受支持的正式视图属性，避免误导用户，保障了视图元数据独立存放这一用例的长期可用性。
