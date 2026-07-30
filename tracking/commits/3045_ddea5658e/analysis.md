# 提交 3045：Spark: Copy back 4.1 as 4.0

## 提交信息

- **序号**：3045 / 4088
- **哈希**：ddea5658ece8ba5aff6f22d8e3477a05064175d1
- **短哈希**：ddea5658e
- **日期**：2025-12-22
- **作者**：manuzhang
- **提交说明**：Spark: Copy back 4.1 as 4.0
- **PR/Issue**：无

## 总体目的

本提交是引入 Spark 4.1 支持的目录分叉操作的第二步，与前一提交 3044（"Move 4.0 as 4.1"）配对。3044 已将 `spark/v4.0` 整体重命名为 `spark/v4.1`（使 v4.1 继承完整 git 历史），本提交则把 `spark/v4.1` 的全部内容复制回 `spark/v4.0`，作为 574 个全新文件加入仓库。两步合计的净效果是：从原 `spark/v4.0` 模块分叉出两个内容完全相同、但 git 历史来源不同的目录——`spark/v4.1`（保有连续 rename 历史）与 `spark/v4.0`（新副本，无 rename 历史）。

经核对，本提交后 `spark/v4.0` 与 `spark/v4.1` 两目录的 git tree 哈希完全相同（`fd331a59b4b92b41de6532efd037ecc060e568a5`），证明二者内容字节级一致，`spark/v4.0/build.gradle` 内部的 `sparkMajorVersion` 仍为 `'4.0'`。这与"分叉起点"的定位一致：两个模块从同一代码基线出发，后续将独立演进——`spark/v4.1` 适配即将发布的 Spark 4.1，`spark/v4.0` 继续维护对 Spark 4.0 的兼容。

采用"先 move 再 copy back"而非直接 `cp -r` 的手法，其设计意图在于 git 历史的归属：rename 操作让 `spark/v4.1` 的文件可通过 `git log --follow` 追溯到原 v4.0 的全部历史，而重建的 `spark/v4.0` 作为新文件不携带 rename 历史。这种安排把连续开发历史赋予代表最新 Spark 版本、未来主要演进方向的 v4.1 模块，符合"主线历史连贯、维护分支从分叉点重新开始"的常见实践。本提交与 3044 时间戳完全相同，由 manuzhang 直接推送，无关联 PR 编号；真正的 Spark 4.1 适配（修改 `sparkMajorVersion`、CI、版本目录等）将在后续独立提交中完成。

## 如何达成设计目的

将 `spark/v4.1` 下全部 574 个文件的内容复制到 `spark/v4.0` 对应路径，并以 `new file mode` 形式新增提交。复制后仓库 spark 目录结构由 `spark/{v3.4, v3.5, v4.1}` 变为 `spark/{v3.4, v3.5, v4.0, v4.1}`，恢复了对 Spark 4.0 模块的存在，同时新增了 Spark 4.1 模块。新增文件覆盖 `build.gradle`、JMH 基准、Scala 扩展源码、ANTLR 语法、Java/Scala 测试及测试资源等整个模块资产，共计约 14 万行（140378 insertions）。

## 修改详情

### `spark/v4.0/**` (574 new files, +140378 lines)

**修改目的**：以 `spark/v4.1` 为源重建 `spark/v4.0` 模块，完成 v4.0/v4.1 的目录分叉。

**工作逻辑**：
将 `spark/v4.1` 下 574 个文件逐一复制到 `spark/v4.0` 对应路径并以新文件提交。代表性文件包括：

- `spark/v4.0/build.gradle`（新文件）：与 `spark/v4.1/build.gradle` 内容一致，仍声明 `sparkMajorVersion = '4.0'`、`scalaVersion = '2.13'`、JDK 17/21 守卫，以及 `iceberg-spark-4.0_2.13` 等子项目配置。后续若要正式区分两模块，需在此文件及版本目录/settings.gradle 中调整。
- `spark/v4.0/spark-extensions/src/main/antlr/.../IcebergSqlExtensions.g4`（新文件）：Iceberg SQL 扩展语法，内容与 v4.1 相同。
- `spark/v4.0/spark-extensions/src/main/scala/.../IcebergSparkSessionExtensions.scala` 及 `catalyst/{analysis,optimizer,parser,plans}/` 下 Scala 源码（新文件）：会话扩展、视图解析、命令改写、SQL 解析、逻辑计划等，内容与 v4.1 相同。
- `spark/v4.0/spark-extensions/src/jmh/java/.../*Benchmark.java`（新文件）：JMH 性能基准。
- `spark/v4.0/spark-extensions/src/test/{java,scala}/...` 下大量测试（新文件）：包括 `TestMerge`、`TestMetadataTables`、`TestSparkVariantRead`、`TestStoragePartitionedJoins` 等。
- `spark/v4.0/spark-extensions/src/test/resources/decimal_dict_and_plain_encoding.parquet`（新文件，二进制）：测试用 Parquet 资源。

由于这些文件以 `new file mode` 提交（非 rename），`spark/v4.0` 不携带原 v4.0 的 rename 历史，成为分叉后的新副本。两目录 tree 哈希一致确认内容相同，构成后续独立演进的共同基线。

## 总结

本提交将 `spark/v4.1` 内容复制回 `spark/v4.0`（574 个新文件、约 14 万行），完成 Spark 4.0/4.1 模块的目录分叉。与 3044 配合的净效果是从原 v4.0 模块分叉出两个内容相同的目录：v4.1 保有连续 git 历史（作为未来主要演进方向），v4.0 作为新副本重建（继续维护 Spark 4.0 兼容）。两者从共同基线出发，后续独立适配各自目标 Spark 版本。
