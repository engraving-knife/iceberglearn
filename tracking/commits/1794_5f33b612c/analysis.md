# 提交 1794：Build: Bump Spark from 3.5.4 to 3.5.5 (#12396)

## 提交信息

- **序号**：1794 / 4088
- **哈希**：5f33b612ccf4110244b3c21b7bba3207d41afc56
- **短哈希**：5f33b612c
- **日期**：2025-02-28 07:57:36 +0100
- **作者**：Manu Zhang
- **提交说明**：Build: Bump Spark from 3.5.4 to 3.5.5 (#12396)
- **PR/Issue**：#12396

## 总体目的

此提交用于将 Iceberg 构建中依赖的 Spark 3.5 版本从 3.5.4 升级到 3.5.5。Spark 3.5.5 是 3.5 系列的维护版本，包含 bug 修复与改进。Iceberg 的 `spark-hive35` 模块针对 Spark 3.5.x 进行集成测试与构建，及时跟进 Spark 维护版本有助于保持与上游 Spark 的兼容性并获取最新修复。

除版本号升级外，本次提交还顺带移除了 Spark v3.5 测试代码中针对 `TimestampNTZType` 的一个临时 workaround。该 workaround 的存在是因为旧版 Spark 中 `ColumnarRow.get` 不支持 `TimestampNTZType`，导致测试失败，因此代码在比较时把 `TimestampNTZType` 临时当作 `TimestampType` 处理。Spark 3.5.5 修复了该问题，因此 workaround 不再需要，可一并清理，避免测试代码与实际类型语义脱节。

## 如何达成设计目的

通过两处改动达成目标：

1. **依赖版本升级**：在 `gradle/libs.versions.toml` 中将 `spark-hive35` 的版本从 `3.5.4` 改为 `3.5.5`。该目录文件是 Gradle 的版本目录（version catalog），集中管理依赖版本，单点修改即可让所有引用 `spark-hive35` 的模块统一切换版本。

2. **清理失效 workaround**：在 `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java` 中删除两处内容：
   - 不再需要的 `TimestampNTZType` 和 `TimestampType$` 的 import；
   - 在 `assertEquals` 前把 `TimestampNTZType` 转换为 `TimestampType$.MODULE$` 的逻辑块（连同解释注释）。

这样测试代码恢复为直接按字段真实类型比较，与 Spark 3.5.5 的行为一致。

## 修改详情

### `gradle/libs.versions.toml`（修改, +1/-1 lines）

**修改目的**：升级 Spark 3.5 系列依赖版本。

**工作逻辑**：版本目录中 `spark-hive35 = "3.5.4"` 改为 `spark-hive35 = "3.5.5"`。该变量被 Spark v3.5 相关模块（spark-hive35 等）引用，改一处即全局生效。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`（修改, +0/-7 lines）

**修改目的**：移除 Spark 3.5.5 已修复的 `TimestampNTZType` workaround。

**工作逻辑**：原代码在遍历 struct 字段做断言前，有这样一段：

```java
// ColumnarRow.get doesn't support TimestampNTZType, causing tests to fail. the representation
// is identical to TimestampType so this uses that type to validate.
if (type instanceof TimestampNTZType) {
  type = TimestampType$.MODULE$;
}
```

其作用是把 `TimestampNTZType`（不带时区的时间戳）临时当作 `TimestampType$`（带时区的时间戳）来做列读取校验，因为旧版 Spark 的 `ColumnarRow.get` 不支持 `TimestampNTZType`。Spark 3.5.5 修复了该缺陷，因此删除此段逻辑及对应 import，恢复按真实类型校验。

## 小结

- **成效**：Iceberg 的 Spark 3.5 集成跟进到 3.5.5，获取上游 bug 修复；同时清理了因旧版 Spark 缺陷而存在的测试 workaround，使测试代码更准确地反映类型语义。
- **影响范围**：涉及构建配置 `gradle/libs.versions.toml` 与 Spark v3.5 测试代码 `TestHelpers.java`。影响 Spark 3.5 模块的构建与测试依赖版本，不影响运行时 API 行为。
- **回迁到 1.4.x 的注意事项**：依赖版本升级回迁需谨慎评估：
  1. **1.4.x 的 Spark 版本**：1.4.x 分支本身可能针对不同的 Spark 版本组合（如 3.3/3.4/3.5）进行测试，回迁前需确认 1.4.x 是否已使用 Spark 3.5.4 作为基线。若 1.4.x 仍在 3.5.4 或更早，则升级到 3.5.5 通常安全（同为 3.5.x 维护版本，API 兼容），但需跑一遍 Spark v3.5 模块测试确认无回归。
  2. **workaround 清理**：`TestHelpers.java` 中 `TimestampNTZType` workaround 的删除依赖于 Spark 3.5.5 的修复。若 1.4.x 回迁版本升级但实际运行 Spark 版本低于 3.5.5，则删除 workaround 会导致测试失败。因此该测试改动应与版本升级绑定回迁，不可单独回迁。
  3. **无其他前置依赖**，可独立回迁。
