# 提交 3796：Docs: Publish Iceberg security model (#16538)

## 提交信息

- **序号**：3796 / 4088
- **哈希**：60d3fc01b95e51952d07bfd6228f5bc8c855de0b
- **短哈希**：60d3fc01b
- **日期**：2026-05-28 14:03:49 -0400
- **作者**：Sung Yun <107272191+sungwy@users.noreply.github.com>
- **提交说明**：Docs: Publish Iceberg security model (#16538)
- **PR/Issue**：#16538

## 总体目的

本提交旨在为 Apache Iceberg 项目正式发布一份完整的安全模型文档，便于维护者和自动化安全工具在评估潜在漏洞时拥有一个权威、清晰的依据。长期以来，Iceberg 作为表格式库通常被嵌入到更大的系统（catalog、查询引擎、存储服务等）中运行，许多看似"安全相关"的缺陷实际上并不构成 Iceberg 自身的安全漏洞。这一部署特征使得维护者在面对大量自动化扫描结果时难以快速区分真正的安全问题和普通的健壮性/正确性问题。

为解决这一痛点，本提交新增了一份面向人类和 AI Agent 的 `SECURITY-THREAT-MODEL.md` 详细威胁模型文档，明确了 Iceberg 的信任边界、角色划分、在范围内与通常不在范围内的漏洞类别，以及针对扫描器的校准规则。同时，在面向公众的 `site/docs/security.md` 中补充了一段简洁的安全模型摘要，让外部用户在报告安全问题时能够先对照判断自己的问题是否属于 Iceberg 的安全边界。

通过这一文档化工作，社区希望降低误报率，加快安全响应节奏，并为自动化安全工具（例如 CodeQL 扫描器）提供校准参考。

## 如何达成设计目的

提交者采用了"详细 + 简要"双文档策略：在仓库根目录新增一份 260 行的完整威胁模型 `SECURITY-THREAT-MODEL.md`，作为权威且面向 Agent/扫描器使用的详细说明；同时在面向用户的网站安全文档 `site/docs/security.md` 中加入一段精炼的安全模型摘要，引导用户到详细文档。此外，在 `AGENTS.md` 中加入了到威胁模型的引用，方便 AI Agent 在评估安全问题时自动查阅。

## 修改详情

### `AGENTS.md` (+4/-0 lines)

**修改目的**：让 AI Agent 在评估潜在漏洞时知道存在权威的安全威胁模型文档，应作为评估依据。

**工作逻辑**：在 AGENTS.md 的 Security Model 小节中新增引用链接，指向 `SECURITY-THREAT-MODEL.md`，说明该文档是关于 Iceberg 安全边界、信任假设和非边界的权威详细描述。

### `SECURITY-THREAT-MODEL.md` (+260/-0 lines)

**修改目的**：新增权威的详细安全威胁模型文档，作为维护者和自动化安全 triage 的依据。

**工作逻辑**：文档共分多个章节：
- **Purpose & Scope**：说明文档目的是厘清什么算 Iceberg 安全漏洞，什么不算；范围限于 Iceberg 项目本身的表格式实现、客户端库、引擎集成和仓库内的 catalog 组件。
- **Security Goals**：明确 Iceberg 应避免向未授权主体暴露密钥/委托凭证，避免在 Iceberg 组件中创建新的未授权能力，避免违反 Iceberg 自己拥有的信任边界；同时不打算成为用户级授权或存储级授权的强制点。
- **Roles**：定义 Operator、Catalog 控制面、REST catalog server、REST catalog client、引擎/应用、表写入者/维护者等角色及其信任假设。
- **Trust Boundaries**：定义 5 个边界，包括 operator 可信配置、catalog 提供的元数据、REST catalog server 提供的配置和委托存储访问、存储级授权、引擎级用户授权，明确各边界的归属。
- **In-Scope Vulnerabilities**：仅"密钥/凭证向新受众披露"和"Iceberg 自有信任边界违反"两类通常视为安全漏洞。
- **Out of Scope by Default**：正确性 bug、解析器健壮性、恶意 catalog/metastore 场景、等效危害报告通常不算安全漏洞。
- **Scanner Calibration Rules**：扫描器只有在显示密钥披露、新未授权能力、Iceberg 自有边界违反时才提高置信度；对于依赖畸形输入、恶意外部服务或已具备等效权限主体的报告应默认降级。

### `site/docs/security.md` (+15/-0 lines)

**修改目的**：在面向公众的安全页面添加简洁的安全模型摘要，让报告者能在提交报告前先判断自己的问题是否属于 Iceberg 安全边界内。

**工作逻辑**：在报告安全漏洞的邮箱指引之后新增 "Security Model" 小节，简述 Iceberg 是表格式与库的集成层，主要信任与授权边界由周围的 catalog、引擎、服务、operator 配置与存储级授权共同执行；说明 Iceberg 安全问题通常包含密钥/凭证向新受众披露，以及 Iceberg 自身创建新的未授权能力等情况；并指出健壮性问题、需要恶意 catalog 等场景通常不算安全漏洞。最后提供到详细威胁模型文档的链接。

## 总结

本提交是文档化的工作，没有改动代码逻辑，但对 Iceberg 项目的安全治理具有重要意义。通过发布权威的威胁模型和面向用户的简要说明，明确了 Iceberg 安全边界与责任划分，可帮助维护者更高效地 triage 安全报告，并指导自动化扫描工具降低误报。这种"人类 + Agent"双消费场景的设计也呼应了当前 AI 辅助安全审计的发展趋势。
