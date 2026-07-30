# 提交 2635：Core: Move properties of REST catalog into RESTCatalogProperties (#13991)

## 提交信息

- **序号**：2635 / 4088
- **哈希**：4e9f9ccac9d418a779aec883fde2d47c9471828f
- **短哈希**：4e9f9ccac
- **日期**：2025-09-15 09:54:40 +0200
- **作者**：gaborkaszab
- **提交说明**：Core: Move properties of REST catalog into RESTCatalogProperties (#13991)
- **PR/Issue**：#13991

## 总体目的

REST catalog 的配置属性（如快照加载模式、指标上报开关、分页大小、视图端点支持等）此前散落在 `RESTSessionCatalog` 类内部，以 `private static final` 字段形式定义，外加一个嵌套枚举 `SnapshotMode`。这种做法将"配置常量定义"与"catalog 运行时逻辑"耦合在一起，导致这些常量难以被外部复用和引用，也不符合将配置属性集中管理的最佳实践。

本提交将这些属性常量抽取到独立的 `RESTCatalogProperties` 类中，使配置项成为公开 API 的一部分，便于使用者在代码中以常量方式引用属性键名，而不是硬编码字符串。同时，原有的 `REST_PAGE_SIZE` 字段标记为 `@Deprecated`，引导使用者迁移到新位置。

## 如何达成设计目的

1. 新建 `RESTCatalogProperties` 类，集中定义 REST catalog 的所有配置属性键名、默认值以及 `SnapshotMode` 枚举。
2. 修改 `RESTSessionCatalog`，删除内部重复的属性常量定义和 `SnapshotMode` 枚举，改为引用 `RESTCatalogProperties` 中的常量。
3. `SnapshotMode` 枚举原有的 `params()` 实例方法被改为 `RESTSessionCatalog` 中的静态方法 `snapshotModeToParam`，保持枚举纯粹。
4. 将 `REST_PAGE_SIZE` 标记为 `@Deprecated`（计划在 2.0.0 移除），保留向后兼容。
5. 更新相关测试，将引用从 `RESTSessionCatalog` 迁移到 `RESTCatalogProperties`，并删除已无意义的 `testSnapshotParams` 测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogProperties.java` (新建, +42 lines)

**修改目的**：集中定义 REST catalog 配置属性。

**工作逻辑**：新类为 `final`，私有构造函数，包含以下常量：
- `SNAPSHOT_LOADING_MODE` = "snapshot-loading-mode"，默认 `SnapshotMode.ALL`。
- `METRICS_REPORTING_ENABLED` = "rest-metrics-reporting-enabled"，默认 true。
- `VIEW_ENDPOINTS_SUPPORTED` = "view-endpoints-supported"，默认 false（用于与不发送 endpoints 字段的旧服务端向后兼容）。
- `PAGE_SIZE` = "rest-page-size"。
- 内嵌枚举 `SnapshotMode { ALL, REFS }`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+33/-23 lines)

**修改目的**：移除内部属性常量，改为引用新类。

**工作逻辑**：
- 删除了 `REST_METRICS_REPORTING_ENABLED`、`REST_SNAPSHOT_LOADING_MODE`、`VIEW_ENDPOINTS_SUPPORTED` 常量定义。
- `REST_PAGE_SIZE` 保留但标记 `@Deprecated`，注释指向 `RESTCatalogProperties#PAGE_SIZE`。
- 删除嵌套 `SnapshotMode` 枚举（含 `params()` 方法），改为 import `RESTCatalogProperties.SnapshotMode`，并新增静态方法 `snapshotModeToParam(SnapshotMode mode)` 将枚举值转为查询参数 `{"snapshots": "<mode>"}`。
- 初始化逻辑中所有属性读取改为引用 `RESTCatalogProperties` 的常量和默认值。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+12/-21 lines)

**修改目的**：迁移测试引用并删除过时测试。

**工作逻辑**：将 `SnapshotMode` 的 import 从 `RESTSessionCatalog` 改为 `RESTCatalogProperties`。将多处硬编码的 `"snapshot-loading-mode"`、`"refs"` 改为常量 `RESTCatalogProperties.SNAPSHOT_LOADING_MODE` 和 `SnapshotMode.REFS.name()`。将 `RESTSessionCatalog.REST_PAGE_SIZE` 引用改为 `RESTCatalogProperties.PAGE_SIZE`。删除了 `testSnapshotParams` 测试（因为 `params()` 方法已不存在）。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java` (+2/-2 lines)

**修改目的**：迁移属性引用。

**工作逻辑**：将 `RESTSessionCatalog.REST_PAGE_SIZE` 改为 `RESTCatalogProperties.PAGE_SIZE`，将 `RESTSessionCatalog.VIEW_ENDPOINTS_SUPPORTED` 改为 `RESTCatalogProperties.VIEW_ENDPOINTS_SUPPORTED`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalogWithAssumedViewSupport.java` (+1/-1 lines)

**修改目的**：迁移属性引用。

**工作逻辑**：将 `RESTSessionCatalog.VIEW_ENDPOINTS_SUPPORTED` 改为 `RESTCatalogProperties.VIEW_ENDPOINTS_SUPPORTED`。

## 总结

本提交将 REST catalog 的配置属性从 `RESTSessionCatalog` 内部抽取到独立的 `RESTCatalogProperties` 类，提升了配置项的可发现性和可复用性，是迈向更清晰 API 结构的重构。旧常量 `REST_PAGE_SIZE` 暂保留并标记废弃以保证兼容性。这是一次纯粹的代码组织优化，不改变运行时行为。
