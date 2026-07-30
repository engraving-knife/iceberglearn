# 提交 3540：Core: Introduce default values in RESTCatalogProperties (#15873)

## 提交信息

- **序号**：3540 / 4088
- **哈希**：8f30d8350bdac64e67e3778cc9489f07a57bc2e7
- **短哈希**：8f30d8350
- **日期**：2026-04-15 15:54:06 -0700
- **作者**：gaborkaszab
- **提交说明**：Core: Introduce default values in RESTCatalogProperties (#15873)
- **PR/Issue**：#15873

## 总体目的

`RESTCatalogProperties` 类集中定义了 REST Catalog 的配置属性键名和默认值。此前有几个属性的默认值散落在各处使用点，而不是统一定义在 `RESTCatalogProperties` 中：

1. `NAMESPACE_SEPARATOR` 属性的默认值 `RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8` 直接在 `RESTSessionCatalog` 和 `ResourcePaths` 中硬编码引用 `RESTUtil`，没有在 `RESTCatalogProperties` 中定义对应的 `NAMESPACE_SEPARATOR_DEFAULT` 常量
2. `SCAN_PLANNING_MODE` 属性的默认值 `ScanPlanningMode.CLIENT` 在 `RESTSessionCatalog` 中直接硬编码枚举值，没有 `SCAN_PLANNING_MODE_DEFAULT` 常量
3. `SNAPSHOT_LOADING_MODE_DEFAULT` 虽然有定义，但类型是 `String`（`SnapshotMode.ALL.name()`），调用方需要再做 `.toUpperCase()` 转换，类型不够语义化

本提交将这三个属性的默认值统一收敛到 `RESTCatalogProperties`，提升代码可读性和可维护性：默认值与属性键名放在一起，便于一目了然地查看每个属性的默认值。同时把 `SNAPSHOT_LOADING_MODE_DEFAULT` 和 `SCAN_PLANNING_MODE_DEFAULT` 改为强类型（枚举类型而非 String），调用方按需调用 `.name()`。

## 如何达成设计目的

在 `RESTCatalogProperties` 中：
- 新增 `NAMESPACE_SEPARATOR_DEFAULT = RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8`
- 新增 `SCAN_PLANNING_MODE_DEFAULT = ScanPlanningMode.CLIENT`（枚举类型）
- 把 `SNAPSHOT_LOADING_MODE_DEFAULT` 从 `String` 改为 `SnapshotMode` 枚举类型

然后更新所有调用点：
- `RESTSessionCatalog`：`NAMESPACE_SEPARATOR` 默认值改引用 `NAMESPACE_SEPARATOR_DEFAULT`；`SNAPSHOT_LOADING_MODE` 默认值改用 `.name()`；`SCAN_PLANNING_MODE` 默认值改引用 `SCAN_PLANNING_MODE_DEFAULT`
- `ResourcePaths`：`NAMESPACE_SEPARATOR` 默认值改引用 `NAMESPACE_SEPARATOR_DEFAULT`
- `RESTCatalogAdapter`（测试）：`SNAPSHOT_LOADING_MODE_DEFAULT` 改用 `.name()`

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogProperties.java` (+4/-1 lines)

**修改目的**：集中定义三个属性的默认值。

**工作逻辑**：
```java
-  public static final String SNAPSHOT_LOADING_MODE_DEFAULT = SnapshotMode.ALL.name();
+  public static final SnapshotMode SNAPSHOT_LOADING_MODE_DEFAULT = SnapshotMode.ALL;
...
   public static final String NAMESPACE_SEPARATOR = "namespace-separator";
+  public static final String NAMESPACE_SEPARATOR_DEFAULT =
+      RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8;
...
   public static final String SCAN_PLANNING_MODE = "scan-planning-mode";
+  public static final ScanPlanningMode SCAN_PLANNING_MODE_DEFAULT = ScanPlanningMode.CLIENT;
```
现在三个属性的键名和默认值都在同一处，类型更语义化（枚举而非字符串）。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+3/-3 lines)

**修改目的**：调用点改用集中定义的默认值常量。

**工作逻辑**：
- `SNAPSHOT_LOADING_MODE` 默认值：`RESTCatalogProperties.SNAPSHOT_LOADING_MODE_DEFAULT` → `.name()`（因为现在是枚举，需要转字符串）
- `NAMESPACE_SEPARATOR` 默认值：`RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8` → `RESTCatalogProperties.NAMESPACE_SEPARATOR_DEFAULT`
- `SCAN_PLANNING_MODE` 默认值：`RESTCatalogProperties.ScanPlanningMode.CLIENT` → `RESTCatalogProperties.SCAN_PLANNING_MODE_DEFAULT`

### `core/src/main/java/org/apache/iceberg/rest/ResourcePaths.java` (+1/-1 lines)

**修改目的**：`NAMESPACE_SEPARATOR` 默认值改用集中常量。

**工作逻辑**：
```java
-            RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8));
+            RESTCatalogProperties.NAMESPACE_SEPARATOR_DEFAULT));
```

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+1/-1 lines)

**修改目的**：测试适配 `SNAPSHOT_LOADING_MODE_DEFAULT` 类型变化。

**工作逻辑**：
```java
-                RESTCatalogProperties.SNAPSHOT_LOADING_MODE_DEFAULT)
+                RESTCatalogProperties.SNAPSHOT_LOADING_MODE_DEFAULT.name())
```
因默认值从 String 改为枚举，此处需要 `.name()` 转字符串。

## 总结

本提交将 `RESTCatalogProperties` 中散落在各处的三个属性默认值（`NAMESPACE_SEPARATOR`、`SCAN_PLANNING_MODE`、`SNAPSHOT_LOADING_MODE`）统一收敛到 `RESTCatalogProperties` 类中，与对应的属性键名放在一起，提升可读性和可维护性。同时把两个默认值改为强类型枚举，更语义化。属于代码组织优化，无行为变化。
