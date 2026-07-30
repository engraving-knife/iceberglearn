# 提交 0450：Flink: change defaultFlinkVersion back to 1.18 (#9625)

## 提交信息

- **序号**：0450
- **完整哈希**：0f11340bfa6143ab85d16780149b5892c54f277c
- **短哈希**：0f11340bf
- **日期**：2024-02-02 19:13:51 +0100（10:13:51 -0800）
- **作者**：Rodrigo <rmenesespinillos@apple.com>
- **提交说明**：Flink: change defaultFlinkVersion back to 1.18 (#9625)
- **关联 PR**：#9625

## 总体目的

本提交把 `gradle.properties` 中的 `systemProp.defaultFlinkVersions` 从 `1.16,1.17,1.18` 改回 `1.18`，撤销了同一天早些时候的提交 0446（`2c247501c`，PR #9598）对该项的临时改动。提交 0446 在做 Flink 1.16/1.17 测试从 JUnit 4 到 JUnit 5 的迁移回 port 时，为了让默认构建（不显式指定 `-PflinkVersions=...`）能同时跑三个版本的测试、一次性回归验证迁移结果，把 `defaultFlinkVersions` 临时扩展为 `1.16,1.17,1.18`。

迁移合并完成后，这个临时设置就完成了它的使命，需要恢复为只默认跑 1.18。原因是：Iceberg 的 Flink 模块为每个支持的 Flink 版本（1.16、1.17、1.18）各自维护一份测试目录，三套测试加起来体量很大，默认全跑会显著拖慢 CI 和本地构建。社区惯例是默认只跑最新的 1.18，需要时再通过命令行 `-PflinkVersions=1.16,1.17,1.18` 显式指定跑全量版本（`knownFlinkVersions=1.16,1.17,1.18` 仍保留，说明这三个版本都仍被支持，只是默认不都跑）。本提交就是把这一惯例恢复回来，是迁移收尾的清理动作。

## 如何达成设计目的

实现路径是单行改动：把 `gradle.properties` 中 `systemProp.defaultFlinkVersions` 的值从 `1.16,1.17,1.18` 改回 `1.18`。`knownFlinkVersions` 行不动（仍为 `1.16,1.17,1.18`），`defaultHiveVersions`/`knownHiveVersions` 等其他配置项也不动。从 diff 的 blob 索引看（`d4edeea0a` → `ea857e7f2`），改后的 `gradle.properties` 与提交 0446 之前的状态完全一致，是一次干净的回退。改回后，`./gradlew test` 默认只为 Flink 1.18 编译并运行 Flink 测试；如需覆盖 1.16/1.17，需显式传 `-PflinkVersions=1.16,1.17,1.18`（或子集），由 `build.gradle` 中的版本循环逻辑据此生成对应的子项目与测试任务。

## 修改详情

### `gradle.properties`
**修改目的**：把默认 Flink 测试版本收回到 1.18，恢复迁移前的构建性能惯例。
**工作逻辑**：
```
- systemProp.defaultFlinkVersions=1.16,1.17,1.18
+ systemProp.defaultFlinkVersions=1.18
```
该属性被 `flink` 子项目的 `build.gradle` 读取，决定默认情况下为哪些 Flink 版本生成并执行测试任务。改为 `1.18` 后默认只跑最新版本，1.16/1.17 的测试目录（含本次迁移涉及的 `FlinkCatalogTestBase` 删除、`CatalogTestBase` 派生类改写等）仍可在显式指定版本时运行，不会因为默认配置变化而失去覆盖——CI 流水线通常会在定时或 PR 触发时显式跑全量版本。`knownFlinkVersions=1.16,1.17,1.18` 保持不变，确保这三个版本依然被构建系统识别为"已知支持版本"。

## 小结

本提交是 Flink JUnit 5 迁移（提交 0446）的收尾清理：把临时扩展为 `1.16,1.17,1.18` 的 `defaultFlinkVersions` 恢复为 `1.18`，使默认构建回到"只跑最新版本"的惯例，避免三套版本测试拖慢 CI。这是一次单行、零风险、纯配置的回退，标志着迁移工作的正式结束——迁移期间用来全量回归的临时开关已不再需要，1.16/1.17 的覆盖改由显式 `-PflinkVersions` 触发。
