# 提交 3405：Docs: Add HadoopTables lock configuration to Hadoop configuration section (#15520)

## 提交信息

- **序号**：3405 / 4088
- **哈希**：eb460a524f3a192e3584a3bdabcff237c38b04a4
- **短哈希**：eb460a524f
- **日期**：2026-03-16 20:34:12 -0700
- **作者**：Anshul Baliga
- **提交说明**：Docs: Add HadoopTables lock configuration to Hadoop configuration section (#15520)
- **PR/Issue**：#15520

## 总体目的

在 Hadoop 配置文档中添加 HadoopTables 锁配置说明。HadoopTables（不使用 catalog 的表）可以使用锁属性来确保在 S3 等缺乏原生写入互斥的文件系统上实现原子提交，但之前文档中缺少这部分说明。需要补充如何通过 `iceberg.tables.hadoop.` 前缀配置锁属性。

## 如何达成设计目的

- 在 `docs/docs/configuration.md` 的 "Hadoop configuration" 章节下新增 "HadoopTables Lock Configuration" 子章节
- 说明如何使用 `iceberg.tables.hadoop.` 前缀配置锁属性
- 提供使用 DynamoDB 作为锁管理器的示例配置
- 将原有的 Hive Metastore 配置内容调整为单独子章节

## 修改详情

### `docs/docs/configuration.md` (+9 lines)

**修改目的**：补充 HadoopTables 锁配置文档。

**工作逻辑**：
- 新增 "HadoopTables Lock Configuration" 子章节，说明：
  - HadoopTables 可以通过 `iceberg.tables.hadoop.` 前缀配置锁属性
  - 这些属性来自 Lock catalog properties 章节
  - 用途是确保在 S3 等缺乏原生写入互斥的文件系统上实现原子提交
- 添加 info 提示框，说明如何使用 DynamoDB 作为锁管理器：
  - 设置 `iceberg.tables.hadoop.lock-impl` 为 `org.apache.iceberg.aws.dynamodb.DynamoDbLockManager`
  - 设置 `iceberg.tables.hadoop.lock.table` 为 DynamoDB 表名
  - 参考 DynamoDB Lock Manager 文档
- 将原有 Hive Metastore 内容调整为 "Hive Metastore Configuration" 子章节

## 总结

本提交为文档补充了 HadoopTables 的锁配置说明，帮助用户在 S3 等文件系统上使用 HadoopTables 时配置锁管理器以实现原子提交。提供了 DynamoDB 锁管理器的具体配置示例。
