# 提交 0070：Nessie: Use custom client builder name (#8798)

## 提交信息

- **序号**：0070 / 4088
- **哈希**：bd66d0f54802aaf269fe426901d54d69e08c577a
- **短哈希**：bd66d0f54
- **日期**：2023-10-19 17:17:44 +0530
- **作者**：Naveen Kumar
- **提交说明**：Nessie: Use custom client builder name (#8798)
- **PR/Issue**：#8798

## 总体目的

本提交跟随 Nessie 客户端 API 的演进，把 Iceberg Nessie 集成测试中"通过实现类全限定名指定自定义 Nessie 客户端构造器"的旧机制，迁移到"通过逻辑名称指定客户端构造器"的新机制。

背景是：Nessie 客户端在较早版本中提供 `CONF_NESSIE_CLIENT_BUILDER_IMPL` 配置项，让用户传入一个实现了 `NessieClientBuilder` 的类的全限定名（例如 `org.projectnessie.client.http.HttpClientBuilder`），Nessie 通过反射加载该类来构造客户端。这种"按类名加载"的方式有几个缺点：耦合了具体实现类、不利于模块化/JPMS、不利于通过 ServiceLoader 按名称发现、也难以在不同 Nessie 版本间保持稳定。为此 Nessie 引入了 `CONF_NESSIE_CLIENT_NAME` 配置项，用户传入一个逻辑名称（如 `"HTTP"`），Nessie 内部通过 ServiceLoader 发现已注册的 builder 并按 `name()` 方法匹配，从而解耦了配置与具体实现类。

本提交把 Iceberg 测试中用到旧机制的三处全部迁到新机制，并删除了仅在新机制下无意义的旧测试用例，使测试与 Nessie 最新客户端 API 保持一致。对 Iceberg 演进的意义在于：跟随上游 Nessie 的 API 变更，避免因使用已废弃的配置项而导致升级 Nessie 版本后测试失败，保证 Iceberg-Nessie 集成的向前兼容性。

## 如何达成设计目的

改动只涉及一个测试文件 `TestCustomNessieClient.java`，共三处变更：第一，把 `testUnnecessaryDefaultCustomClient` 中的配置键从 `CONF_NESSIE_CLIENT_BUILDER_IMPL` + `HttpClientBuilder.class.getName()` 换成 `CONF_NESSIE_CLIENT_NAME` + `"HTTP"`，用逻辑名称代替类名；第二，把 `testNonExistentCustomClient` 中的配置键和测试数据同步换成新机制，并更新期望的异常消息以匹配新机制下"按名称查找失败"的报错文案；第三，删除整个 `testCustomClientByImpl` 测试方法，因为该方法专门验证旧的"按实现类名加载"路径，在新机制下已不再适用。同时移除不再使用的 `HttpClientBuilder` import。

## 修改详情

### `nessie/src/test/java/org/apache/iceberg/nessie/TestCustomNessieClient.java`

**修改目的**：将测试从 `CONF_NESSIE_CLIENT_BUILDER_IMPL`（按类名）迁移到 `CONF_NESSIE_CLIENT_NAME`（按逻辑名），与 Nessie 最新客户端 API 对齐。

**工作逻辑**：

1. **移除 import**：删除 `import org.projectnessie.client.http.HttpClientBuilder;`，因为不再需要在测试里引用 `HttpClientBuilder` 的类名。

2. **`testUnnecessaryDefaultCustomClient` 测试**：该测试验证"显式指定默认的 HTTP 客户端构造器"不会报错。原配置为：
   ```java
   NessieConfigConstants.CONF_NESSIE_CLIENT_BUILDER_IMPL,
   HttpClientBuilder.class.getName(),
   ```
   即传入 `HttpClientBuilder` 的全限定类名让 Nessie 反射加载。改为：
   ```java
   NessieConfigConstants.CONF_NESSIE_CLIENT_NAME,
   "HTTP",
   ```
   即传入逻辑名称 `"HTTP"`，由 Nessie 内部 ServiceLoader 匹配到 HTTP 客户端 builder。两者语义等价（都指向同一个 HTTP 客户端实现），但新写法不再耦合具体类名。

3. **`testNonExistentCustomClient` 测试**：该测试验证"指定一个不存在的客户端"会抛异常。原配置传入一个不存在的类名 `"non.existent.ClientBuilderImpl"`，期望异常消息包含 `"Cannot load Nessie client builder implementation class"`（反射加载失败的文案）。改为传入一个不存在的逻辑名称 `"non_existent_Client"`，期望异常消息变为 `"Requested Nessie client named non_existent_Client not found"`（按名称查找未命中的文案）。这反映了新机制的错误路径也从"类加载失败"变为"名称未注册"。

4. **删除 `testCustomClientByImpl` 测试**：原测试专门验证旧的"按实现类名"机制——传入 `DummyClientBuilderImpl.class.getName()`（一个测试内部定义的假 builder 类），期望其 `build()` 被调用并抛 `"BUILD CALLED"`。由于旧机制 `CONF_NESSIE_CLIENT_BUILDER_IMPL` 在新版本 Nessie 中被废弃/移除，这个测试已无意义，整段删除。注意：`testCustomClientByName` 测试（使用 `CONF_NESSIE_CLIENT_NAME` + `"Dummy"`）被保留，它覆盖了"按名称加载自定义 builder"的等价场景，且 `DummyClientBuilderImpl` 内部类也因被 `testCustomClientByName` 引用而保留。

## 小结

本提交将 Nessie 集成测试从已废弃的 `CONF_NESSIE_CLIENT_BUILDER_IMPL`（按实现类全限定名加载客户端构造器）迁移到新的 `CONF_NESSIE_CLIENT_NAME`（按逻辑名称发现构造器）机制，跟随上游 Nessie 客户端 API 演进，保证 Iceberg-Nessie 集成的向前兼容性。
