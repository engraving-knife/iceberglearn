# 提交 3950：Core: Set scan planning mode when initializing client (#15903)

## 提交信息

- **序号**：3950 / 4088
- **哈希**：b99389ba82cd573e17683818e00b6b052a5ef345
- **短哈希**：b99389ba8
- **日期**：2026-06-25 15:34:52 +0200
- **作者**：gaborkaszab
- **提交说明**：Core: Set scan planning mode when initializing client (#15903)
- **PR/Issue**：#15903

## 总体目的

这次提交重构了 REST catalog 客户端中 scan planning mode（扫描计划模式）的初始化和处理逻辑。scan planning mode 决定扫描计划是在客户端还是服务端执行（`CLIENT` 或 `SERVER`），这是 REST catalog 的一个重要配置。

原实现存在两个问题：
1. **客户端 scan planning mode 在每次表加载时才从 properties 读取**：`loadTableSession` 方法中每次加载表时都调用 `properties().get(RESTCatalogProperties.SCAN_PLANNING_MODE)` 从 catalog 属性中读取客户端配置。这不仅效率低，而且 `properties()` 返回的可能是不可变 map。
2. **使用字符串比较而非枚举**：客户端和服务端配置都作为字符串处理，通过 `equalsIgnoreCase` 比较是否冲突，然后再转换为枚举。这导致代码冗余且类型不安全。

修复后：
1. 在 catalog 初始化时（`initialize` 方法）一次性读取并解析客户端 scan planning mode 为 `ScanPlanningMode` 枚举，存储为 `clientScanPlanningMode` 字段。
2. 在 `loadTableSession` 中直接使用已解析的枚举值，避免重复读取和解析。
3. 使用枚举的 `equals` 比较替代字符串的 `equalsIgnoreCase`，类型更安全。

## 如何达成设计目的

在 `RESTSessionCatalog` 中：
1. 新增 `clientScanPlanningMode` 字段（`ScanPlanningMode` 类型），在 `initialize` 方法中从合并属性中读取并解析。
2. 重构 `loadTableSession` 中的 scan planning mode 逻辑：服务端配置也解析为枚举，冲突检测和有效模式确定都基于枚举类型。
3. 使用枚举比较和 `modeName()` 方法替代字符串操作。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+25/-16 lines)

**修改目的**：将 scan planning mode 初始化提前到 catalog 初始化时。

**工作逻辑**：
1. 新增 import `RESTCatalogProperties.ScanPlanningMode`。
2. 新增字段 `private ScanPlanningMode clientScanPlanningMode = null`。
3. 在 `initialize` 方法中读取配置：
```java
String scanPlanningModeConfig = mergedProps.get(RESTCatalogProperties.SCAN_PLANNING_MODE);
this.clientScanPlanningMode =
    scanPlanningModeConfig == null ? null : ScanPlanningMode.fromString(scanPlanningModeConfig);
```
4. 重构 `loadTableSession` 中的逻辑：
   - 服务端配置也解析为 `ScanPlanningMode` 枚举。
   - 冲突检测使用 `clientScanPlanningMode != serverScanPlanningMode`（枚举 equals）。
   - 警告消息使用 `clientScanPlanningMode.modeName()`。
   - 有效模式确定：优先服务端，其次客户端，最后默认值，全部基于枚举。
   - SERVER 模式检查使用 `effectiveMode == ScanPlanningMode.SERVER`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+81/-0 lines)

**修改目的**：补充 scan planning mode 的边界测试。

**工作逻辑**：新增 5 个测试：
1. `defaultPlanningModeWhenNoneSpecified`：客户端和服务端都不指定时使用默认模式。
2. `invalidPlanningModeConfiguredForClient`：客户端配置无效模式时抛出 `IllegalArgumentException`。
3. `invalidPlanningModeConfiguredForServer`：服务端配置无效模式时抛出异常。
4. `planningModeWithDifferentCasesOnClient`：参数化测试客户端配置不同大小写（client/CLIENT/Client/cLiEnT/server/SERVER/Server/sErVeR）的正确处理。
5. `planningModeWithDifferentCasesOnServer`：参数化测试服务端配置不同大小写的正确处理。

## 总结

这次提交将 REST catalog 客户端的 scan planning mode 初始化从每次表加载时提前到 catalog 初始化时，避免了重复读取和解析。同时将配置处理从字符串操作重构为枚举操作，提升了类型安全性。新增的大小写不敏感参数化测试验证了 `ScanPlanningMode.fromString` 对各种大小写变体的正确处理。
