# 提交 0386：Docs, Spark: Distribution mode not respected for CTAS/RTAS before 3.5.0 (#9439)

## 提交信息

- **序号**：0386
- **哈希**：057f8877136276b3a0c8c01551f733e804fc42a3
- **短哈希**：057f88771
- **日期**：2024-01-19（Fri Jan 19 00:40:34 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs, Spark: Distribution mode not respected for CTAS/RTAS before 3.5.0 (#9439)
- **PR/Issue**：#9439

## 总体目的

这个提交旨在记录并验证一个已知限制：在 Spark 3.5.0 之前的版本中，CTAS（CREATE TABLE AS SELECT）和 RTAS（REPLACE TABLE AS SELECT）不会尊重 Iceberg 表的 `write.distribution-mode` 配置。这是一个跨越文档与测试两端的小型补丁，文档明确告知用户该限制存在，而测试则把行为差异固化下来，以防止后续重构时无声地破坏现有约定。

Iceberg 的 Spark 写入器默认要求每个 Spark task 中的数据按分区值聚簇，以最小化写文件时打开的文件句柄数量。从 Iceberg 1.2.0 起，Iceberg 还会请求 Spark 在写入前对数据进行预排序以满足这一分布要求。然而该请求是否生效取决于 Spark 引擎本身是否会将该分布要求应用到 CTAS/RTAS 的写入计划中——Spark 在 3.5.0 之前并不会这么做，因此即使表设置了 `write.distribution-mode = hash` 或 `range`，CTAS/RTAS 仍可能写出违反"按分区聚簇"假设的数据，进而触发 writer 抛错或写出过多小文件。

提交的另一个微妙意图是建立 3.4 与 3.5 两个 Spark 版本之间的对照测试：3.4 中以"期望抛错"的方式验证限制依然存在，3.5 中以"期望成功并校验 schema/spec/数据"的方式验证限制已被修复。这种"双版本对照测试 + 文档说明"的组合让限制的存在与修复都有据可查，并为后续用户从 3.4 升级到 3.5 提供明确预期。

## 如何达成设计目的

作者分两步落地：其一在 `docs/spark-writes.md` 中加一行说明 Spark 在 3.5.0 之前不尊重 CTAS/RTAS 的分布模式，并顺带修正 `exchanged → exchange` 的拼写错误；其二在 Spark 3.4 与 3.5 的 `TestCreateTableAsSelect` 测试类中分别添加 `testCTASWriteDistributionModeNotRespected`（3.4，期望抛 SparkException）和 `testCTASWriteDistributionModeRespected`（3.5，期望写入成功并验证表结构）两个测试，并把源表数据从 3 行扩到 5 行，以让 bucket(2, id) 分桶下数据分布更明显、更易触发"记录未按 spec 聚簇"的校验。

## 修改详情

### docs/spark-writes.md

**修改目的**：向用户明确告知 Spark 3.5.0 之前 CTAS/RTAS 不尊重分布模式的限制，并修正一处拼写。

**工作逻辑**：
- 在描述 `write.distribution-mode = hash` 默认行为的段落末尾追加一句："Spark doesn't respect distribution mode in CTAS/RTAS before 3.5.0."。这一句直接对应 PR 标题，让用户在配置 CTAS/RTAS 时对预期行为有准确认知。
- 在 `range` 模式段落中将 "range based exchanged" 修正为 "range based exchange"，属于顺手清理的笔误。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTableAsSelect.java

**修改目的**：在 Spark 3.4 中固化"CTAS 不尊重分布模式会抛错"的行为，确保该限制被显式记录在测试中。

**工作逻辑**：
- 新增 import：`org.apache.spark.SparkException` 与 `org.assertj.core.api.Assertions`，用于断言抛出的异常类型与消息。
- 在 `@Before` 中把源表插入数据从 `(1,'a'),(2,'b'),(3,'c')` 扩展为 `(1,'a'),(2,'b'),(3,'c'),(4,'d'),(5,'e')`，扩大数据量以便分桶后更明显地暴露分布问题。
- 新增测试 `testCTASWriteDistributionModeNotRespected`：执行 `CREATE TABLE %s USING iceberg PARTITIONED BY (bucket(2, id)) AS SELECT * FROM %s`，断言抛出 `SparkException` 且消息包含 "Incoming records violate the writer assumption that records are clustered by spec and by partition within each spec"。这是 Iceberg writer 在检测到记录未按 spec/分区聚簇时抛出的标准错误信息，反向证明了 Spark 3.4 在 CTAS 中没有按 `write.distribution-mode` 重排数据。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTableAsSelect.java

**修改目的**：在 Spark 3.5 中验证 CTAS 已经能够正确尊重分布模式，写入成功且表结构、分区、数据均符合预期。

**工作逻辑**：
- 同样把 `@BeforeEach` 中的源表数据从 3 行扩到 5 行，与 3.4 测试保持一致的数据规模。
- 新增测试 `testCTASWriteDistributionModeRespected`：执行同样的 `CREATE TABLE ... PARTITIONED BY (bucket(2, id)) AS SELECT * FROM %s`，但期望它成功而不抛异常。随后：
  1. 构造期望 schema（id Long、data String，均为 optional）并断言 `ctasTable.schema().asStruct()` 与之相等；
  2. 构造期望 `PartitionSpec`（对 id 做 bucket(2)）并断言 `ctasTable.spec()` 与之相等；
  3. 用 `assertEquals` 比对源表与 CTAS 表的 `SELECT * ORDER BY id` 结果，确保数据完整无丢失。

  这一测试与 3.4 版的"抛错"测试形成镜像：同样 SQL、同样数据，3.4 抛错、3.5 成功，清楚地把修复边界画出来。

## 小结

这是一个"文档 + 测试"性质的小补丁，本身不修改产品代码逻辑，价值在于：
1. 把 Spark 3.5 前后对 CTAS/RTAS 分布模式的行为差异显式化，避免用户踩坑；
2. 用一组镜像测试锁定行为契约，3.4 验证限制存在、3.5 验证限制解除，未来若有人反向引入回归会被立即捕获；
3. 顺手修正文档拼写。

它体现了一种典型的"已知限制"管理模式：不改代码、只改文档与测试，让限制本身成为受版本控制的事实。
