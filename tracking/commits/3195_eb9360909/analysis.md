# 提交 3195：ORC: Fix typos in IdToOrcName and ORC JavaDoc (#15214)

## 提交信息

- **序号**：3195 / 4088
- **哈希**：eb9360909cc64397fa8e8af5effa5f0079b646a3
- **短哈希**：eb9360909
- **日期**：2026-02-01
- **作者**：Chihiro
- **提交说明**：ORC: Fix typos in IdToOrcName and ORC JavaDoc (#15214)
- **PR/Issue**：#15214

## 总体目的

本提交修复 Iceberg ORC 模块中两处文档注释（JavaDoc）的英文拼写错误。ORC 是 Iceberg 支持的开源列式存储格式之一（与 Avro、Parquet 并列），`IdToOrcName` 负责生成 Iceberg 字段 ID 到 ORC 限定列名的映射，是 Iceberg 与 ORC 之间字段名对齐的关键组件；`ORC` 类则是 ORC 读写入口的构建器集合。这两处错误虽不影响运行时行为，但会误导阅读源码的开发者，并对以这些 JavaDoc 为依据生成 API 文档的产物造成低质量内容。

具体错误有二：其一，`IdToOrcName` 类级 JavaDoc 中将 `enclose`（包围）误写为 `enclose` 的第三人称形式 `encloses` 时漏掉了一个字母——原文 `This visitor also enclose column names` 主语为单数 `This visitor`，谓语应为 `encloses`；其二，`ORC.java` 中 `createContextFunc` 方法上方的行内注释首单词 `supposed` 大小写有误（小写开头），应为句子首字母大写 `Supposed`。本提交将两处分别订正为语法正确的 `encloses` 与首字母大写的 `Supposed`，使注释符合英文语法规范。

## 如何达成设计目的

改动仅限两个文件的注释文本，分别修正一处动词单数形式与一处首字母大小写，不触碰任何可执行代码逻辑。

## 修改详情

### `orc/src/main/java/org/apache/iceberg/orc/IdToOrcName.java` (+1/-1 lines)

**修改目的**：修正类级 JavaDoc 中动词与主语数的一致性。

**工作逻辑**：
将类级 JavaDoc 中 `This visitor also enclose column names in backticks i.e. \` so that ORC can correctly parse` 中的 `enclose` 改为 `encloses`。因主语 `This visitor` 为单数第三人称，谓语动词应使用单数形式 `encloses`，修正后注释语法正确，准确描述该访问者会把列名用反引号包裹以便 ORC 正确解析含特殊字符的列名。

### `orc/src/main/java/org/apache/iceberg/orc/ORC.java` (+1/-1 lines)

**修改目的**：修正行内注释首单词大小写。

**工作逻辑**：
将 `createContextFunc` 方法上方的注释 `// supposed to always be a private method used strictly by data and delete write builders` 改为 `// Supposed to always be a private method used strictly by data and delete write builders`，即将首单词 `supposed` 首字母大写为 `Supposed`，使注释以符合英文规范的句首大写开头。

## 总结

本提交修正 ORC 模块两处 JavaDoc/注释的英文拼写与语法错误（动词单数形式、句首大小写），虽不改变任何运行时行为，但提升了源码与生成文档的可读性与专业度，属轻量的文档质量改进。
