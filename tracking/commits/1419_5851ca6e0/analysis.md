# 提交 1419：Docs: Mention HIVE-28121 for MySQL/MariaDB-based HMS users (#11631)

## 提交信息

- **序号**：1419 / 4088
- **哈希**：5851ca6e0797bb84d669f27bb9ebc4a7b2a00361
- **短哈希**：5851ca6e0
- **日期**：2024-11-23（Sat Nov 23 14:28:24 2024 +0800）
- **作者**：Cheng Pan <chengpan@apache.org>
- **提交说明**：Docs: Mention HIVE-28121 for MySQL/MariaDB-based HMS users (#11631)
- **PR/Issue**：#11631

## 总体目的

Iceberg 的 `configuration.md` 文档中有一段说明 `HiveCatalog` 在提交时关闭 Hive 锁的配置项（`commit-lock-enabled=false`）。该配置项只有在满足若干前置条件时才允许设置，包括：HMS 已修复 HIVE-26882、所有相关 HiveCatalog 都在 Iceberg 1.3+、且都关闭了 Hive 锁。

但社区发现一个补充条件：当 HMS 后端数据库是 MySQL 或 MariaDB 时，还需要 HMS 修复 HIVE-28121 才能安全关闭 Hive 锁。否则在某些边界场景下（如 HMS 事务隔离级别与死锁处理），关闭锁可能导致元数据一致性问题。本提交在文档中补充该条件，提醒 MySQL/MariaDB 后端的 HMS 用户在关闭 Hive 锁前确认已部署 HIVE-28121 修复。

## 如何达成设计目的

直接编辑 `docs/docs/configuration.md`，在原有的 HIVE-26882 条件项之后插入一条 HIVE-28121 条件项，说明它仅在 HMS 后端为 MySQL 或 MariaDB 时需要。这是纯文档说明，无代码逻辑变更。

## 修改详情

### `docs/docs/configuration.md`

**修改目的**：在关闭 Hive 锁的前置条件清单中补充 HIVE-28121。

**工作逻辑**：在原文档关于 `commit-lock-enabled=false` 的前置条件列表中，在 HIVE-26882 条目之后新增两行：

```markdown
 - [HIVE-28121](https://issues.apache.org/jira/browse/HIVE-28121)
is available on the Hive Metastore server, if it is backed by MySQL or MariaDB
```

该条目与上方 HIVE-26882 条目格式一致（链接 + 说明文字跨两行），并明确限定条件"if it is backed by MySQL or MariaDB"，仅对 MySQL/MariaDB 后端的 HMS 生效。该条目位于"所有 HiveCatalog 都在 1.3+"与"都关闭 Hive 锁"两条之前，保持了原列表"先 HMS 修复、再集群一致性"的逻辑顺序。

## 小结

- **成效**：文档明确提醒 MySQL/MariaDB 后端 HMS 用户在关闭 Hive 锁前需确认 HIVE-28121 修复已部署，避免在未修复环境下因关闭锁导致元数据一致性问题。
- **影响范围**：仅 `docs/docs/configuration.md` 一个文档文件，新增 2 行，无代码或构建逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是纯文档补充说明，与产品版本功能无关，对 1.4.x 运行时无任何影响。是否回迁取决于 1.4.x 是否维护该文档段落：若 1.4.x 的 `configuration.md` 中存在同样的 `commit-lock-enabled=false` 前置条件清单，则**建议回迁**以提醒 1.4.x 用户该约束；该回迁风险为零（仅文档文字）。若 1.4.x 的文档结构与 main 不同（如段落位置不同），按 1.4.x 实际结构调整插入位置即可。
