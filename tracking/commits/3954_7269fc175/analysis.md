# 提交 3954：Flink: Backport: Add equality delete conversion API and integration tests (#16969)

## 提交信息

- **序号**：3954 / 4088
- **哈希**：7269fc1750b13d3f871b5e051fe670bb19fbe870
- **短哈希**：7269fc175
- **日期**：2026-06-26 07:58:46 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Add equality delete conversion API and integration tests (#16969)
- **PR/Issue**：#16969（backport #16948，即提交 3951）

## 总体目的

这次提交是 #16948（提交 3951）的 backport，将 `ConvertEqualityDeletes` 维护任务 API 及其集成测试同步到 Flink 1.20 和 2.0 分支。

`ConvertEqualityDeletes` 是 Flink Iceberg 维护框架的顶层入口，将 equality deletes 重写为 deletion vectors，使读取时可以通过位置应用删除而非 merge-on-read 连接，提升读取性能。写入器持续向 staging 分支追加 equality deletes，转换在后台并行运行。

## 如何达成设计目的

将 #16948 的 `ConvertEqualityDeletes.java`、相关算子修改和测试文件原样复制到 Flink 1.20 和 2.0 的对应目录。

## 修改详情

### Flink 1.20 模块（7 个文件，+1883/-1 lines）

**修改目的**：将 equality delete 转换 API 添加到 Flink 1.20。

涉及文件：
- `ConvertEqualityDeletes.java`（+286 lines, 新文件）：维护任务 API。
- `EqualityConvertCommitter.java`（+2/-1 line）：适配修改。
- `EqualityConvertPlanner.java`（+20 lines）：支持修改。
- `TestConvertEqualityDeletes.java`（+1333 lines, 新文件）：单元测试。
- `TestConvertEqualityDeletesE2E.java`（+170 lines, 新文件）：端到端测试。
- `TestMaintenanceE2E.java`（+33 lines）：E2E 场景。
- `TestEqualityConvertPlanner.java`（+40 lines）：planner 测试。

### Flink 2.0 模块（7 个文件，+1883/-1 lines）

**修改目的**：将 equality delete 转换 API 添加到 Flink 2.0。

涉及文件与 Flink 1.20 相同。

## 总结

这次提交将 #16948 的 `ConvertEqualityDeletes` 维护任务 API 和全部测试 backport 到 Flink 1.20 和 2.0，确保所有受支持的 Flink 版本都能使用 equality delete 到 deletion vector 的自动转换功能。结合 #16944（planner backport），这完成了 equality delete 转换管道在所有 Flink 版本上的完整部署。
