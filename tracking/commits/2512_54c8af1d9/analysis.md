# 提交 2512：Flink: Fix schedule data file size incorrect in RewriteDataFilesConfig (#13848)

## 提交信息

- **序号**：2512 / 4088
- **哈希**：54c8af1d9e4e8ef70004e6c4974a23b4e3c2f382
- **短哈希**：54c8af1d9
- **日期**：2025-08-17 22:06:08 -0700
- **作者**：GuoYu
- **提交说明**：Flink: Fix schedule data file size incorrect in RewriteDataFilesConfig (#13848)
- **PR/Issue**：#13848

## 总体目的

此提交修复了 Flink 维护模块中 `RewriteDataFilesConfig` 配置类的一个 bug：`SCHEDULE_ON_DATA_FILE_SIZE_OPTION` 配置项错误地使用了 `SCHEDULE_ON_DATA_FILE_COUNT` 作为 key，导致基于数据文件大小触发重写调度的配置无法正确生效。

`RewriteDataFilesConfig` 是 Flink 维护 API 中用于配置数据文件重写（RewriteDataFiles）操作的配置类。它定义了两个调度触发条件：
- `SCHEDULE_ON_DATA_FILE_COUNT`：基于数据文件数量触发调度
- `SCHEDULE_ON_DATA_FILE_SIZE`：基于数据文件总大小触发调度

由于配置 key 的复制粘贴错误，`SCHEDULE_ON_DATA_FILE_SIZE_OPTION`（文件大小选项）实际上注册的 key 是 `SCHEDULE_ON_DATA_FILE_COUNT`（文件数量），这意味着用户通过设置 `schedule.data-file-size` 属性时无法生效，反而该配置的默认值（100GB）与文件数量选项的配置会冲突。

## 如何达成设计目的

修复方案非常直接：将 `SCHEDULE_ON_DATA_FILE_SIZE_OPTION` 的 `ConfigOptions.key()` 参数从错误的 `SCHEDULE_ON_DATA_FILE_COUNT` 改为正确的 `SCHEDULE_ON_DATA_FILE_SIZE`。

此修复需要应用于 Flink 三个版本（v1.19、v1.20、v2.0）的对应文件，因为这三个版本各自维护了独立的代码副本。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFilesConfig.java` (+1/-1 lines)

**修改目的**：修正 v1.19 版本的配置 key。

**工作逻辑**：将 `ConfigOptions.key(SCHEDULE_ON_DATA_FILE_COUNT)` 改为 `ConfigOptions.key(SCHEDULE_ON_DATA_FILE_SIZE)`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFilesConfig.java` (+1/-1 lines)

**修改目的**：修正 v1.20 版本的配置 key。

**工作逻辑**：同上，将 `SCHEDULE_ON_DATA_FILE_COUNT` 改为 `SCHEDULE_ON_DATA_FILE_SIZE`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFilesConfig.java` (+1/-1 lines)

**修改目的**：修正 v2.0 版本的配置 key。

**工作逻辑**：同上，将 `SCHEDULE_ON_DATA_FILE_COUNT` 改为 `SCHEDULE_ON_DATA_FILE_SIZE`。

## 总结

此提交修复了一个由复制粘贴导致的配置 key 错误，使得基于数据文件大小的调度触发条件能够正确工作。修复涉及 Flink 的三个版本分支，确保所有版本的行为一致。这是一个影响功能正确性的 bug 修复，对依赖文件大小触发数据文件重写的用户至关重要。
