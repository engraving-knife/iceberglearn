# 提交 1355：Docs: Site Update for 1.7.0 Release (#11494)

## 提交信息

- **序号**：1355 / 4088
- **哈希**：6a16340947ec7f596133104d7945555aabdeee86
- **短哈希**：6a1634094
- **日期**：2024-11-08（Fri Nov 8 13:34:08 2024 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Docs: Site Update for 1.7.0 Release (#11494)
- **PR/Issue**：#11494
- **协作者**：Fokko Driesprong

## 总体目的

Iceberg 官网（基于 mkdocs 构建，源文件在 `site/` 目录）需要在每次发布时同步更新：在发布说明页 `releases.md` 中加入新版本的摘要列表，把站点全局变量 `icebergVersion` 切到新版本，并在导航 `nav.yml` 中加入新版本的文档子站入口。1.7.0 发布在即，需要把这些站点级配置与内容一并更新。

本提交完成 1.7.0 的站点更新：(1) 在 `releases.md` 中新增 "1.7.0 release" 章节，按 API / AWS / Build / Dependencies / Core / Flink / GCS / Hive / OpenAPI / Spark / Spec 等模块列出本版本的重要变更项及其 PR 链接；(2) 把 `mkdocs.yml` 中的 `icebergVersion` 从 `1.6.1` 改为 `1.7.0`，使站点中通过 `{{ icebergVersion }}` 引用版本号的页面（如依赖说明、下载指引）显示 1.7.0；(3) 在 `nav.yml` 中新增 `1.7.0` 子站入口，让用户可访问 1.7.0 版本固化文档。此外，顺手修正了 1.6.0 章节中 "Kafak" 的拼写错误（改为 "Kafka"）。

## 如何达成设计目的

通过三个文件的协同编辑完成站点发布更新：

1. `site/docs/releases.md`：在 1.6.0 章节之前插入完整的 1.7.0 release notes，按模块分组列出变更条目，每条带 PR 链接；同时把 1.6.0 描述中的 "Kafak" 修正为 "Kafka"。
2. `site/mkdocs.yml`：把 `extra.icebergVersion` 由 `'1.6.1'` 改为 `'1.7.0'`。
3. `site/nav.yml`：在 `Docs` 导航下、`1.6.1` 之前插入 `- 1.7.0: '!include docs/docs/1.7.0/mkdocs.yml'`。

这些都是站点配置与文档内容变更，无代码逻辑。

## 修改详情

### `site/docs/releases.md`

**修改目的**：新增 1.7.0 release notes，修正 1.6.0 章节的拼写错误。

**工作逻辑**：在 1.6.0 章节之前插入 "### 1.7.0 release" 段落，开头说明本版本包含修复、依赖更新与新功能，并指向 GitHub 完整 release notes；随后按模块分组列出主要变更：

- **Deprecation / End of Support**：Java 8、Apache Pig
- **API**：`SupportsRecoveryOperations` mixin for FileIO、默认值 API 与 Avro 实现、Schema 默认值兼容性检查、`timestamp_ns`/`timestamptz_ns` 类型、`addNonDefaultSpec` 等
- **AWS**：S3OutputStream 不在 finalize 时完成 multipart upload、S3FileIO 实现 `SupportsRecoveryOperations`、刷新 vended credentials、S3 directory bucket listing、S3 跨区域访问
- **Build**：构建切换到 Java 11（移除 Java 8）、checkstyle 禁用 assert、支持 Java 21 构建
- **Dependencies**：AWS SDK 2.29.1、Avro 1.12.0、Spark 3.4.4 / 3.5.2、Netty 4.1.114.Final、Jetty 11.0.24、Kafka 3.8.0、Nessie 0.99.0、ORC 1.9.4、Roaring Bitmap 1.3.0、Spring 5.3.39、Sqllite JDBC 3.46.0.0、Hadoop 3.4.1
- **Core**：`RewriteDataFilesAction` 移除 dangling deletes、分区统计工具、`estimateRowCount`、portable Roaring bitmap for row positions、rewritten delete files 写入结果、Table Version 3 基础类、`ContentCache.invalidateAll` 弃用、弃用旧位置删除加载方式、manifest 并行写入、支持不同 spec 的文件追加
- **Flink**：基于 V2 Sink 抽象的新 `IcebergSink`、planned Avro reads、FLIP-27 批模式并行度推断、FLIP-27 在 SQL 中设为默认并弃用旧 FlinkSource、FLIP-27 limit pushdown
- **GCS**：刷新 vended credentials
- **Hive**：HIVE catalog 支持 View
- **OpenAPI**：`RemovePartitionSpecsUpdate`、表 credentials 端点、`loadTable/loadView` 凭据标准化、Scan Planning 端点、REST Compatibility Kit
- **Spark**：migrate procedures 并行读取、表统计 compute action、dangling deletes remove action、可靠加载表状态工具、仅改 local order 不变 distribution、planned Avro reads、Analyze table action、Column Stats、`RewriteTablePath` action 接口
- **Spec**：v3 类型与类型提升、Row Lineage、弃用 file system table scheme

同时把 1.6.0 章节描述中的 "Kafak Connect" 改为 "Kafka Connect"。

### `site/mkdocs.yml`

**修改目的**：把站点全局版本变量切到 1.7.0。

**工作逻辑**：`extra.icebergVersion` 由 `'1.6.1'` 改为 `'1.7.0'`。所有通过 `{{ icebergVersion }}` 模板变量引用 Iceberg 版本号的页面（如依赖安装示例、JAR 名）会随之显示 1.7.0。

### `site/nav.yml`

**修改目的**：在导航中加入 1.7.0 文档子站入口。

**工作逻辑**：在 `Docs` 节点下、`1.6.1` 之前插入：

```yaml
    - 1.7.0: '!include docs/docs/1.7.0/mkdocs.yml'
```

使网站顶部导航出现 1.7.0 版本切换入口，用户可浏览 1.7.0 发布时固化的文档快照。

## 小结

- **成效**：Iceberg 官网完成 1.7.0 发布的站点级更新——发布说明、全局版本变量、版本导航入口均同步到 1.7.0；并修正了 1.6.0 章节的 "Kafak" 拼写。
- **影响范围**：3 个文件（`site/docs/releases.md`、`site/mkdocs.yml`、`site/nav.yml`），共新增约 82 行、修改 2 行，无代码、构建或运行时变更。
- **回迁到 1.4.x 的注意事项**：这是 main 分支官网对 1.7.0 发布的站点更新，与 1.4.x 维护分支的发布产物无关。1.4.x 自身的发布说明与站点配置已随其发布时定型，不应回迁 main 上的 1.7.0 站点更新——回迁会让 1.4.x 分支的站点变量与导航指向 1.7.0，与该分支维护的版本不符。**无需回迁**。
