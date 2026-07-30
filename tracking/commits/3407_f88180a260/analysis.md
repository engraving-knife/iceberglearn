# 提交 3407：Add AGENTS.md with project conventions for AI coding agents (#15529)

## 提交信息

- **序号**：3407 / 4088
- **哈希**：f88180a2607ecb8ffe04af86b1970f2d733d28c6
- **短哈希**：f88180a260
- **日期**：2026-03-17 07:19:29 -0700
- **作者**：Russell Spitzer
- **提交说明**：Add AGENTS.md with project conventions for AI coding agents (#15529)
- **PR/Issue**：#15529

## 总体目的

在仓库根目录添加 `AGENTS.md` 文件，遵循开放标准为 AI 编码代理提供项目特定的上下文和规范。该文件的内容综合分析了项目历史上 58,000+ 条审查评论和 4,300+ 个合并 PR，涵盖了模块边界、高敏感区域、设计模式、编码风格、命名规范、序列化、错误处理、性能、测试以及 REST/OpenAPI 规范等方面的约定。

## 如何达成设计目的

- 创建 `AGENTS.md` 文件，按照 apache/airflow 的 AGENTS.md 模式组织内容
- 包含以下主要章节：Architecture（模块边界、高敏感区域）、Design Patterns、Coding Conventions（API 设计、命名、代码风格、代码放置、序列化、错误处理、性能、配置、测试、REST/OpenAPI）、Commands、PR & Commit Conventions、Boundaries
- 内容基于对项目历史 PR 审查评论的分析，提炼出实际的项目规范

## 修改详情

### `AGENTS.md` (+172 lines, 新文件)

**修改目的**：为 AI 编码代理提供项目规范文档。

**工作逻辑**：

**Architecture 部分**：
- 模块边界：API（最强稳定性保证）、Core（引擎无关）、Data、Spark、Flink、REST Catalog、云特定实现
- 高敏感区域：TableMetadata、SnapshotProducer/MergingSnapshotProducer、ManifestGroup/ManifestReader、序列化解析器、REST 规范、扫描规划

**Design Patterns 部分**：
- 精化模式（TableScan 方法返回新实例）
- CloseableIterable 优于 Stream
- null 优于 Optional
- Builder 模式
- 包私有优先
- Postel 法则（宽容输入，规范输出）
- 不可变元数据
- 边界验证

**Coding Conventions 部分**：
- API 设计：新公开方法需充分理由，永不破坏 API
- 命名：方法名描述具体行为，避免 get 前缀，kebab-case 属性名
- 代码风格：2 空格缩进，try-with-resources，方法引用优于 lambda
- 序列化：永不使用 Jackson 注解，自定义 XxxParser
- 错误处理：直接可操作的消息，不吞异常
- 性能：注意流式管道中的隐藏物化
- 测试：JUnit 5 + AssertJ，计算预期值而非硬编码

**Commands 部分**：
- 构建（跳过测试）：`./gradlew build -x test -x integrationTest`
- 单个测试类/方法、版本化模块测试、格式化、API 兼容性检查命令

**PR & Commit Conventions 部分**：
- PR 标题格式 `Module: Description`
- 每个 PR 一个关注点
- 提交信息描述 what 和 why

**Boundaries 部分**：
- 永不修改 .asf.yaml、LICENSE、NOTICE、versions.props
- 永不添加 Jackson 注解
- 永不破坏公开 API
- 永不添加 Hadoop 依赖
- 添加新依赖前先询问

## 总结

本提交创建了 AGENTS.md 文件，为 AI 编码代理（如 Cursor、Claude 等）提供 Iceberg 项目的详细规范和约定。该文件综合了项目历史 PR 审查经验，涵盖了架构、设计模式、编码规范、构建命令、PR 规范和安全边界，帮助 AI 代理在参与 Iceberg 项目时遵循项目惯例。
