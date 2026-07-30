# 提交 3941：Flink: Backport: Add equality delete conversion planner (#16889) (#16944)

## 提交信息

- **序号**：3941 / 4088
- **哈希**：d5c427b93b5713a7650c793cc393d3d4c932f10f
- **短哈希**：d5c427b93
- **日期**：2026-06-24 08:21:01 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Add equality delete conversion planner (#16889) (#16944)
- **PR/Issue**：#16944（backport #16889）

## 总体目的

这次提交是 #16889（提交 3936）的 backport，将 `EqualityConvertPlanner` 算子及其测试从 Flink 2.1 分支 backport 到 Flink 1.20 和 2.0 分支。这确保了 Iceberg 在所有受支持的 Flink 版本上都能提供 equality delete 转换的维护能力。

由于 Flink 1.20 和 2.0 的 API 与 2.1 略有差异，backport 中包含了一个适配性修改：将 Flink 1.20 中的 scan-task wrapper 转换为普通 Java 类（`fixup! Convert scan-task wrapper in Flink 1.20 to plain Java class`），以兼容该版本的 API 约束。

## 如何达成设计目的

将 `EqualityConvertPlanner.java` 和 `TestEqualityConvertPlanner.java` 原样复制到 Flink 1.20 和 2.0 的对应目录下，并对 Flink 1.20 中的 scan-task wrapper 做适配性修改。此外还修改了 `EqualityDeleteFileScanTask` 和 `FlinkAddedRowsScanTask` 以支持新算子的需求。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertPlanner.java` (+762 lines, 新文件)

**修改目的**：将 planner 添加到 Flink 1.20。

**工作逻辑**：与 Flink 2.1 版本功能一致，可能包含针对 1.20 API 的适配。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertPlanner.java` (+1115 lines, 新文件)

**修改目的**：将 planner 测试添加到 Flink 1.20。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertPlanner.java` (+762 lines, 新文件)

**修改目的**：将 planner 添加到 Flink 2.0。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertPlanner.java` (+1115 lines, 新文件)

**修改目的**：将 planner 测试添加到 Flink 2.0。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityDeleteFileScanTask.java` (+24/-? lines)

**修改目的**：适配 Flink 1.20 的 scan-task wrapper。

**工作逻辑**：将 scan-task wrapper 转换为普通 Java 类以兼容 Flink 1.20 的 API 约束。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/FlinkAddedRowsScanTask.java` (+33/-? lines)

**修改目的**：适配 Flink 1.20 的 scan-task。

## 总结

这次提交将 #16889 引入的 `EqualityConvertPlanner` 算子及其测试 backport 到 Flink 1.20 和 2.0 分支，并针对 Flink 1.20 的 API 差异做了 scan-task wrapper 的适配。这确保了 equality delete 转换维护能力在所有受支持的 Flink 版本上可用。
