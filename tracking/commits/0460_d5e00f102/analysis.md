# 提交 0460：Docs: Update Nessie URI to API v2 (#9648)

## 提交信息

- **序号**：0460
- **完整哈希**：d5e00f1022f2e85572bb02e3e2d2b651612958da
- **短哈希**：d5e00f102
- **日期**：2024-02-05 14:46:19 +0530
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Docs: Update Nessie URI to API v2 (#9648)
- **关联 PR**：#9648
- **修改文件**：1 个，共 4 行新增、4 行删除

## 总体目的

本提交是 Iceberg 文档中关于 Nessie catalog 配置示例的一项准确性修正。Nessie 是一个支持分支与版本管理的开源数据目录，可作为 Iceberg 的 catalog 实现使用。在 Iceberg 文档 `docs/docs/nessie.md` 中，给出了通过 Java API、Spark 以及 Flink 三种方式配置 Nessie catalog 的代码示例，这些示例中包含 Nessie 服务的 `uri` 配置项。

在示例中，`uri` 此前使用的是 `http://localhost:19120/api/v1`（基于 REST API v1 版本）。随着 Nessie 服务端演进到推荐使用 API v2，文档示例需要同步更新，将路径前缀从 `/api/v1` 改为 `/api/v2`，以引导用户采用当前推荐的 API 版本。这是一个避免用户照搬过时示例、确保新部署使用正确 API 版本的文档准确性维护。

更新覆盖了文档中全部三处出现该 URI 示例的位置（Java、Spark、Flink 各一处），保证三种集成方式的示例一致。

## 如何达成设计目的

实现路径直接明了：在 `docs/docs/nessie.md` 文件中，将四处 Nessie URI 示例（一处文字说明中的示例、一处 Java 代码块、一处 Spark 配置代码块、一处 Flink SQL 代码块）中的 `/api/v1` 统一替换为 `/api/v2`。端口（19120）与协议（http/https）保持不变，仅修改 API 版本路径段。

## 修改详情

### docs/docs/nessie.md

**修改目的**：将 Nessie catalog 配置示例中的 REST API 版本从 v1 更新到 v2。

**工作逻辑**：文件中共有四处涉及 URI 示例的文本被修改，均为将 `/api/v1` 替换为 `/api/v2`：

1. **属性说明处**（约第 57 行）：文字描述中给出的示例 `uri`，由 `http://localhost:19120/api/v1` 改为 `http://localhost:19120/api/v2`。此处用于向用户说明 `uri` 属性的含义与格式。

2. **Java 代码示例处**（约第 66 行）：在 "To run directly in Java this looks like" 代码块中，`options.put("uri", "https://localhost:19120/api/v1")` 改为 `options.put("uri", "https://localhost:19120/api/v2")`。注意此处使用的是 `https` 协议。

3. **Spark 配置示例处**（约第 74 行）：在 "and in Spark" 代码块中，`conf.set("spark.sql.catalog.nessie.uri", "http://localhost:19120/api/v1")` 改为 `conf.set("spark.sql.catalog.nessie.uri", "http://localhost:19120/api/v2")`。

4. **Flink SQL 配置示例处**（约第 94 行）：在 Flink `CREATE CATALOG` 语句中，`'uri'='http://localhost:19120/api/v1'` 改为 `'uri'='http://localhost:19120/api/v2'`。

四处修改保持端口（19120）与各示例原有的协议（http 或 https）不变，仅将 API 路径版本号统一提升为 v2。

## 小结

本提交是一次纯文档修正，不涉及代码逻辑变更。其核心价值在于消除文档中过时的 API v1 示例，引导用户在配置 Nessie catalog 时使用当前推荐的 API v2 端点，避免新部署因照搬文档而使用已被取代的 API 版本。修改范围小且聚焦（4 处文本替换），风险极低。
