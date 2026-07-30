# 提交 3410：CORE: Allow table level override for scan planning (#15572)

## 提交信息

- **序号**：3410 / 4088
- **哈希**：67ed9d116581cc3d45d8c8f205eeaa1743d8d171
- **短哈希**：67ed9d1165
- **日期**：2026-03-17 16:58:37 -0700
- **作者**：Prashant Singh
- **提交说明**：CORE: Allow table level override for scan planning (#15572)
- **PR/Issue**：#15572

## 总体目的

将 REST 扫描规划配置从简单的布尔开关（`rest-scan-planning-enabled`）简化为两种模式的枚举（`ScanPlanningMode`：CLIENT 和 SERVER），并允许通过 `LoadTableResponse.config()` 进行表级覆盖。此前扫描规划只能在 catalog 级别全局开关，现在服务器可以为每个表单独配置扫描规划模式，实现更细粒度的控制。当客户端和服务器配置冲突时，服务器配置优先，并记录警告日志。

## 如何达成设计目的

1. 在 `RESTCatalogProperties` 中用 `ScanPlanningMode` 枚举替换布尔配置 `REST_SCAN_PLANNING_ENABLED`
2. 枚举支持 CLIENT（默认，客户端规划）和 SERVER（服务端规划）两种模式
3. 在 `RESTSessionCatalog.restTableForScanPlanning` 方法中同时检查客户端配置（catalog 属性）和服务器配置（tableConf）
4. 服务器配置优先于客户端配置；冲突时记录警告
5. 当模式为 SERVER 但服务器不支持相应端点时，抛出 `IllegalStateException`
6. 更新所有调用 `restTableForScanPlanning` 的位置，传入 tableConf 参数

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogProperties.java` (+34/-3 lines)

**修改目的**：用 ScanPlanningMode 枚举替换布尔开关。

**工作逻辑**：
- 移除 `REST_SCAN_PLANNING_ENABLED` 和 `REST_SCAN_PLANNING_ENABLED_DEFAULT`
- 新增 `SCAN_PLANNING_MODE = "scan-planning-mode"` 常量
- 新增 `ScanPlanningMode` 枚举，包含 `CLIENT` 和 `SERVER` 两个值
- `modeName()` 返回小写名称
- `fromString(String)` 解析字符串为枚举值，无效值时抛出异常并列出有效值

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+53/-18 lines)

**修改目的**：实现表级扫描规划模式覆盖逻辑。

**工作逻辑**：

**restTableForScanPlanning 方法重构**：
- 新增 `Map<String, String> tableConf` 参数
- 从 `properties()` 读取客户端配置 `planningModeClientConfig`
- 从 `tableConf` 读取服务器配置 `planningModeServerConfig`
- 冲突时记录警告日志，服务器配置优先
- 确定 `effectiveMode`：服务器配置优先，无服务器配置时用客户端配置，都无则默认 CLIENT
- 当 `effectiveMode == SERVER` 时，检查服务器是否支持 `V1_SUBMIT_TABLE_SCAN_PLAN` 端点，不支持则抛出异常
- 移除 `restScanPlanningEnabled` 字段及其初始化逻辑

**调用点更新**：
- 三处调用 `restTableForScanPlanning` 的位置都新增传入 `tableConf` 参数

### 测试文件 (+189/-36 lines across multiple files)

**修改目的**：更新测试以适应新的枚举配置模式。

**工作逻辑**：
- `TestRESTScanPlanning.java`：更新测试使用 `ScanPlanningMode` 枚举替代布尔配置
- `TestBaseWithRESTServer.java`：小幅调整
- 多个 Spark 版本的 `TestRemoteScanPlanning.java`：更新配置方式

## 总结

本提交将 REST 扫描规划配置从布尔开关重构为枚举模式（CLIENT/SERVER），并支持表级覆盖。服务器可通过 `LoadTableResponse.config()` 为特定表设置扫描规划模式，优先于客户端 catalog 级别配置。冲突时服务器配置优先并记录警告。这为混合场景提供了更灵活的扫描规划控制。
