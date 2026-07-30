# 提交 1634 6e2bc9ac4 分析

## 提交信息
- 哈希：6e2bc9ac4ef9ca9afeff66814de6567ae63da9da
- 日期：2025-01-24 16:02:04 +0200
- 作者：Bjorn Olsen
- 消息：Spark: Fix typo in `NoSuchTableException` (#12091)

## 总体目的

本提交修复一处英文拼写错误（双重否定）。在 `BaseTableCreationSparkAction` 加载源表失败时抛出的异常消息中，原文本为 “Cannot not find source table '%s'”，其中 “not” 多写了一次，造成 “Cannot not find” 这种语义错误的句子。本提交将其修正为 “Cannot find source table '%s'”。

这是一个纯文档级（错误消息文案）的修正，不改变任何控制流或异常类型，仅让面向用户的错误信息更准确、专业。

该错误消息出现在创建表的 Spark 动作（如 `CreateOrReplaceTableSparkAction` / `CreateTableSparkAction` 等继承自 `BaseTableCreationSparkAction` 的动作）中，当指定的源表在 catalog 中找不到时，用户会看到这条消息。修正后用户体验更友好，也便于在日志/告警中检索关键词。

## 如何达成设计目的

设计思路：直接修正字符串字面量。由于该消息在三个 Spark 版本模块（v3.3、v3.4、v3.5）的同名文件中各出现一次，本提交同时修正三处，保持三个分支文案一致。

### 修改详情

#### spark/v33/spark/src/main/java/org/apache/iceberg/spark/actions/BaseTableCreationSparkAction.java
#### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/BaseTableCreationSparkAction.java
#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/BaseTableCreationSparkAction.java

三处修改完全相同，均在加载源表的 try/catch 块中：

修改前：
    throw new NoSuchTableException("Cannot not find source table '%s'", sourceTableIdent);

修改后：
    throw new NoSuchTableException("Cannot find source table '%s'", sourceTableIdent);

工作逻辑：当 `sourceCatalog.loadTable(sourceTableIdent)` 抛出 Spark 的 `NoSuchTableException` 时，代码捕获并转换为 Iceberg 自身的 `NoSuchTableException` 重新抛出，消息中带上源表标识。修正仅针对消息文案，异常类型、参数格式（%s 占位符）、抛出时机均不变。

## 小结

- 成效：修正了三处错误消息中的拼写错误，使异常文案准确。无功能影响，无测试变更（文案类修正通常不补测试）。
- 影响范围：仅影响 Spark 3.3/3.4/3.5 三个模块的 `BaseTableCreationSparkAction` 错误路径文案，不影响正常路径或其它模块。
- 回迁到 1.4.x 的注意事项：1.4.x 分支仅维护 Spark 3.5（iceberg 1.4.x 已不再支持 spark 3.3/3.4），回迁时只需修正 `spark/v3.5` 那一处即可。改动极小、无风险，可直接 cherry-pick 对应文件的对应行。注意确认 1.4.x 该行文案是否仍为 “Cannot not find”（若 1.4.x 较早且文案已不同则无需回迁）。
