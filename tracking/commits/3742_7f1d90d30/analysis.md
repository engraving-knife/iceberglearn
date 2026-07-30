# 提交 3742：Docs: add more to 1.10.2 release notes (#16410)

## 提交信息

- **序号**：3742 / 4088
- **哈希**：7f1d90d307fd74c7a8ceaba10b586221ac5e8ef7
- **短哈希**：7f1d90d30
- **日期**：2026-05-18 22:48:12 -0700
- **作者**：Kevin Liu
- **提交说明**：Docs: add more to 1.10.2 release notes (#16410)
- **PR/Issue**：#16410

## 总体目的

本提交是对 1.10.2 发布说明的补充完善。在 #16406 创建初始发布说明后，发现遗漏了一些同样包含在 1.10.2 版本中的修复，特别是多个模块的 LICENSE/NOTICE 文件修复 PR（之前只列了一个 PR 编号，实际有多个），以及 Open API 模块停止发布运行时 Jar 这一重要变更。本提交将这些遗漏的内容补充到发布说明中，使发布说明更完整、准确，便于用户全面了解 1.10.2 的改动。

## 如何达成设计目的

修改 `site/docs/releases.md` 中 1.10.2 发布说明段落：
1. 在 Flink、Spark、Azure 三个模块的 LICENSE/NOTICE 修复条目中补充遗漏的 PR 编号（#16130、#16162、#16260）。
2. 新增 Open API 模块的变更条目，说明停止发布 Open API 运行时 Jar 及对用户的影响。

## 修改详情

### `site/docs/releases.md` (+5/-3 lines)

**修改目的**：补充遗漏的 PR 和新增 Open API 变更条目。

**工作逻辑**：
- Flink 模块 LICENSE/NOTICE 修复：从 `#16175` 补充为 `#16175, #16130`。
- Spark 模块 LICENSE/NOTICE 修复：从 `#16255` 补充为 `#16255, #16162`。
- Azure 模块 LICENSE/NOTICE 修复：从 `#16242` 补充为 `#16242, #16260`。
- 新增 Open API 条目：
```markdown
* Open API
    - Stop publishing the Open API runtime Jar due to license compliance issues ([#16188](https://github.com/apache/iceberg/pull/16188)). Users who depended on the shadow JAR must now use the `test-fixtures` classifier and explicitly add `iceberg-core`, `iceberg-core:tests`, `jetty-server`, `jetty-servlet`, `sqlite-jdbc`, and `hadoop-common`.
```
  该条目说明由于许可证合规问题停止发布 Open API 运行时 Jar（shadow JAR），依赖该 Jar 的用户需要改用 `test-fixtures` classifier 并显式添加 `iceberg-core`、`iceberg-core:tests`、`jetty-server`、`jetty-servlet`、`sqlite-jdbc`、`hadoop-common` 等依赖。这是一个对用户有破坏性影响的变更，需要用户在升级时注意。

## 总结

本提交补充完善了 1.10.2 发布说明，主要变更：为 Flink、Spark、Azure 三个模块的 LICENSE/NOTICE 修复条目补充遗漏的 PR 编号；新增 Open API 模块停止发布运行时 Jar 的重要变更说明，并明确告知受影响用户需要改用 `test-fixtures` classifier 并显式添加相关依赖。这是发布说明的准确性完善，特别是 Open API 的变更对用户有实际影响，需要用户在升级 1.10.2 时关注。
