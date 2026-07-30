# 提交 0762：Docs: Update vendor information for Cloudera (#10278)

## 提交信息

- **序号**：0762 / 4088
- **哈希**：ea916c1700ef37377d3e8bba1197b3fe32d1a248
- **短哈希**：ea916c170
- **日期**：2024-05-14 00:40:09 -0700
- **作者**：Andrew Sherman（Cloudera）
- **提交说明**：Docs: Update vendor information for Cloudera (#10278)
- **PR/Issue**：#10278

## 总体目的

本提交更新 Iceberg 官方文档中 Cloudera 厂商信息段落。原内容以技术组件清单形式列举 Cloudera Data Platform（CDP）集成 Iceberg 的各组件（Hive、Impala、Spark、SDX、HMS、Data Visualization），更新后改为以"开放数据湖仓（open data lakehouse）"为定位的叙述性介绍，更突出 Cloudera 在数据摄取、处理、分析、AI 全链路的端到端能力，并补充了 NiFi、Flink、Kafka 等摄取工具及 Machine Learning 产品的链接。这是一次纯文档营销/定位层面的更新，使 Cloudera 在 Iceberg 生态中的厂商描述与 Cloudera 自身最新的产品定位（open data lakehouse）保持一致。

## 如何达成设计目的

### 更新策略

Iceberg 的 `site/docs/vendors.md` 维护了一份支持 Iceberg 表格式的厂商列表，每个厂商有独立段落。Cloudera 段落此前是一段较干瘪的功能清单（逐条列举哪些组件能查询/访问 Iceberg 表），更新后改为：

1. **定位转变**：从"CDP 集成 Iceberg 到以下组件"改为"Cloudera 的开放数据湖仓使客户能以 Apache Iceberg 等开放表格式存储和管理数据，用于大规模多功能分析和 AI"。
2. **强调开放性与多引擎**：明确指出 Cloudera 的 Iceberg 支持易于使用、易于集成到任何数据生态、易于运行多个引擎（Cloudera 和非 Cloudera 引擎均可），不论数据驻留在何处。
3. **强调统一治理**：补充"为所有数据提供统一安全、治理、元数据管理和细粒度访问控制"的描述。
4. **端到端数据流**：新增从摄取（NiFi、Flink、Kafka 批流数据）→ 处理（Spark）→ 分析/AI（Data Visualization、Data Warehouse、Machine Learning）的完整链路叙述。
5. **链接更新**：原内容仅有一个 CDP 文档链接，更新后增加了 Cloudera 开放数据湖仓产品页、Data Visualization、Data Warehouse、Machine Learning 等多个产品链接，使读者可直达 Cloudera 各产品页面。

### 设计意图

这种从"功能清单"到"解决方案叙述"的转变，反映了 Iceberg 作为开放表格式在厂商生态中的定位变化——厂商不再仅仅"支持查询 Iceberg 表"，而是将 Iceberg 作为其数据湖仓战略的核心开放格式。文档更新使 Iceberg 社区页面更准确地反映厂商的当前产品策略。

## 修改详情

### `site/docs/vendors.md`

**修改目的**：重写 Cloudera 厂商段落，更新产品定位与链接。

**工作逻辑**：
- 共 16 行新增、9 行删除（净增 7 行）。
- 删除原 7 条组件清单项（Apache Hive/Impala/Spark 查询、CDW、CDE、SDX、HMS、Data Visualization）及一条 CDP 文档链接。
- 新增 3 段叙述性文字：
  - 第 1 段：介绍 Cloudera 开放数据湖仓以 Iceberg 等开放表格式存储管理数据，支持大规模多功能分析和 AI，强调易用、易集成、多引擎、统一治理。
  - 第 2 段：描述端到端数据流（NiFi/Flink/Kafka 摄取 → Spark 处理 → Data Visualization/Data Warehouse/Machine Learning 分析），支持私有云和公有云。
  - 链接：Cloudera 开放数据湖仓产品页、Cloudera 主页、Data Visualization、Data Warehouse、Machine Learning 产品页。
- 其他厂商段落（ClickHouse、Dremio 等）未改动。

## 小结

- **成效**：Cloudera 在 Iceberg 官方文档的厂商描述从组件功能清单升级为面向数据湖仓的端到端解决方案叙述，更贴合 Cloudera 当前产品定位，并提供了更丰富的产品链接供用户进一步了解。文档更新无任何代码影响。
- **影响范围**：仅 `site/docs/vendors.md` 一个文档文件，不影响任何源码或运行时行为。
- **回迁注意事项**：
  1. 纯文档改动，cherry-pick 到 1.4.x 分支无技术风险，不会产生代码冲突。
  2. 若 1.4.x 分支的 `vendors.md` 已有其他厂商段落更新，cherry-pick 时仅在 Cloudera 段落处可能产生上下文冲突，手动解决即可。
  3. 文档内容为厂商提供的营销/定位文案，回迁价值在于保持文档与 main 分支一致；若 1.4.x 分支不维护厂商文档可酌情跳过。
