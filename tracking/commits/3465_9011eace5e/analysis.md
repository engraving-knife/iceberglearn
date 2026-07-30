# 提交 3465：Docs: Fix Flink Getting Started page (#15772)

## 提交信息

- **序号**：3465 / 4088
- **哈希**：9011eace5e82c9022491e5a65c7257fe4fb423a7
- **短哈希**：9011eace5e
- **日期**：2026-03-27 14:53:35 +0100
- **作者**：Manu Zhang
- **提交说明**：Docs: Fix Flink Getting Started page (#15772)
- **PR/Issue**：#15772

## 总体目的

修复 Flink Getting Started 文档页面中的多处问题，包括版本变量使用错误、语法错误、格式问题和措辞改进。

## 如何达成设计目的

- 修复 jar 文件名中的版本变量（使用 `flinkVersionMajor` 替代硬编码版本号）
- 修正 SQL 语法（catalog 配置使用单引号）
- 修正代码块语言标记（`python` 改为 `bash`）
- 修正 snapshots 元数据表查询语法（使用 `$snapshots` 而非 `.snapshots`）
- 改进多处措辞和语法

## 修改详情

### `docs/docs/flink.md` (+20/-18 lines)

**修改目的**：修复 Flink Getting Started 文档中的多处问题。

**工作逻辑**：

1. **jar 文件名修复**：
   - 旧：`iceberg-flink-runtime-1.15-{{ icebergVersion }}.jar`（硬编码 1.15）
   - 新：`iceberg-flink-runtime-{{ flinkVersionMajor }}-{{ icebergVersion }}.jar`（使用变量）

2. **SQL 语法修复**：
   - 旧：`` `<config_key>`=`<config_value>` ``
   - 新：`'<config_key>' = '<config_value>'`

3. **代码块语言标记修复**：
   - `pip install` 命令的代码块从 `python` 改为 `bash`

4. **snapshots 查询语法修复**：
   - 旧：`` SELECT * FROM `hive_catalog`.`default`.`sample`.`snapshots` ``
   - 新：`` SELECT * FROM `hive_catalog`.`default`.`sample$snapshots`; ``

5. **措辞和语法改进**：
   - "1.15 or less" → "Flink 1.15 or earlier"
   - "1.16 or above has a regression" → "Flink 1.16+ has a regression"
   - "PyFlink 1.6.1 does not work on OSX with a M1 cpu" → "PyFlink 1.6.1 has a known issue on macOS with Apple Silicon"
   - "Adding catalogs." → "Adding catalogs"
   - "Flink support to create" → "Flink supports creating"
   - "flink streaming job" → "Flink streaming jobs"
   - "Iceberg also support" → "Iceberg also supports"
   - "sentences" → "statements"
   - "historical snapshot-id" → "historical snapshot ID"

## 总结

该提交修复了 Flink Getting Started 文档页面中的多处问题，包括版本变量使用错误、SQL 语法错误、代码块标记错误、元数据表查询语法错误，以及多处措辞和语法改进。
