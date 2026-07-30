# 提交 2952：Docs: encryption (#14621)

## 提交信息

- **序号**：2952 / 4088
- **哈希**：8626ef5137024c1a69daaff97a832af6b0ae37ea
- **短哈希**：8626ef513f
- **日期**：2025-12-04
- **作者**：ggershinsky
- **提交说明**：Docs: encryption (#14621)
- **PR/Issue**：#14621

## 总体目的

Iceberg 早就具备表级加密能力（`StandardEncryptionManager`、`KeyManagementClient`、Parquet 模块化加密与 Avro 的 AES GCM Stream 加密、AWS/GCP/Azure 的 KMS 客户端等），但文档站一直缺少一篇系统介绍加密的页面，用户只能从代码、配置项零散地拼凑使用方法。这导致两个问题：一是新用户难以发现"如何启用加密"——需要同时设置 catalog 属性 `encryption.kms-impl` 和表属性 `encryption.key-id`，以及哪些 catalog/数据格式支持加密；二是自定义 catalog 实现者不清楚加密对 catalog 的安全要求（如不能让 `encryption.key-id` 被篡改、metadata.json 的完整性保护等），容易写出不安全的 catalog。

本提交新增 `docs/docs/encryption.md` 一篇完整文档，覆盖加密概述、Spark 启用示例、catalog 安全要求、KMS 客户端接口、以及"内部工作原理"附录（从数据文件到 manifest、manifest list、KEK 的逐层加密机制）。同时在 `configuration.md` 与 `custom-catalog.md` 中补上指向新文档的引用与配置项说明，并把 `encryption.md` 注册到 `mkdocs.yml` 导航。文档背景是 Iceberg 加密功能已较成熟但长期缺文档，本提交补齐这一空白，对实际使用与安全合规都有指导价值。

## 如何达成设计目的

设计上是一篇"用户向 + 实现向"兼顾的文档：开篇讲清加密覆盖范围（data/delete/manifest/manifest list，metadata.json 不加密）与启用所需的两项配置；给出可直接复制的 Spark SQL 示例；明确列出 catalog 的三条安全要求；介绍 `KeyManagementClient` 接口与 AWS/GCP/Azure 内置实现；最后用附录详述从数据文件 KEK 到 manifest list 元数据的逐层加密与 KEK 轮换机制（引用 NIST SP 800-57）。配套在配置文档里补充 `encryption.key-id`/`encryption.data-key-length`/`encryption.kms-impl` 三项，并在自定义 catalog 文档里加一行指向 catalog 安全要求。

## 修改详情

### `docs/docs/encryption.md` (+153/-0 lines, 新文件)

**修改目的**：新增 Iceberg 表加密的完整文档。

**工作逻辑**：
文档结构：
- 概述：说明加密保护 data/delete/manifest/manifest list 的机密性与完整性，`metadata.json` 不含数据/统计故不加密；当前仅 Hive 与 REST catalog 支持，数据格式限 Parquet 与 Avro；启用需 catalog 属性 `encryption.kms-impl`（KMS 客户端类路径）+ 表属性 `encryption.key-id`（主键 ID）。
- Example：给出 `spark-sql` 启动配置（catalog 加 `encryption.kms-impl=org.apache.iceberg.aws.AwsKeyManagementClient`）+ `CREATE TABLE ... TBLPROPERTIES ('encryption.key-id'='...')`，并演示 `INSERT` 自动加密、`SELECT` 自动解密；给出用 `hexdump` 验证文件已加密的方法（Parquet 应以 `PARE` 开头，manifest/list 以 `AGS1` 开头）。
- Catalog security requirements：三条——(1) catalog 必须保证 `encryption.key-id` 在表生命周期内不被修改/删除；(2) catalog 实现不得从易被篡改的存储直接读 `metadata.json`，给出了三种可信方案（独立可信存储、防篡改存储、独立校验和）。
- Key Management Clients：列出 AWS/GCP/Azure 内置 KMS 客户端，并贴出 `KeyManagementClient` 接口的 `initialize`/`wrapKey`/`unwrapKey` 方法 Javadoc，说明如何自定义实现。
- Appendix: Internals Overview：详述逐层加密——每个 data/delete 文件由 worker 用安全随机数生成 DEK 与 AAD prefix，Parquet 走模块化加密、Avro 走 AES GCM Stream；manifest 文件存每个 data/delete 文件的 `key_metadata`（Avro 还存文件长度），manifest 自身由 driver 生成的密钥加密；manifest list 存每个 manifest 的 `key_metadata`，自身也加密；manifest list 的密钥元数据用 KEK（密钥加密密钥）加密，KEK_ID 记在 snapshot 的 `key-id` 字段，加密后的 KEK 记在 table metadata 的 `encryption-keys` 列表；KEK 经 KMS 用表主键包装，按 NIST SP 800-57 周期轮换，旧 KEK 保留以服务历史快照。这一附录把代码里的加密流程用文字串起来，对实现者理解设计动机极有价值。

### `docs/docs/configuration.md` (+10/-0 lines)

**修改目的**：在表属性与 catalog 属性文档中补充加密相关配置项。

**工作逻辑**：
- 在表属性表后新增 "Encryption properties" 小节，列出 `encryption.key-id`（默认未设置，表主键 ID）与 `encryption.data-key-length`（默认 16 字节，有效值 16/24/32），并链接到 `encryption.md`。
- 在 catalog 属性表中新增一行 `encryption.kms-impl`（默认 null，自定义 `KeyManagementClient` 实现类路径），同样链接到 `encryption.md`。这让用户在查阅配置时能直接发现加密选项。

### `docs/docs/custom-catalog.md` (+2/-0 lines)

**修改目的**：提醒自定义 catalog 实现者注意加密安全要求。

**工作逻辑**：
在文档开头的目录列表后加一行注释："To work with encrypted tables, custom catalogs must address a number of security requirements"，并链接到 `encryption.md#catalog-security-requirements`。这把安全要求显式暴露给 catalog 开发者，避免遗漏。

### `docs/mkdocs.yml` (+1/-0 lines)

**修改目的**：把加密文档加入站点导航。

**工作逻辑**：
在 `nav` 的 "Tables" 下、`configuration.md` 之后插入 `- encryption.md`，按字母序与现有 `evolution.md`/`maintenance.md` 排列，使加密页出现在文档站 Tables 分类下。

## 总结

本提交新增 Iceberg 加密功能的首篇完整文档，覆盖启用方法、Spark 示例、catalog 安全要求、KMS 客户端接口与逐层加密内部原理，并在配置与自定义 catalog 文档中补齐引用。它填补了加密能力长期缺文档的空白，既指导用户上手使用，也提醒 catalog 实现者满足安全前提，对推广与正确使用 Iceberg 加密有实际价值。
