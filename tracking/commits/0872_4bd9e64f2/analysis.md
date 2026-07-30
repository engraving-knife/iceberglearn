# 提交 0872：Spark: Remove useless code in `TestRemoveOrphanFilesProcedure` (#10562)

## 提交信息

- **序号**：0872 / 4088
- **哈希**：4bd9e64f283bc80b2fd41cec2fe939dd5697db0a
- **短哈希**：4bd9e64f2
- **日期**：2024-06-25 12:18:05 +0200
- **作者**：dongwang
- **提交说明**：Spark: Remove useless code in `TestRemoveOrphanFilesProcedure` (#10562)
- **PR/Issue**：#10562

## 总体目的

本次提交针对 Spark 模块下三个版本（v3.3、v3.4、v3.5）的 `TestRemoveOrphanFilesProcedure` 测试类做了一处小规模的清理工作。这些测试类中各自存在一个未被使用的局部变量 `metadataLocation`，它的值被计算出来后从未被任何后续逻辑读取，属于典型的"死代码"（dead code）。

清理掉这种无用代码有助于减少阅读测试代码时的干扰，避免读者误以为该变量后续会被使用，也避免 IDE 中的告警噪音。同时，由于这些是测试文件，删除该变量不会影响生产代码、API 兼容性或运行时行为。

## 如何达成设计目的

实现方式非常直接：在三个版本对应的 `TestRemoveOrphanFilesProcedure.java` 文件中，分别删除一行 `String metadataLocation = table.location() + "/metadata";`。该行紧接在 `String dataLocation = table.location() + "/data";` 之前，删除后 `dataLocation` 的定义与上下文完全保留，不影响后续逻辑。

## 修改详情

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java`

**修改目的**：删除未被使用的局部变量 `metadataLocation`。
**工作逻辑**：仅删除一行声明，其余逻辑不变。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java`

**修改目的**：删除未被使用的局部变量 `metadataLocation`。
**工作逻辑**：仅删除一行声明，其余逻辑不变。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java`

**修改目的**：删除未被使用的局部变量 `metadataLocation`。
**工作逻辑**：仅删除一行声明，其余逻辑不变。

## 小结

- **成效**：清理了三个 Spark 版本测试类中无用的局部变量 `metadataLocation`，减少代码噪音。
- **影响范围**：仅涉及 Spark v3.3、v3.4、v3.5 三个模块的测试文件，每个文件 1 行删除，共 3 行删除。
- **回迁到 1.4.x 的注意事项**：属于纯测试代码清理，回迁风险极低。若 1.4.x 分支对应版本测试文件中存在相同的无用变量，可安全回迁；若不存在则无需回迁。该改动不影响任何生产逻辑。
