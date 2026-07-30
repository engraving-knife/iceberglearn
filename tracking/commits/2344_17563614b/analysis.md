# 提交 2344：Docs: Add missing VIEW keyword in View creation DDL (#13539)

## 提交信息

- **序号**：2344 / 4088
- **哈希**：17563614ba2b59d3ab934e773494077fc1b1ae30
- **短哈希**：17563614b
- **日期**：2025-07-14 09:18:18 +0200
- **作者**：david yuan
- **提交说明**：Docs: Add missing VIEW keyword in View creation DDL (#13539)
- **PR/Issue**：#13539

## 总体目的

本提交修复了 Spark DDL 文档中视图创建语句缺少 `VIEW` 关键字的拼写错误。

在 `docs/docs/spark-ddl.md` 文档中，展示使用 `CREATE OR REPLACE` 更新视图的 DDL 示例时，SQL 语句写成了 `CREATE OR REPLACE <viewName> ...`，缺少了 `VIEW` 关键字。正确的 Spark SQL 语法应该是 `CREATE OR REPLACE VIEW <viewName> ...`。

这个错误会导致用户照抄文档中的 SQL 语句时遇到语法错误，影响文档的可用性和用户体验。

## 如何达成设计目的

在文档的 SQL 示例中补上缺失的 `VIEW` 关键字，使语句符合 Spark SQL 的正确语法。

## 修改详情

### `docs/docs/spark-ddl.md` (+1/-1 lines)

**修改目的**：修复视图创建 DDL 示例中缺失的 `VIEW` 关键字。

**工作逻辑**：将 `CREATE OR REPLACE <viewName> (updated_id COMMENT 'updated ID')` 改为 `CREATE OR REPLACE VIEW <viewName> (updated_id COMMENT 'updated ID')`，补上 `VIEW` 关键字，使 SQL 语句语法正确。

## 总结

本提交是纯文档修复，补上了 Spark DDL 文档中视图创建示例缺失的 `VIEW` 关键字，确保用户可以正确使用文档中的 SQL 示例。
