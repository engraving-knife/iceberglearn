# 提交 2219：Flink: Backport support compact in sink v2 to 1.19 and 2.0 (#13250)

## 提交信息

- **序号**：2219 / 4088
- **哈希**：b4f5da906ea31ec16e322b3ab97c8fd79cbf2b22
- **短哈希**：b4f5da906
- **日期**：2025-06-06 18:52:34 +0800
- **作者**：GuoYu
- **提交说明**：Flink: Backport support compact in sink v2 to 1.19 and 2.0 (#13250) backports #12979
- **PR/Issue**：#13250（backport #12979）

## 总体目的

这个提交是 PR #12979（即序号 2212 的提交 4079a4fbb）的反向移植，将 Flink Iceberg Sink v2 的小文件压缩（compaction）功能同步到 Flink 1.19 模块。原提交已在 Flink 1.20 模块实现了该功能，本提交将完全相同的代码变更应用到 Flink 1.19 模块，确保两个 Flink 版本的功能一致性。该功能通过在 sink 的 post-commit 阶段接入 maintenance 框架的 RewriteDataFiles 任务，自动合并流式写入产生的小文件，并引入分布式锁（JDBC/Zookeeper）协调并发压缩任务。由于 Iceberg 同时维护 Flink 1.19 和 1.20 两个版本，backport 是必要的同步操作。

## 如何达成设计目的

- 将 Flink 1.20 模块中已实现的所有压缩相关代码完整复制到 Flink 1.19 模块，包括：
  - 配置类：FlinkWriteConf、FlinkWriteOptions、FlinkConfParser 的修改
  - 维护 API：FlinkMaintenanceConfig、LockConfig、RewriteDataFilesConfig、RewriteDataFiles、TableMaintenance、JdbcLockFactory 的修改/新增
  - 维护算子：LockFactoryBuilder、TableChange 的修改/新增
  - Sink 类：IcebergSink、IcebergCommitter、FlinkManifestUtil、CommittableToTableChangeConverter 的修改/新增
  - 测试类：TestFlinkTableSinkCompaction、TestRewriteDataFilesConfig、TestLockConfig、TestLockFactoryBuilder、TestCommittableToTableChangeConverter、TestIcebergCommitter、TestIcebergSinkCompact 的修改/新增

## 修改详情

### Flink 1.19 模块的所有文件（与 2212 提交的 1.20 模块完全对应）

**修改目的**：将压缩功能同步到 Flink 1.19 模块。

**工作逻辑**：每个文件的修改逻辑与提交 2212（4079a4fbb）中对应文件完全相同，包括：

- `FlinkWriteOptions.java`（+3）：新增 `COMPACTION_ENABLE` 配置项。
- `FlinkWriteConf.java`（+9）：新增 `compactMode()` 方法。
- `FlinkConfParser.java`（+13/-12）：将配置解析器公开为 public + @Internal。
- `IcebergSink.java`（+59/-8）：实现 `addPostCommitTopology` 接入 TableMaintenance 和 RewriteDataFiles。
- `IcebergCommitter.java`（+7/-2）：compactMode 下延迟清理 manifest。
- `FlinkManifestUtil.java`（+13/-4）：新增重载方法支持 tableName+FileIO 清理 manifest。
- `CommittableToTableChangeConverter.java`（+107，新增）：committable → TableChange 转换器。
- `FlinkMaintenanceConfig.java`（+112，新增）：维护配置管理。
- `LockConfig.java`（+183，新增）：分布式锁配置（JDBC/Zookeeper）。
- `LockFactoryBuilder.java`（+87，新增）：锁工厂构建器。
- `RewriteDataFilesConfig.java`（+150，新增）：压缩任务配置。
- `RewriteDataFiles.java`、`TableMaintenance.java`、`JdbcLockFactory.java`、`TableChange.java`：小幅修改支持集成。
- 测试文件（共约 +920）：端到端集成测试和各组件单元测试。

## 总结

该提交是提交 2212（4079a4fbb）的 Flink 1.19 版本 backport，将完全相同的压缩功能代码同步到 Flink 1.19 模块。所有文件修改逻辑与原提交完全一致，确保两个 Flink 版本功能对等。详细的修改分析请参考提交 2212 的分析文档。
