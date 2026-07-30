# 提交 0021：Nessie: Remove dead code in NessieCatalog (#8750)

## 提交信息

- **序号**：0021 / 4088
- **哈希**：39239a19555f55e0facc0a585086791a606a4b32
- **短哈希**：39239a195
- **日期**：2023-10-09 09:38:15 +0200
- **作者**：Ajantha Bhat
- **提交说明**：Nessie: Remove dead code in NessieCatalog (#8750)
- **PR/Issue**：#8750

## 总体目的

这个提交清理了 `NessieCatalog` 中一段已经无人调用的死代码——私有静态方法 `createNessieClientBuilder(String customBuilder)`，以及它所依赖的两个 import：`org.apache.iceberg.common.DynMethods` 和 `org.projectnessie.client.http.HttpClientBuilder`。

背景在于 Nessie 客户端构建逻辑的演进。早期 Iceberg 的 `NessieCatalog` 自己负责构造 Nessie HTTP 客户端：它通过 `DynMethods` 反射调用用户配置中传入的自定义 builder 类名，或在未配置时直接回退到 `HttpClientBuilder.builder()`。这套机制写在 `createNessieClientBuilder` 中。后来 Nessie 上游在 `NessieClientBuilder` 上提供了官方的 `createClientBuilderFromSystemSettings(configSource)` 工厂方法，统一了客户端 builder 的发现与配置加载逻辑（支持系统设置、配置源回退等），Iceberg 随之切换到了这一上游 API。当前的 `NessieCatalog.initialize` 中已经直接调用 `NessieClientBuilder.createClientBuilderFromSystemSettings(configSource)`（见 `NessieCatalog.java` 第 121-122 行），原先的 `createNessieClientBuilder` 方法因此彻底失去了调用方，变成了悬留代码。

本提交的价值在于：消除死代码以降低维护负担、避免读者误以为该反射构建路径仍在生效、同时移除两个不再需要的 import，保持 `NessieCatalog` 的简洁性。这是 Iceberg 与上游 Nessie API 协同演进过程中的常规清理动作，对功能没有影响。

## 如何达成设计目的

改动非常聚焦：仅修改 `nessie/src/main/java/org/apache/iceberg/nessie/NessieCatalog.java` 一个文件，删除 18 行（无新增）。具体删除两块内容：一是方法 `createNessieClientBuilder` 的整个方法体（约 16 行，含反射调用、异常包装与回退分支）；二是仅被该方法使用的两个 import（`DynMethods` 和 `HttpClientBuilder`）。由于该方法已无任何调用点（代码库内全文检索 `createNessieClientBuilder` 仅剩定义处），删除是安全的，不需要补充任何替代逻辑。

## 修改详情

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieCatalog.java`

**修改目的**：移除已废弃的 `createNessieClientBuilder` 私有方法及其专属 import，使 `NessieCatalog` 仅依赖上游 `NessieClientBuilder.createClientBuilderFromSystemSettings` 这一条客户端构建路径。

**工作逻辑**：被删除的 `createNessieClientBuilder(String customBuilder)` 原本承担两件事——当 `customBuilder` 非空时，通过 `DynMethods.builder("builder").impl(customBuilder).build().asStatic().invoke()` 反射加载用户指定的 builder 类（失败时包装为 `RuntimeException`）；当 `customBuilder` 为空时，回退到 `HttpClientBuilder.builder()`。这套自实现的反射发现机制现已被 `NessieClientBuilder.createClientBuilderFromSystemSettings(configSource)` 取代，后者由 Nessie 官方维护，统一处理 builder 发现、系统配置读取等逻辑。删除该方法后，`NessieCatalog` 的客户端构建链路从"自定义反射 + 默认回退"收敛为单一上游 API 调用，行为上与切换前的实际运行路径一致（因为该方法早已不被调用），属于纯清理。

## 小结

跟随 Nessie 上游客户端 API 的演进，移除 `NessieCatalog` 中已被 `NessieClientBuilder.createClientBuilderFromSystemSettings` 取代的反射式客户端构建死代码，保持模块简洁。
