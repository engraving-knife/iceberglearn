# 提交 0895：REST: disallow overriding "credential" in table sessions (#10345)

## 提交信息

- **序号**：0895 / 4088
- **哈希**：d1f9f28f4fbf32e4adff324fa5864d73bd522880
- **短哈希**：d1f9f28f4
- **日期**：2024-07-04（Thu Jul 4 08:14:58 2024 +0200）
- **作者**：Alexandre Dutra <adutra@users.noreply.github.com>
- **提交说明**：REST: disallow overriding "credential" in table sessions (#10345)
- **PR/Issue**：#10345（关联 #10256）

## 总体目的

Iceberg REST Catalog 在加载表时，服务端可以通过 `LoadTableResponse` 的 `config` 返回一些表级别的配置，客户端会把这些配置与 catalog 级别的配置合并，用于构建该表的会话（`AuthSession`）。在认证场景下，`tableSession(...)` 方法会把表的完整 `tableConf` 同时当作 "credentials" 和 "properties" 传给 `newSession(tableConf, tableConf, parent)`，由 `newSession` 根据 credentials 中是否含 `token` / `credential` / `urn:ietf:params:oauth:token-type:*` 决定如何获取该表的访问 token。

这带来一个安全隐患（详见 #10256）：REST 服务端可以在 `LoadTableResponse` 中返回一个 `credential` 属性（形如 `client-id:client-secret`），客户端会用它通过 OAuth2 client credentials 流程换取一个新的 access token。这意味着服务端有能力让客户端以服务端指定的身份（而非客户端原本身份）发起后续对该表的请求，从而绕过客户端本应保持的认证身份边界。这在多租户或不可信 REST 服务的场景下是危险的。

本提交的目的是引入一个"允许覆盖"白名单（allow-list），限制只有 `token` 属性以及用于 token 交换的 `urn:ietf:params:oauth:token-type:*` 属性可以从表级配置传入 table session，而 `credential` 属性被显式过滤掉，从而堵住这一认证身份被服务端覆盖的入口。

## 如何达成设计目的

设计上不改变 `newSession` 的逻辑（它仍支持 token、credential、token-exchange 三种方式），而是在 `tableSession` 调用 `newSession` 之前，对传入的 credentials 做一次白名单过滤：从 `tableConf` 中只挑出白名单内的属性放入新的 `credentials` map，再把过滤后的 `credentials`（而非完整 `tableConf`）传给 `newSession` 的第一个参数，`tableConf` 仍作为第二个参数（properties，用于读取 `expires-at` 等非认证元信息）。

白名单 `TABLE_SESSION_ALLOW_LIST` 由 `OAuth2Properties.TOKEN` 与 `TOKEN_PREFERENCE_ORDER`（即 `ID_TOKEN_TYPE`、`ACCESS_TOKEN_TYPE`、`JWT_TOKEN_TYPE`、`SAML2_TOKEN_TYPE`、`SAML1_TOKEN_TYPE`，对应 `urn:ietf:params:oauth:token-type:*` 一系列 token 类型）组成。这些属性要么是直接给出的 bearer token，要么是用于 token exchange 的 token 类型，不会引入"用服务端给的 client credentials 去换 token"这种身份冒充风险。

这样，即便 `LoadTableResponse` 返回了 `credential`，它也会被过滤掉，table session 只能基于 parent（catalog）会话进行 token exchange 或直接使用 parent 的 token，认证身份始终由客户端控制。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：引入 table session 认证属性白名单，过滤掉 `credential` 等不在白名单中的认证属性。

**工作逻辑**：

1. 新增 import：`ImmutableSet`。

2. 在类常量区新增白名单常量：

```java
// Auth-related properties that are allowed to be passed to the table session
private static final Set<String> TABLE_SESSION_ALLOW_LIST =
    ImmutableSet.<String>builder()
        .add(OAuth2Properties.TOKEN)
        .addAll(TOKEN_PREFERENCE_ORDER)
        .build();
```

`TOKEN_PREFERENCE_ORDER` 是已有的 `List<String>`，包含五个 `urn:ietf:params:oauth:token-type:*` 值。白名单就是 `token` + 这五个 token 类型。

3. 修改 `tableSession(Map<String, String> tableConf, AuthSession parent)` 方法。修改前直接 `newSession(tableConf, tableConf, parent)`：

```java
private AuthSession tableSession(Map<String, String> tableConf, AuthSession parent) {
    Pair<String, Supplier<AuthSession>> newSession = newSession(tableConf, tableConf, parent);
    if (null == newSession) {
      return parent;
    }
    AuthSession session = tableSessions.get(newSession.first(), id -> newSession.second().get());
    return session != null ? session : parent;
}
```

修改后先构建过滤后的 `credentials` map，只保留白名单内的属性，再把它传给 `newSession`：

```java
private AuthSession tableSession(Map<String, String> tableConf, AuthSession parent) {
    Map<String, String> credentials = Maps.newHashMapWithExpectedSize(tableConf.size());
    for (String prop : tableConf.keySet()) {
      if (TABLE_SESSION_ALLOW_LIST.contains(prop)) {
        credentials.put(prop, tableConf.get(prop));
      }
    }

    Pair<String, Supplier<AuthSession>> newSession = newSession(credentials, tableConf, parent);
    if (null == newSession) {
      return parent;
    }
    AuthSession session = tableSessions.get(newSession.first(), id -> newSession.second().get());
    return session != null ? session : parent;
}
```

注意第二个参数仍是 `tableConf`（未过滤），因为 `newSession` 内部会从 properties 读取 `token-expires-at-ms` 等非认证元信息，这些属性不需要被过滤。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：更新 `testTableAuth` 中一个测试用例的预期，并重构 `testTableAuth` 辅助方法中对 HTTP 请求的验证逻辑，使其适配"credential 被忽略后 table session 回退到 parent"的新行为。

**工作逻辑**：

1. 修改 `testTableAuth` 的一个用例（catalog 配置含 `urn:ietf:params:oauth:token-type:id_token`，table 配置含 `credential`）：

```diff
     testTableAuth(
         "catalog",
         ImmutableMap.of("urn:ietf:params:oauth:token-type:id_token", "id-token"),
-        ImmutableMap.of("credential", "table-user:secret"),
+        ImmutableMap.of("credential", "table-user:secret"), // will be ignored
+        ImmutableMap.of("Authorization", "Bearer token-exchange-token:sub=id-token,act=catalog"),
         ImmutableMap.of("Authorization", "Bearer token-exchange-token:sub=id-token,act=catalog"),
-        ImmutableMap.of("Authorization", "Bearer client-credentials-token:sub=table-user"),
         oauth2ServerUri);
```

修改前，table 配置中的 `credential` 会让 table session 通过 client credentials 流程换取 `client-credentials-token:sub=table-user`，因此 table 请求的 `Authorization` 头与 context 请求不同。修改后，`credential` 被白名单过滤掉，table session 回退到 parent（catalog）会话，所以 table 请求与 context 请求都使用同一个 `token-exchange-token:sub=id-token,act=catalog`。

2. 重构 `testTableAuth` 中对 token 请求与 GET 请求的验证逻辑：

```diff
-    // if the table returned a bearer token, there will be no token request
-    if (!tableConfig.containsKey("token")) {
-      // client credentials or token exchange to get a table token
+    // if the table returned a bearer token or a credential, there will be no token request
+    if (!tableConfig.containsKey("token") && !tableConfig.containsKey("credential")) {
+      // token exchange to get a table token
       Mockito.verify(adapter, times(1))
           .execute(...)
     }
```

修改前：只要 table 配置不含 `token`，就期望发生一次 token 请求（可能是 client credentials 或 token exchange）。修改后：只有 table 配置既不含 `token` 也不含 `credential` 时，才期望发生一次 token exchange 请求。这是因为现在 `credential` 被忽略后，若 table 配置只有 `credential`（无 token-type 属性），`newSession` 返回 null，table session 回退到 parent，不会再发起 token 请求；若 table 配置含 `token`，则直接用 token，也无 token 请求。

```diff
-    // automatic refresh when metadata is accessed after commit
-    Mockito.verify(adapter)
-        .execute(..., eq(expectedTableHeaders), any());
-
-    // load table from catalog
-    Mockito.verify(adapter)
-        .execute(..., eq(expectedContextHeaders), any());
-
-    // refresh loaded table
-    Mockito.verify(adapter)
-        .execute(..., eq(expectedTableHeaders), any());
+    if (expectedContextHeaders.equals(expectedTableHeaders)) {
+      // load table from catalog + refresh loaded table
+      Mockito.verify(adapter, times(2))
+          .execute(..., eq(expectedTableHeaders), any());
+    } else {
+      // load table from catalog
+      Mockito.verify(adapter)
+          .execute(..., eq(expectedContextHeaders), any());
+
+      // refresh loaded table
+      Mockito.verify(adapter)
+          .execute(..., eq(expectedTableHeaders), any());
+    }
```

修改前：分别验证一次"用 context headers 加载表"和两次"用 table headers 的 GET"（一次 refresh + 一次 load）。修改后：当 `expectedContextHeaders` 与 `expectedTableHeaders` 相同时（即 table session 回退到 parent 的场景），用 `times(2)` 验证两次 GET 都用 table headers；不同时则保持原有的分别验证。这样能正确处理 credential 被忽略后 table 与 context 头一致的边界情况。

## 小结

- **成效**：堵住 REST Catalog 中"服务端通过 `LoadTableResponse` 返回 `credential` 让客户端以服务端指定身份发起后续请求"的安全隐患（#10256）。现在只有 `token` 与 `urn:ietf:params:oauth:token-type:*` 类属性能从表级配置传入 table session，`credential` 被显式过滤。
- **影响范围**：2 个文件，`RESTSessionCatalog.java` 新增白名单常量与过滤逻辑（约 17 行），`TestRESTCatalog.java` 更新一个测试用例的预期与验证逻辑（约 74 行变动）。无 API 签名变更，不影响正常 token/token-exchange 流程，仅影响 table 配置中带 `credential` 的边界场景。
- **回迁到 1.4.x 的注意事项**：该提交是安全修复，**强烈建议回迁到 1.4.x**。理由：
  1. 安全问题对 1.4.x 同样存在，1.4.x 的 `RESTSessionCatalog.tableSession` 应该也直接把 `tableConf` 传给 `newSession`，需要同样加白名单过滤；
  2. `OAuth2Properties.TOKEN`、`TOKEN_PREFERENCE_ORDER` 等常量在 1.4.x 中应已存在，可直接复用；
  3. 测试改动需要同步回迁，尤其是 `testTableAuth` 的验证逻辑（`expectedContextHeaders.equals(expectedTableHeaders)` 分支）；
  4. 回迁时需注意 1.4.x 的 `TestRESTCatalog` 是否已有该测试用例，以及 `testTableAuth` 辅助方法的签名是否与 main 一致（参数顺序、mock 设置等）；若 1.4.x 已有自定义测试改动，需要手动合并；
  5. 该改动不影响正常认证流程，只影响"table 配置含 credential"这一边界场景，回迁风险低、收益高。
