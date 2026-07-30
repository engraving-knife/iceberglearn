# 提交 4057：Site: add Iceberg Rust and DataFusion Comet blog post (#17162)

## 提交信息

- **序号**：4057 / 4088
- **哈希**：6ec1a01cb92252bc26516947d4aa6034267549ca
- **短哈希**：6ec1a01cb
- **日期**：2026-07-16 16:25:03 -0700
- **作者**：Matt Butrovich
- **提交说明**：Site: add Iceberg Rust and DataFusion Comet blog post (#17162)
- **PR/Issue**：#17162

## 总体目的

这个提交在 Iceberg 官方网站博客栏目发布了一篇新博客文章，题为「Accelerating Apache Spark Queries (and Iceberg Rust Development) with Apache DataFusion Comet」。文章由 Matt Butrovich（Apache Iceberg 贡献者、Apache DataFusion PMC 成员）撰写，探讨了 Iceberg Rust 与 Apache DataFusion Comet 之间的关系及其带来的双向收益。

文章核心内容涵盖两个主题：(1) Comet 通过 Iceberg Rust 原生执行 Spark 对 Iceberg 表的读取，加速 Spark 查询；(2) 这一集成将 Iceberg Java 近 10,000 个 Spark 测试转化为差分测试（differential testing）框架——Iceberg Rust 在大量真实场景中得到验证，同时这种比对甚至发现了 Iceberg Java 自身的 bug，修复后的成果惠及所有基于这些库的项目。

## 如何达成设计目的

新增一篇博客 Markdown 文章及配套的多张图片资源，并在博客作者配置文件中注册新作者信息。文章采用 mkdocs 博客规范，包含 front matter（日期、标题、slug、作者、分类）和正文，正文中配有 5 张示意图说明 Comet 的架构、计划翻译、任务翻译、TDD 循环和 TPC-DS 性能对比等内容。

## 修改详情

### `site/docs/blog/posts/2026-07-15-accelerating-iceberg-rust-development-with-datafusion-comet.md` (+229/-0 lines)

**修改目的**：新增博客文章正文。

**工作逻辑**：文章 front matter 设定日期 2026-07-15、标题、slug、作者 mbutrovich、分类 blog。正文从 Iceberg 生态背景出发，介绍 Iceberg Java 作为参考实现及其成熟的 Spark 集成，然后引出 Iceberg Rust 和 DataFusion Comet 的角色：Comet 作为 Spark 的原生加速层，通过 Iceberg Rust 读取 Iceberg 表数据。文章详细阐述了差分测试方法——利用 Iceberg Java 已有的近万条 Spark 测试作为对照基准，验证 Iceberg Rust 实现的正确性，同时这种双向比对也能发现 Iceberg Java 的潜在 bug。文中配有架构图、计划翻译流程图、任务翻译流程图、TDD 循环图和 TPC-DS 性能对比图。

### `site/docs/blog/.authors.yml` (+4/-0 lines)

**修改目的**：注册博客新作者。

**工作逻辑**：
```yaml
mbutrovich:
  name: Matt Butrovich
  description: Apache Iceberg Contributor, Apache DataFusion PMC Member
  avatar: https://github.com/mbutrovich.png
```
新增作者 mbutrovich 的信息，用于博客文章的作者展示。

### 5 张图片资源 (binaries)

**修改目的**：为博客文章提供配图。

**工作逻辑**：新增以下图片：
- `2026-07-15-comet-differential-testing.png`：差分测试示意图
- `2026-07-15-comet-plan-translation.png`：计划翻译流程
- `2026-07-15-comet-task-translation.png`：任务翻译流程
- `2026-07-15-comet-tdd-loop.png`：TDD 循环
- `2026-07-15-comet-tpcds.png`：TPC-DS 性能对比

## 总结

纯内容提交，发布了一篇关于 Iceberg Rust 与 DataFusion Comet 集成的技术博客。文章阐述了 Comet 通过 Iceberg Rust 加速 Spark 查询的机制，以及利用 Iceberg Java 测试套件进行差分测试、双向发现 bug 的实践。这是 Iceberg 社区对外分享跨语言生态协作经验的重要内容产出。
