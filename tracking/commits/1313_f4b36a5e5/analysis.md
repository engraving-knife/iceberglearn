# 提交 1313：OpenAPI: Remove credentials from LoadViewResult (#11433)

## 提交信息

- **序号**：1313 / 4088
- **哈希**：f4b36a5e553cc8a14208890da2a3adc91ceefbda
- **短哈希**：f4b36a5e5
- **日期**：2024-10-30（Wed Oct 30 18:52:34 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：OpenAPI: Remove credentials from LoadViewResult (#11433)
- **PR/Issue**：#11433

## 总体目的

这是与提交 1312（Core: Remove credentials from LoadViewResponse）配套的协议规范改动。提交 1312 删除了 Java 端 `LoadViewResponse` 的 `credentials()` 字段及对应的序列化逻辑，本提交则同步更新 REST Catalog 的 OpenAPI 规范文档，把 `LoadViewResult` 模型中的 `storage-credentials` 字段移除，使规范与实际实现保持一致。

Iceberg REST 规范中同时维护 `.yaml`（人类和工具直接消费）与 `.py`（基于 pydantic 的等价模型，用于生成校验和示例）两份 OpenAPI 描述，二者必须同步修改。本提交即对这两份文件做对应的字段删除。

## 如何达成设计目的

1. 在 `rest-catalog-open-api.yaml` 的 `LoadViewResult` schema 中：
   - 删除 `storage-credentials` 属性定义（数组 + `$ref: '#/components/schemas/StorageCredential'`）；
   - 删除描述里关于 "Storage Credentials" 的整段说明文字（提醒客户端优先从 `storage-credentials` 取凭据，再回退到 `config`）。
2. 在 `rest-catalog-open-api.py` 的 `LoadViewResult` pydantic 模型中：
   - 删除 `storage_credentials` 字段；
   - 同步删除 docstring 中的 "Storage Credentials" 段落。

修改后 `LoadViewResult` 只保留 `metadata-location`、`metadata`、`config` 三个字段，与 Java 端 `LoadViewResponse` 完全对齐。`StorageCredential` schema 本身并未删除（仍被 `LoadCredentialsResult` 等其他模型使用），只是不再出现在 `LoadViewResult` 中。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：从 OpenAPI 规范中移除 `LoadViewResult.storage-credentials` 字段。

**工作逻辑**：

- 在 `LoadViewResult` 的 `description` 中删除以下 markdown 段落：
  ```markdown
  ## Storage Credentials

  Credentials for ADLS / GCS / S3 / ... are provided through the `storage-credentials` field.
  Clients must first check whether the respective credentials exist in the `storage-credentials` field before checking the `config` for credentials.
  ```
  删除后描述里只剩 `## General Configurations` 段落（说明 `token` 等通用配置）。
- 在 `properties` 中删除：
  ```yaml
  storage-credentials:
    type: array
    items:
      $ref: '#/components/schemas/StorageCredential'
  ```

  注意 `required` 数组中本来就没有 `storage-credentials`（它是可选的），所以 `required` 段无需改动。

### `open-api/rest-catalog-open-api.py`

**修改目的**：同步 pydantic 模型，删除 `storage_credentials` 字段。

**工作逻辑**：

- 类 `LoadViewResult(BaseModel)` 的 docstring 中删除相同的 "Storage Credentials" 段落。
- 删除字段：
  ```python
  storage_credentials: Optional[List[StorageCredential]] = Field(
      None, alias='storage-credentials'
  )
  ```
  模型仅保留 `metadata_location`（alias `metadata-location`）、`metadata`、`config` 三个字段。

由于 `StorageCredential` 在文件其他地方仍被引用（如独立的 `LoadCredentialsResult`），其 import 与 schema 定义保持不变。

## 小结

- **成效**：OpenAPI 规范的 `LoadViewResult` 模型与 Java 实现（`LoadViewResponse`）重新对齐，从协议层正式声明"加载视图不再下发存储凭据"，凭据需通过独立端点按需获取。
- **影响范围**：2 个文件、共 17 行删除，纯文档/规范修改，无代码逻辑。
- **回迁到 1.4.x 的注意事项**：
  - 此改动属于 REST 协议规范层面的不兼容收窄。1.4.x 若回迁，须与 1312 配套使用——只在 Java 端删字段而不更新规范，或反过来，都会造成规范与实现不一致，影响基于规范生成的客户端代码。
  - 若 1.4.x 仍需保留旧版协议（响应含 `storage-credentials`），则不应回迁 1312/1313 这一组提交；若决定对齐 main 协议，则两提交需同时回迁，并核验下游引擎（Spark/Trino/Flink 等）的 REST 客户端是否已切换到独立 credentials 端点。
  - 由于纯文档改动，回迁不引入编译或运行时风险，仅需注意规范版本号与兼容性声明（如有）是否需要相应调整。
