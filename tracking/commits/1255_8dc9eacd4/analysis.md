# 提交 1255：Core: Add credentials to loadTable / loadView responses (#11173)

## 提交信息

- **序号**：1255 / 4088
- **哈希**：8dc9eacd4ea0227683710cdeb5ff6fcd4acd93fb
- **短哈希**：8dc9eacd4
- **日期**：2024-10-18（Fri Oct 18 19:24:36 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Add credentials to loadTable / loadView responses (#11173)
- **PR/Issue**：#11173

## 总体目的

本提交是 #10722（本批序号 1254，OpenAPI 规范侧）的 Java 实现配套。OpenAPI 规范已在 `LoadTableResult` / `LoadViewResult` 中新增结构化的 `storage-credentials` 字段，每个凭证由 `prefix`（适用的存储位置前缀）与 `config`（凭证键值对）组成。本提交在 Iceberg Core 模块中落地该规范：

1. 新建 `Credential` 不可变模型与 `CredentialParser`（JSON 序列化/反序列化器）；
2. 在 `LoadTableResponse`（普通类）与 `LoadViewResponse`（immutables 接口）中新增 `credentials()` 访问器与 Builder 写入方法；
3. 在对应的 `LoadTableResponseParser` / `LoadViewResponseParser` 中实现 `storage-credentials` 数组的 JSON 读写；
4. 补充覆盖 S3 / GCS / ADLS 三类凭证的解析器单元测试，以及含凭证的加载响应往返序列化测试。

这样 REST Catalog 服务端在响应 loadTable / loadView 时即可输出标准化凭证，客户端也能解析消费，与规范保持一致。

## 如何达成设计目的

整体遵循 Iceberg 既有的 REST 响应模型约定：

- **模型层**：用 `@Value.Immutable` 的 `Credential` 接口表达「prefix + config」结构，并通过 `@Value.Check` 在构造时校验 prefix 与 config 均非空，保证凭证对象自洽。`LoadTableResponse` 是普通可变类（带 Jackson 反序列化无参构造与私有全参构造），故直接加字段、加 Builder 方法；`LoadViewResponse` 是 immutables 接口，故用 `@Value.Default` 提供空列表默认值。
- **序列化层**：复用 `JsonUtil` 工具，在两个 Parser 中以字段名 `storage-credentials` 读写数组；数组为空时不写出该字段，保持 JSON 紧凑且与旧行为兼容。
- **向后兼容**：解析端仅在 `json.hasNonNull(STORAGE_CREDENTIALS)` 时才读取，并校验其必须为数组；旧响应（无该字段）仍可正常解析。`credentials()` 在字段为 null 时返回 `ImmutableList.of()`，调用方无需判空。
- **测试覆盖**：为 `CredentialParser` 写 null/空/缺字段/非法字段等边界用例及 S3、GCS、ADLS 三类凭证的往返；为两个响应 Parser 各加一个含三组凭证（含同类型多前缀 `gs` 与 `gs://custom-uri`，验证最长前缀场景）的往返测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/credentials/Credential.java`（新增）

**修改目的**：定义标准化凭证的不可变数据模型。

**工作逻辑**：`@Value.Immutable` 接口，含 `String prefix()` 与 `Map<String, String> config()` 两个方法。`@Value.Check` 标注的 `validate()` 校验 prefix 非空字符串、config 非空 map，否则抛 `IllegalArgumentException`。Immutables 会生成 `ImmutableCredential` 实现与 builder。

### `core/src/main/java/org/apache/iceberg/rest/credentials/CredentialParser.java`（新增）

**修改目的**：提供 `Credential` 与 JSON 互转的工具类。

**工作逻辑**：定义常量 `PREFIX="prefix"`、`CONFIG="config"`。提供三个 `toJson` 重载（返回字符串 / 带缩进 / 写入 `JsonGenerator`）：写对象时先写 `prefix` 字符串字段，再用 `JsonUtil.writeStringMap` 写 `config` map。提供两个 `fromJson` 重载（从字符串 / 从 `JsonNode`）：读取 prefix 与 config map，构建 `ImmutableCredential`。校验入参非 null。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadTableResponse.java`

**修改目的**：为加载表响应增加 `credentials` 字段。

**工作逻辑**：新增字段 `private List<Credential> credentials`；私有全参构造增加该参数并赋值；新增 `credentials()` 访问器（null 时返回 `ImmutableList.of()`）；`Builder` 增加 `credentials` 列表成员、`addCredential(Credential)` 与 `addAllCredentials(List<Credential>)` 方法，并在 `build()` 时把 credentials 传入构造。`toString` 未显示 credentials（保持原输出精简）。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadTableResponseParser.java`

**修改目的**：实现 `storage-credentials` 数组的 JSON 读写。

**工作逻辑**：新增常量 `STORAGE_CREDENTIALS="storage-credentials"`。写（`toJson`）：当 `response.credentials()` 非空时，用 `gen.writeArrayFieldStart(STORAGE_CREDENTIALS)` 起数组，逐个调用 `CredentialParser.toJson(credential, gen)`，最后 `writeEndArray`。读（`fromJson`）：当 `json.hasNonNull(STORAGE_CREDENTIALS)` 时取节点并校验 `isArray()`，遍历数组元素用 `CredentialParser.fromJson` 解析后 `addCredential` 进 builder。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadViewResponse.java`

**修改目的**：为加载视图响应增加 `credentials` 字段。

**工作逻辑**：`LoadViewResponse` 是 immutables 接口，故新增 `@Value.Default` 方法 `default List<Credential> credentials()` 返回 `ImmutableList.of()`。注意此处 builder 方法名为 `addCredentials`（复数，与 immutables 集合生成约定一致，区别于 `LoadTableResponse.Builder` 的 `addCredential`），测试代码也据此调用。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadViewResponseParser.java`

**修改目的**：实现 `LoadViewResponse` 的 `storage-credentials` 读写。

**工作逻辑**：与 `LoadTableResponseParser` 完全对称——常量 `STORAGE_CREDENTIALS`、写时数组非空才输出、读时 `hasNonNull` + `isArray` 校验后用 `CredentialParser.fromJson` 解析并 `builder.addCredentials(...)`。

### `core/src/test/java/org/apache/iceberg/rest/credentials/TestCredentialParser.java`（新增）

**修改目的**：覆盖 `CredentialParser` 的各类场景。

**工作逻辑**：`nullAndEmptyCheck` 验证 null 入参抛对应异常；`invalidOrMissingFields` 验证缺 prefix / 缺 config / 空 prefix / 空 config 均抛 `IllegalArgumentException`；`s3Credential`、`gcsCredential`、`adlsCredential` 三个用例分别构造对应存储系统的凭证，断言 `toJson(..., true)` 输出与预期 pretty JSON 完全一致，并验证「序列化→反序列化→再序列化」往返结果不变。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadTableResponseParser.java`

**修改目的**：验证含多组凭证的 `LoadTableResponse` 往返序列化。

**工作逻辑**：新增 `roundTripSerdeWithCredentials`：构造带三组凭证（s3、gs://custom-uri、gs）的响应，给出完整预期 JSON（含 `storage-credentials` 数组，每组含 prefix 与 config），断言 `toJson` 输出一致，并验证往返序列化结果不变。注意 schema 无 equals/hashCode，故用「序列化比较」而非对象相等。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadViewResponseParser.java`

**修改目的**：验证含多组凭证的 `LoadViewResponse` 往返序列化。

**工作逻辑**：新增 `roundTripSerdeWithCredentials`：构造 `ViewMetadata` 与三组凭证（同 LoadTable 用例），给出预期 JSON（注意 view 响应无 `config` 字段，仅 `metadata-location`/`metadata`/`storage-credentials`），断言 `toJson` 与往返序列化一致。

## 小结

- **成效**：Core 模块完整落地 REST Catalog 的标准化凭证机制：新增 `Credential` 不可变模型与 `CredentialParser`；`LoadTableResponse` / `LoadViewResponse` 及其 Parser 支持 `storage-credentials` 数组的读写；测试覆盖 S3/GCS/ADLS 三类凭证与边界异常、往返序列化。REST 服务端现可输出结构化凭证，客户端可解析消费，与 #10722 规范对齐。
- **影响范围**：core 模块 4 个主代码文件（2 新增 + 2 改动响应类 + 2 改动 Parser 实为 6 个）与 3 个测试文件（1 新增 + 2 改动），共约 555 行新增。属 REST 协议实现的向前演进，新增字段为可选、序列化时空数组不输出、解析时旧响应无该字段仍可读，向后兼容。
- **回迁到 1.4.x 的注意事项**：应与 #10722（OpenAPI 规范）成对回迁。回迁价值在于让 1.4.x 的 REST Catalog 也具备下发标准化凭证的能力。回迁时需注意：1.4.x 的 `LoadTableResponse`/`LoadViewResponse`/Parser 可能与 main 有差异，需逐文件比对合并而非整体替换；immutables 生成代码的 builder 方法名差异（`addCredential` vs `addCredentials`）需保留各自版本原有命名以免破坏既有调用。若 1.4.x 的 REST 客户端仅消费旧 `config` 凭证且无标准化凭证诉求，可不回迁，向后兼容性保证无风险。
