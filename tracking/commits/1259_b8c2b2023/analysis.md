# 提交 1259：Build: Bump parquet from 1.13.1 to 1.14.3 (#11264)

## 提交信息

- **序号**：1259 / 4088
- **哈希**：b8c2b20237bc9309d34dc96c473e9941d1b2ad58
- **短哈希**：b8c2b2023
- **日期**：2024-10-21（Mon Oct 21 07:34:44 2024 +0200）
- **作者**：dependabot[bot]；共同作者：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Build: Bump parquet from 1.13.1 to 1.14.3 (#11264)
- **PR/Issue**：#11264

## 总体目的

Iceberg 依赖 Apache Parquet 作为列式数据文件的核心读写库。dependabot 检测到 `gradle/libs.versions.toml` 中声明的 parquet 版本 1.13.1 已落后，发起本次版本升级至 1.14.3（跨 1.14.0/1.14.1/1.14.2/1.14.3 多个版本的累积升级）。升级目的是获取 Parquet 1.14.x 系列的缺陷修复、性能改进与稳定性提升，保持依赖处于受维护状态。

由于 Parquet 库在写入文件时的页结构、元数据布局等在新版本有细微变化，导致 Iceberg 的 Flink 元数据表可读性指标测试（`TestMetadataTableReadableMetrics`）中部分期望的「文件大小（字节数）」值发生变化。共同作者 Eduard Tudenhoefner 配合升级同步调整了三个 Flink 版本（1.18/1.19/1.20）测试中的期望数值，使升级后测试仍能通过。

## 如何达成设计目的

两步完成：

1. **版本声明更新**：在 `gradle/libs.versions.toml` 中将 `parquet` 从 `"1.13.1"` 改为 `"1.14.3"`。该文件是 Iceberg 的 Gradle 版本目录（version catalog），所有子模块通过引用此 catalog 获取 parquet 依赖坐标，改一处即可全局生效。

2. **测试期望值同步**：Parquet 1.14.x 写出的文件在某些列类型上的总字节数（totalSizeBytes / 文件大小指标）略有变化。`TestMetadataTableReadableMetrics` 通过 Flink 读取 Iceberg 的元数据表（`readable_metrics`）校验各列的文件大小、值计数等指标，其中第一个字段为文件字节数。升级后这些字节数发生变化（如 binary 列 52→55、boolean 32→36、decimal/double 85→91、fixed 44→47、float/int 71→77、long/string 79→85、嵌套 double 46→50、嵌套 long 54→57），故逐个调整三个 Flink 版本测试类中的 `Row.of(...)` 第一个参数。其余字段（值计数、null 计数、上下界值等）不变，说明变化仅体现在文件体积指标上。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 parquet 依赖版本。

**工作逻辑**：将 `parquet = "1.13.1"` 改为 `parquet = "1.14.3"`。版本目录中其余条目不变。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`

**修改目的**：适配 Parquet 1.14.3 写出文件的字节数变化。

**工作逻辑**：在 `testNonNestedValues` 与 `testNestedValues` 两个用例中，调整各列 `Row.of(...)` 的第一个参数（文件总字节数指标）：

- binaryCol：52 → 55
- booleanCol：32 → 36
- decimalCol / doubleCol：85 → 91
- fixedCol：44 → 47
- floatCol / intCol：71 → 77
- longCol / stringCol：79 → 85
- leafDoubleCol（嵌套）：46 → 50
- leafLongCol（嵌套）：54 → 57

这些数字是 Parquet 写出测试数据文件后的实际字节数，由 Parquet 库的页/元数据编码决定。新版 Parquet 改变了某些编码细节导致文件体积小幅增长（3–6 字节）。

### `flink/v1.19/...` 与 `flink/v1.20/...` 下同名测试文件

**修改目的**：与 1.18 对称，同步调整 1.19 与 1.20 两个 Flink 版本的测试期望值。

**工作逻辑**：与 1.18 完全一致的数值调整（三个文件的改动逐字相同）。

## 小结

- **成效**：parquet 依赖从 1.13.1 升级到 1.14.3，获取 1.14.x 系列的缺陷修复与改进；同步修正三个 Flink 版本元数据表可读性指标测试中因 Parquet 新版文件体积变化而失配的期望字节数，保证 CI 通过。
- **影响范围**：1 个版本目录文件（版本号）+ 3 个 Flink 测试文件（期望数值），共约 34 行改动。不涉及任何生产代码逻辑变更，仅依赖坐标与测试断言。运行时影响：写出/读入的 Parquet 文件格式仍符合 Parquet 规范，向后兼容（Parquet 库自身保证跨版本读写兼容）。
- **回迁到 1.4.x 的注意事项**：依赖升级属低风险变更。1.4.x 若需要 Parquet 1.14.x 的修复（如安全或稳定性），**可回迁**版本号变更。但回迁时需注意：1) 必须同步回迁三个 Flink 测试文件的期望数值调整，否则 1.4.x 的 `TestMetadataTableReadableMetrics` 会失败；2) 1.4.x 的 Parquet 依赖若已有其他 patch（如 ICE 自身锁定的补丁版本），需确认无冲突；3) Parquet 1.14.x 对 JDK 最低版本的要求需与 1.4.x 的构建环境匹配；4) 升级会让 1.4.x 写出的新 Parquet 文件字节略有变化，但格式兼容，存量文件读取不受影响。建议回迁前在 1.4.x 环境完整跑一遍 Flink 元数据表测试确认数值一致。
