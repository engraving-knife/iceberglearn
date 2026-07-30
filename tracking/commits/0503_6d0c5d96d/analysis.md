# 提交 0503：Build: Bump tez010 from 0.10.2 to 0.10.3 (#9702)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0503 |
| 完整哈希 | 6d0c5d96daa692ea0590ac97c84d19d4ddc8e0ea |
| 短哈希 | 6d0c5d96d |
| 日期 | 2024-02-14（Wed Feb 14 21:15:32 2024 +0100） |
| 作者 | dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com> |
| 说明 | Build: Bump tez010 from 0.10.2 to 0.10.3 (#9702) |
| PR | #9702 |
| 依赖类型 | direct:production（tez-dag、tez-mapreduce 均为 direct:production） |
| 更新类型 | version-update:semver-patch（补丁版本升级） |

提交统计：1 个文件修改，1 行新增，1 行删除。

涉及文件：`gradle/libs.versions.toml`

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 针对 Hive 3 集成测试所用的 Apache Tez 0.10 系列构件从 `0.10.2` 升级到 `0.10.3`，跨 1 个补丁版本。Tez 是 Hadoop 生态上基于 DAG 的执行引擎，Hive 可选地以 Tez 作为 MapReduce 之外的执行后端；Iceberg 的 `hive3` 模块在测试中需要 Tez 的 DAG 与 MapReduce 构件来构造 Hive 3 路径下的查询执行环境，以验证 Iceberg 表在 Hive 3 + Tez 场景下的兼容性。该依赖仅在测试类路径使用（`testImplementation`），不进入发布产物。

版本目录中同时维护两套 Tez 版本以适配不同 Hive 代际：`tez010`（本提交升级对象，用于 `hive3` 模块）与 `tez08`（严格锁定 `[0.8, 0.9[`、偏好 `0.8.4`，用于 `mr` 模块对应 Hive 2 旧栈）。两套各自独立，本次仅提升 `tez010`，`tez08` 不受影响。`0.10.2 → 0.10.3` 属补丁级别（`version-update:semver-patch`），按语义化版本约定为向后兼容更新，通常包含执行引擎缺陷修复与稳定性改进，不引入破坏性 API 变更。由于仅在测试类路径且为补丁升级，对 Iceberg 公共 API 与运行时产物零影响。本提交与 0501、0502、0504、0505 同属 2024-02-14 合入的 Dependabot 依赖批次。

## 如何达成设计目的

实现路径是单点修改：在 `gradle/libs.versions.toml` 的版本声明区把 `tez010 = "0.10.2"` 改为 `tez010 = "0.10.3"`。库定义区 `tez010-dag` 与 `tez010-mapreduce` 通过 `version.ref = "tez010"` 引用该变量，无需改动；`hive3/build.gradle` 中 `testImplementation libs.tez010.dag` / `libs.tez010.mapreduce` 的访问器引用亦无需改动。一处版本号变更即把 `hive3` 模块测试类路径上的两个 Tez 构件统一升到 0.10.3。

## 修改详情

### `gradle/libs.versions.toml`

修改目的：把 Tez 0.10 系列（用于 Hive 3 集成测试）的锁定版本从 `0.10.2` 提升到 `0.10.3`。

工作逻辑：

- 版本声明区第 86 行附近：`tez010 = "0.10.2"` → `tez010 = "0.10.3"`。
- 该版本变量被库定义区第 197-198 行附近的两个库别名引用：
  - `tez010-dag = { module = "org.apache.tez:tez-dag", version.ref = "tez010" }`
  - `tez010-mapreduce = { module = "org.apache.tez:tez-mapreduce", version.ref = "tez010" }`
- 二者又被 `hive3/build.gradle` 第 102-103 行以 `testImplementation libs.tez010.dag` / `testImplementation libs.tez010.mapreduce` 消费，为 `:iceberg-hive3` 模块测试提供 Tez DAG 运行时与 MapReduce 适配。该模块测试堆内存配置为 `maxHeapSize '2500m'`（同文件第 108 行），用于支撑 `testJoinTables` / `testScanTable` 等较重的连接与扫描用例。
- 相邻的 `tez08`（第 71 行 `tez08 = { strictly = "[0.8, 0.9[", prefer = "0.8.4" }`）采用 Gradle rich version 严格区间锁定，服务于 `mr` 模块（`mr/build.gradle` 第 72-73 行的 `libs.tez08.dag` / `libs.tez08.mapreduce`），与本次升级互不影响。
- 升级为补丁级别、向后兼容，Tez 0.10.x 内 DAG/MapReduce 客户端 API 稳定，因此 `hive3` 测试源码无需配合改动。

## 小结

本提交是 Dependabot 触发的 Hive 3 测试栈 Tez 构件补丁升级：将 `gradle/libs.versions.toml` 中 `tez010` 由 `0.10.2` 升至 `0.10.3`，连带把 `tez010-dag` 与 `tez010-mapreduce` 两个构件升版。该依赖经 `hive3/build.gradle` 以 `testImplementation` 形式仅用于 `:iceberg-hive3` 模块测试，不进入发布产物；与之并列的 `tez08`（服务于 `mr` 模块）采用严格版本区间独立锁定，不受影响。升级属补丁级别、向后兼容，对 Iceberg 自身代码与公共 API 零影响，回迁 1.4.x 风险极低，仅需同步该一行版本号。
