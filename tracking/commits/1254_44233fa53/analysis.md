# 提交 1254：OpenAPI: Standardize credentials in loadTable/loadView responses (#10722)

## 提交信息

- **序号**：1254 / 4088
- **哈希**：44233fa5307cad4a10dfb33b67bae31da48c6798
- **短哈希**：44233fa53
- **日期**：2024-10-18（Fri Oct 18 19:24:13 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：OpenAPI: Standardize credentials in loadTable/loadView responses (#10722)
- **PR/Issue**：#10722

## 总体目的

Iceberg 的 REST Catalog 规范定义了 `LoadTableResult`（加载表响应）与 `LoadViewResult`（加载视图响应）。此前存储凭证（如 S3 / GCS / ADLS 的临时访问密钥、SAS token、OAuth token 等）只能通过响应中的 `config` 字段以扁平键值对的方式下发给客户端。这种做法存在局限：

1. `config` 字段既承载表配置（如 `write.data.path`、`commit.retry.num-retries`），又承载凭证，职责混杂；
2. 凭证没有「作用范围」概念，客户端无法判断某组凭证适用于哪个存储前缀，难以在多存储后端场景下选择正确凭证；
3. 不同存储系统的凭证前缀各异（`s3.`、`gcs.`、`adls.`），缺乏统一结构。

本提交在 REST Catalog OpenAPI 规范中新增标准化的 `StorageCredential` schema，并在 `LoadTableResult` 与 `LoadViewResult` 中新增可选字段 `storage-credentials`（数组）。每个 `StorageCredential` 由 `prefix`（该凭证适用的存储位置前缀）与 `config`（凭证键值对）组成。规范明确要求客户端优先从 `storage-credentials` 字段获取凭证，仅在该字段缺失时才回退检查旧的 `config` 字段，实现向后兼容的迁移。文档说明客户端在多个前缀匹配时应选最长（最具体）的前缀，以处理嵌套存储路径场景。

本提交（#10722）只改 OpenAPI 规范文件，配套的 Java 实现由同一作者几乎同时提交的 #11173（本批序号 1255）完成。两者共同构成「规范先行、实现跟上」的完整凭证标准化方案。

## 如何达成设计目的

通过在 OpenAPI 规范中新增一个可复用的 `StorageCredential` 组件 schema，并在两个加载响应中引用它，达成凭证的标准化结构化下发。关键设计点：

- **prefix 机制**：用前缀绑定凭证作用范围，客户端按「最长前缀匹配」选择，天然支持多后端与嵌套路径。
- **可选字段 + 回退说明**：`storage-credentials` 为可选字段，并在描述中明确「先查 storage-credentials、再查 config」的回退顺序，保证旧客户端与新服务端、新客户端与旧服务端都能互通。
- **数组而非单值**：支持一次返回多组不同前缀的凭证，覆盖一个表数据横跨多个存储桶的场景。

同时更新了 Python（`rest-catalog-open-api.py`，datamodel-code-generator 生成的 Pydantic 模型）与 YAML（`rest-catalog-open-api.yaml`，原始 OpenAPI 规范）两份契约文件，保持两者一致。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 OpenAPI 规范中定义 `StorageCredential` 组件并接入两个加载响应。

**工作逻辑**：

1. 在 `components.schemas` 中新增 `StorageCredential` 对象，要求字段 `prefix`（string）与 `config`（`additionalProperties: string` 的 map），并在 `prefix` 描述中说明「客户端应选最长前缀以匹配最具体凭证」。

2. 在 `LoadTableResult` 的描述里追加「Storage Credentials」段落，说明 ADLS/GCS/S3 等凭证通过 `storage-credentials` 字段下发，客户端须先检查该字段再回退 `config`；并在 `properties` 中新增 `storage-credentials`（`type: array`，items 引用 `StorageCredential`）。该字段未列入 `required`，故为可选。

3. 对 `LoadViewResult` 做对称改动：同样的描述段落与 `storage-credentials` 数组属性。

### `open-api/rest-catalog-open-api.py`

**修改目的**：同步更新由 OpenAPI 规范生成的 Pydantic 模型（Python 侧契约）。

**工作逻辑**：

1. 新增 `StorageCredential(BaseModel)` 类，含 `prefix: str`（带描述说明最长前缀匹配）与 `config: Dict[str, str]`。

2. 在 `LoadTableResult` 中：描述字符串内追加「Storage Credentials」段；新增字段 `storage_credentials: Optional[List[StorageCredential]]`，通过 `Field(None, alias='storage-credentials')` 映射到规范中的连字符字段名，保持 JSON 序列化与规范一致。

3. 在 `LoadViewResult` 中做对称改动：描述段 + `storage_credentials` 可选字段（同样带 alias）。

两份文件的改动完全对称，确保 YAML 原始规范与 Python 生成模型保持一致。

## 小结

- **成效**：REST Catalog 规范新增结构化的 `storage-credentials` 字段，凭证以「prefix + config」结构化数组形式下发，支持按最长前缀匹配选择、向后兼容回退 `config`，为多存储后端与细粒度凭证分发提供标准方案；YAML 与 Python 契约同步更新。
- **影响范围**：仅 `open-api/` 下 2 个规范文件，共约 56 行新增，无任何运行时代码改动。属于 REST 协议契约层的演进，新增字段为可选，对现有客户端无破坏性影响。
- **回迁到 1.4.x 的注意事项**：这是 REST Catalog 规范的向前演进，新增可选字段，向后兼容。1.4.x 若已包含 REST Catalog 模块，**可考虑回迁**以让 1.4.x 的 REST 服务端也能下发标准化凭证；但回迁需同时带上配套实现 #11173（Java 端的 `LoadTableResponse`/`LoadViewResponse` 序列化与 `Credential` 模型），否则规范有字段而实现不输出会造成规范与实现不一致。若 1.4.x 的 REST 客户端只消费旧 `config` 字段，则单回迁规范也安全（客户端忽略未知字段）。建议规范与实现成对回迁。
