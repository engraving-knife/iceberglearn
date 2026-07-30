# 提交 0360：Nessie: Infer default API version from URI (#9459)

## 提交信息

- **序号**：0360
- **哈希**：8018ab844bd58dfc5fce585c0db507f606aff4a0
- **短哈希**：8018ab844
- **日期**：2024-01-16 08:51:59 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Nessie: Infer default API version from URI (#9459)
- **PR/Issue**：#9459

## 总体目的

本提交改变了 Iceberg `NessieCatalog` 在初始化时确定 Nessie 客户端 API 版本（v1 或 v2）的默认策略。在改动之前，`NessieCatalog.initialize` 中默认硬编码 API 版本为 `"1"`（v1），即用户未显式配置 `client-api-version` 时一律走 Nessie REST API v1。这一默认策略在 Nessie 服务端逐步推广 v2 API、且 Iceberg 集成测试也越来越多地使用 v2 的背景下显得不灵活——用户在 catalog properties 里写了 `uri = http://host:port/v2`（URI 路径中已明确表达 v2）却仍需再额外写 `client-api-version = 2` 才能真正用上 v2，存在"URI 已含版本信息但被忽略"的不一致。

本提交把默认策略从"硬编码 v1"改为"从 URI 末尾推断版本"：若用户未显式配置 `client-api-version`，则解析 `uri` 字符串末尾的 `/v1`、`/v2` 等模式提取版本号；若 URI 也不含版本后缀，则抛 `IllegalArgumentException` 提示用户显式配置 `client-api-version`。这一改动让"URI 表达的版本"与"客户端实际使用的版本"自动对齐，减少配置冗余与潜在的不一致（例如 URI 写 v2 但 client-api-version 漏配为 1 导致版本不匹配错误）。同时显式配置 `client-api-version` 仍优先于 URI 推断，保留用户强制覆盖能力——这一优先级在新增的 `testClientApiVersionOverride` 测试中被明确验证（v1 URI 配 v2 client-api-version，实际走 v2 并因版本不匹配而报错）。

值得注意的是，本提交的"推断失败即报错"策略是 breaking change 的边界：原本不配 `client-api-version` 也不在 URI 末尾写版本的部署（如 `uri = http://host:19120/iceberg`）在旧代码中默认走 v1 能工作，新代码则会抛 `IllegalArgumentException` 要求显式配置。这是为了让版本信息"要么显式配置、要么从 URI 明确推断"，避免隐式默认 v1 掩盖版本不匹配问题。配套的两个测试 `testInvalidClientApiVersionViaURI`（验证 URI 无版本后缀时报错、URI 含 `/v3` 时报"Unsupported client-api-version: 3"）与 `testClientApiVersionOverride`（验证显式 `client-api-version` 优先于 URI 推断）覆盖了这两种边界。

## 如何达成设计目的

实现路径分三层：(1) **生产代码 `NessieCatalog.initialize`**：把原本 `options.getOrDefault(removePrefix.apply(NessieUtil.CLIENT_API_VERSION), "1")` 改为先 `options.get(...)` 取显式配置，若为 null 则调用新增的 `inferVersionFromURI(options.get(CatalogProperties.URI))` 从 URI 推断。`inferVersionFromURI` 用正则 `Pattern.compile("/v(\\d+)$")` 匹配 URI 末尾的 `/v1`/`/v2`/`/v3` 等，取捕获组作为版本号；若 URI 为 null 抛"URI is not specified"，若不匹配抛"URI doesn't end with the version: <uri>. Please configure `client-api-version` explicitly."。后续的 `switch (apiVersion)` 逻辑不变——版本号交给既有的 switch 分支处理，未知版本（如 `/v3`）会被 switch 的 default 分支抛"Unsupported client-api-version: 3. Can only be 1 or 2"。(2) **测试 `TestNessieCatalog`**：删除原本在 `loadCatalog` 中显式传 `client-api-version` 的逻辑（让 URI 推断生效），同时删除 `apiVersion` 字段与 `NessieApiVersion` import（不再需要按 apiVersion 选 version 字符串）。这让现有的 `TestNessieCatalog` 测试在 v1/v2 URI 下自动推断版本，与新默认策略一致。(3) **测试 `TestNessieIcebergClient`**：新增两个测试覆盖边界场景——`testInvalidClientApiVersionViaURI` 验证 URI 无版本后缀（`some/uri/`）报错、URI 含 `/v3` 报"Unsupported client-api-version: 3"；`testClientApiVersionOverride` 验证显式 `client-api-version` 优先于 URI 推断（v1 URI 配 v2 client-api-version，实际走 v2，loadTable 因 URI 与 client 版本不匹配而抛"API version mismatch"）。

## 修改详情

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieCatalog.java`

**修改目的**：把 Nessie 客户端 API 版本的默认策略从"硬编码 v1"改为"从 URI 末尾推断，显式配置优先"。

**工作逻辑**：(1) **import 新增**：`java.util.regex.Matcher`、`java.util.regex.Pattern`，用于 URI 版本正则匹配。(2) **API 版本解析逻辑改写**：原代码 `// default version is set to v1.` + `final String apiVersion = options.getOrDefault(removePrefix.apply(NessieUtil.CLIENT_API_VERSION), "1");` 改为 `// default version is inferred by uri.` + `String apiVersion = options.get(removePrefix.apply(NessieUtil.CLIENT_API_VERSION)); if (apiVersion == null) { apiVersion = inferVersionFromURI(options.get(CatalogProperties.URI)); }`——先尝试取显式 `client-api-version`（去掉 `nessie.` 前缀后的 key），若为 null 则委托 `inferVersionFromURI` 从 URI 推断。注意 `apiVersion` 从 `final` 改为非 final（因为可能在 if 分支被重新赋值）。后续 `switch (apiVersion)` 分支（case "1" → `nessieClientBuilder.build(NessieApiV1.class)`、case "2" → `nessieClientBuilder.build(NessieApiV2.class)`、default 抛 `IllegalArgumentException`）保持不变，复用既有的版本校验逻辑。(3) **新增 `inferVersionFromURI` 私有静态方法**：

```java
private static String inferVersionFromURI(String uri) {
  if (uri == null) {
    throw new IllegalArgumentException("URI is not specified in the catalog properties");
  }
  Pattern pattern = Pattern.compile("/v(\\d+)$");
  Matcher matcher = pattern.matcher(uri);
  if (matcher.find()) {
    return matcher.group(1);
  } else {
    throw new IllegalArgumentException(
        String.format(
            "URI doesn't end with the version: %s. "
                + "Please configure `client-api-version` in the catalog properties explicitly.",
            uri));
  }
}
```

正则 `/v(\\d+)$` 用 `$` 锚定 URI 末尾，匹配 `/v1`/`/v2`/`/v3` 等，捕获组 `\\d+` 取数字部分作为版本号字符串返回。URI 为 null 时抛"URI is not specified in the catalog properties"；URI 不匹配（如 `some/uri/` 或 `http://host:19120/iceberg`）时抛"URI doesn't end with the version: <uri>. Please configure `client-api-version` in the catalog properties explicitly."——错误消息明确告知用户两种解决路径（在 URI 末尾加 `/v1` 或 `/v2`，或在 catalog properties 中显式配 `client-api-version`）。该方法为 `private static`，纯函数无副作用，便于测试与复用。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieCatalog.java`

**修改目的**：让 `TestNessieCatalog` 的 catalog 加载逻辑跟随新默认策略——不再显式传 `client-api-version`，让 URI 推断生效。

**工作逻辑**：(1) **import 删除**：`org.projectnessie.client.ext.NessieApiVersion`（不再需要按 apiVersion 选 version 字符串）。(2) **字段删除**：`private NessieApiVersion apiVersion;` 字段移除。(3) **`setUp` 方法**：删除 `apiVersion = clientFactory.apiVersion();` 行——不再保存 apiVersion。(4) **`loadCatalog` 方法**：原 `ImmutableMap.builder().put(...).put("uri", uri).put(WAREHOUSE_LOCATION, ...).put("client-api-version", apiVersion == NessieApiVersion.V2 ? "2" : "1").build()` 改为删除最后的 `.put("client-api-version", ...)` 行——让 `NessieCatalog.initialize` 通过 URI 推断版本。测试框架 `NessieClientUri` 提供的 `uri` 已经在末尾包含 `/v1` 或 `/v2`（取决于测试运行的 API 版本参数），故 URI 推断能正确工作，与原显式配置效果等价但更简洁。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieIcebergClient.java`

**修改目的**：新增两个测试覆盖 URI 推断的边界场景——URI 无版本后缀报错、URI 含不支持版本报错、显式 `client-api-version` 优先于 URI 推断。

**工作逻辑**：新增 import `org.apache.iceberg.catalog.TableIdentifier`，新增两个 `@Test` 方法：

1. **`testInvalidClientApiVersionViaURI`**：用 try-with-resources 创建 `NessieCatalog`，setConf 后用 `ImmutableMap.builder().put("uri", "some/uri/")` 初始化——URI 末尾是 `/` 不含版本，断言抛 `IllegalArgumentException` 且消息为 `"URI doesn't end with the version: some/uri/. Please configure `client-api-version` in the catalog properties explicitly."`。接着用 `uri = "some/uri/v3"` 再次初始化——URI 含 `/v3`，`inferVersionFromURI` 返回 "3"，但 switch 的 default 分支抛 `"Unsupported client-api-version: 3. Can only be 1 or 2"`。两个断言用 AssertJ 的 `assertThatIllegalArgumentException().isThrownBy(...).withMessage(...)` 链式断言。

2. **`testClientApiVersionOverride`**：验证显式 `client-api-version` 优先于 URI 推断。先取当前 `apiVersion`（测试类的字段，由测试参数注入）的反值（v1 用 v2、v2 用 v1）作为 `version`。用 `ImmutableMap.builder().put(CatalogProperties.URI, uri).put(WAREHOUSE_LOCATION, ...).put("client-api-version", version).build()` 初始化 catalog——显式配置 `client-api-version` 与 URI 末尾版本相反。断言 `newCatalog.loadTable(TableIdentifier.of("foo", "t1"))` 抛 `RuntimeException` 且消息以 `"API version mismatch, check URI prefix"` 开头——因为 client 用显式 version（如 v2）连接 v1 URI 的 Nessie 服务端，服务端返回版本不匹配错误，证明显式配置确实覆盖了 URI 推断。注意此处不抛 `IllegalArgumentException`（initialize 阶段成功，因为显式 version 是合法的 1 或 2），而是在 `loadTable` 时才因服务端版本不匹配抛 `RuntimeException`，断言用 `assertThatRuntimeException().isThrownBy(...).withMessageStartingWith("API version mismatch, check URI prefix")`。

## 小结

本次提交把 `NessieCatalog` 客户端 API 版本的默认确定策略从"硬编码 v1"改为"从 URI 末尾推断（`/v1`/`/v2` 等正则匹配），显式 `client-api-version` 优先"。新增 `inferVersionFromURI` 私有静态方法用正则 `/v(\\d+)$` 锚定 URI 末尾提取版本号，URI 无版本后缀时抛 `IllegalArgumentException` 提示用户显式配置——这是为了让版本信息"要么显式配置、要么从 URI 明确推断"，避免隐式 v1 默认掩盖版本不匹配。显式配置优先级通过新增测试 `testClientApiVersionOverride` 验证（v1 URI 配 v2 client-api-version 实际走 v2 并因服务端版本不匹配报错）。`TestNessieCatalog` 同步删除显式 `client-api-version` 配置让 URI 推断生效。两个新测试 `testInvalidClientApiVersionViaURI` 与 `testClientApiVersionOverride` 覆盖了 URI 推断的边界场景（无版本后缀、不支持版本、显式覆盖）。本提交让 Nessie 的版本配置更自洽——URI 表达的版本与客户端实际使用的版本自动对齐，减少配置冗余与潜在不一致。
