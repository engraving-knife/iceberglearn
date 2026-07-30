# 提交 3240：Docs: add blog post for iceberg-python 0.11.0 release (#15290)

## 提交信息

- **序号**：3240 / 4088
- **哈希**：533d2c9ac2d58ae8dc6b3e3fdeb529da07f8056f
- **短哈希**：533d2c9ac
- **日期**：2026-02-11
- **作者**：geruh
- **提交说明**：Docs: add blog post for iceberg-python 0.11.0 release (#15290)
- **PR/Issue**：#15290

## 总体目的

本提交为 Apache Iceberg Python（PyIceberg）0.11.0 版本发布新增一篇官方博客文章，发布在 Iceberg 网站的博客板块。PyIceberg 是 Iceberg 项目的 Python 客户端实现，0.11.0 是一个重要里程碑版本，包含超过 380 个 PR、50+ 位贡献者（其中 28 位首次贡献者）。博客文章的目的是向社区和用户全面传达本次发布的亮点功能、破坏性变更、基础设施改进以及贡献者致谢，帮助用户了解升级路径和新能力。

文章内容涵盖了以下核心领域的新功能：读写性能优化（DeleteFileIndex 加速删除文件查找、生成器写入降低内存压力、修复 O(N²) manifest 缓存增长问题）、快照管理（回滚到指定快照 ID、回滚到时间点、直接设置当前快照）、Catalog 增强（Entra ID/Azure AD 认证管理器、可配置命名空间分隔符、AWS profile 支持、REST 请求 X-Client-Version 头）、ORC 读取支持、排序顺序演进（Sort Order Evolution）、ConfigResponse 中声明支持的端点、REST 服务端扫描计划等。破坏性变更包括放弃 Python 3.9 支持和移除已弃用方法。基础设施方面新增了 Python 3.13 支持、aarch64 wheel 构建、迁移到 UV 依赖管理工具以及 pyiceberg-core 0.8.0（含 DataFusion 51）。

## 如何达成设计目的

在 `site/docs/blog/posts/` 目录下新增一个 Markdown 文件，文件名以发布日期和主题命名（`2026-02-10-iceberg-python-0.11.0-release.md`）。文件头部使用 Docusaurus 博客前导元数据（frontmatter）声明日期、标题、slug、作者和分类。正文按功能领域分组列出亮点，每项附有指向 iceberg-python 仓库对应 PR 的链接，方便用户深入查看。最后包含贡献者统计（通过 `git shortlog` 生成，排除了 dependabot 机器人）和参与社区的引导信息。

## 修改详情

### `site/docs/blog/posts/2026-02-10-iceberg-python-0.11.0-release.md` (+157/-0 lines)

**修改目的**：新增 PyIceberg 0.11.0 版本发布博客文章。

**工作逻辑**：

- **Frontmatter**：声明 `date: 2026-02-10`、`title: Apache Iceberg Python 0.11.0 Release`、`slug: apache-iceberg-python-0.11.0-release`、`authors: [iceberg-pmc]`、`categories: [release]`，符合 Docusarius 博客文章的标准格式。

- **开篇概述**：介绍 0.11.0 包含 380+ PR、50+ 贡献者（28 位首次），并指向完整 changelog。

- **Release Highlights**：按功能领域分组：
  - **Reads and Writes**：DeleteFileIndex（#2918）加速删除文件查找；生成器写入（#2671）降低内存压力；放宽 `add_files` 的 `field-id` 约束（#2662）；远程 S3 签名连接复用（#2543）；多进程安全的 ExecutorFactory（#2546）；修复 O(N²) manifest 缓存增长（#2951）。
  - **Snapshot Management**：回滚到指定快照 ID（#2878）、回滚到时间点（#2879）、直接设置当前快照（#2871）。
  - **Catalog Improvements**：Entra ID (Azure AD) 认证管理器（#2974）；可配置命名空间分隔符（#2826）和新 `namespace_exists` 方法（#2972）；`rename_table` 验证源/目标命名空间存在（#2588）；REST 请求 X-Client-Version 头（#2910）；Glue 和 fsspec S3 FileIO 的 AWS profile 支持（#2948）；fsspec ADLS FileIO 的 `anon` 属性（#2661）和 S3 `addressing_style` 支持（#2517）。
  - **ORC Read Support**：PyArrow I/O 层新增完整 ORC 读取支持（#2432）。
  - **Sort Order Evolution**：现有表可直接更新排序顺序，无需重建（#2552）。
  - **Supported Endpoints in ConfigResponse**：REST catalog 可通过 `ConfigResponse` 声明支持的端点（#2848），客户端据此检查操作是否被支持，对旧服务器回退默认值。
  - **REST Scan Planning**：REST catalog 支持服务端扫描计划（#2864），当前为同步模式（客户端发送扫描请求、服务端返回文件扫描任务），异步模式计划在未来版本实现。

- **Breaking Changes**：放弃 Python 3.9 支持（#2554）；移除 0.11.0 前弃用的方法（#2983）。

- **Infrastructure Improvements**：Python 3.13 支持（#2863）；aarch64 wheel 构建（#2973）；迁移到 UV 依赖管理（#2601）；pyiceberg-core 0.8.0 含 DataFusion 51（#2928）。

- **Contributors**：通过 `git shortlog`（排除 dependabot）列出全部贡献者及提交数，顶部贡献者为 Kevin Liu (44)、Drew Gallardo (30)、Alex Stephen (19)、Fokko Driesprong (16)。

- **Getting Involved**：引导用户通过 GitHub issues、Slack、贡献者指南和 good first issues 参与社区。

## 总结

本提交是一篇纯文档新增，为 PyIceberg 0.11.0 这一重要版本发布撰写了详尽的博客文章。文章系统性地介绍了读写优化、快照管理、Catalog 增强、ORC 支持、排序演进、REST 扫描计划等核心新功能，明确了放弃 Python 3.9 等破坏性变更，并致谢了 50+ 位贡献者。这对用户了解升级路径、评估新功能价值具有实际指导意义，也体现了 Iceberg 社区对版本发布透明度和社区建设的重视。
