# 提交 0485：Build: Bump com.azure:azure-sdk-bom from 1.2.18 to 1.2.20 (#9571)

## 提交信息

- **序号**：0485 / 4088
- **哈希**：8a47f718959d4a17d409e8532c01d303dc8b2fc9
- **短哈希**：8a47f7189
- **日期**：2024-02-07 09:10:22 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.18 to 1.2.20 (#9571)
- **PR/Issue**：#9571（Dependabot 自动提交）

## 总体目的

这是 GitHub Dependabot 自动生成的依赖升级提交，把 `iceberg-azure` 模块使用的 Azure SDK BOM（`com.azure:azure-sdk-bom`）从 `1.2.18` 升级到 `1.2.20`。Azure SDK BOM 是 Azure 官方为 Java SDK 提供的"依赖版本清单"（Bill of Materials），用 Gradle 的 `platform(...)` 或 Maven 的 `<dependencyManagement><import>` 引入后，可以集中管理所有 `com.azure:*` 子模块（如 `azure-storage-file-datalake`、`azure-identity`、`azure-core` 等）的版本，避免在多个直接依赖上分别写死版本号造成的不一致。

`iceberg-azure` 模块在 `build.gradle` 中以 `compileOnly platform(libs.azuresdk.bom)` 形式引入 BOM，并以 `compileOnly "com.azure:azure-storage-file-datalake"` 和 `compileOnly "com.azure:azure-identity"` 拉取具体子模块。这两个子模块是 `ADLSFileIO`、`ADLSInputStream`、`ADLSOutputStream`、`ADLSFileClient` 等组件在编译期需要的 API 表面。`compileOnly` 意味着这些依赖不会被 Iceberg 产物打包传递，最终用户需在运行期自行提供 Azure SDK 实现 jar——这把版本选择的最终决定权留给部署环境，同时让编译期 API 表面跟随 BOM 升级。

从 1.2.18 到 1.2.20 是两个补丁版本的递增（提交说明中 Dependabot 标注为 `version-update:semver-patch`），通常携带若干 bug 修复与小特性增强，但不引入破坏性 API 变更。Dependabot 的 metadata 显示 `dependency-type: direct:production`，即这是一个被生产代码直接使用的依赖（与 `testImplementation`/`runtimeOnly` 相对）。本次升级与紧随其后的提交 0483（升级 Azurite 容器版本到 3.29.0）构成一组配套工作：先把 Azure SDK 升到较新版本，再把 Azurite 测试容器升到与之匹配的版本，从而让集成测试在新版客户端 + 新版服务端模拟器组合下端到端验证 `ADLSFileIO` 的正确性。提交说明中"check if it works against the latest version of the Azure SDK"即指 0483 的动机回到本提交。

值得指出的是，Dependabot 这里只改了 BOM 版本号字面量，没有任何源码或测试改动，说明在 1.2.18 → 1.2.20 之间，Iceberg `iceberg-azure` 用到的 Azure SDK API 子集源码兼容，升级是"零代码改动"的安全升级。

## 如何达成设计目的

实现路径极其简洁：Dependabot 通过 TOML 版本目录（`gradle/libs.versions.toml`）把 `azuresdk-bom` 的版本字面量从 `"1.2.18"` 改为 `"1.2.20"`。该字符串被 `libs.versions.azuresdk.bom` 通过 `version.ref` 引用，并被 `build.gradle` 的 `iceberg-azure` 子项目以 `compileOnly platform(libs.azuresdk.bom)` 消费。`platform(...)` 是 Gradle 引入 BOM 的方式，它会接管该配置下所有 `com.azure:*` 依赖的版本选择，使 `compileOnly "com.azure:azure-storage-file-datalake"` 和 `compileOnly "com.azure:azure-identity"` 不需要写版本号也能解析到 BOM 给定的版本。一行版本号改动即让整个 `iceberg-azure` 编译期所依赖的 Azure SDK 全套版本同步上升两个补丁版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Azure SDK BOM 的版本从 `1.2.18` 升级到 `1.2.20`，让 `iceberg-azure` 编译期所引用的 `com.azure:*` 全套子模块跟随升级。

**工作逻辑**：

该文件是 Gradle Version Catalog，集中管理所有第三方依赖版本。涉及 Azure SDK 的两行：

```toml
azuresdk-bom = "1.2.20"   # 原 "1.2.18"
...
azuresdk-bom = { module = "com.azure:azure-sdk-bom", version.ref = "azuresdk-bom" }
```

第一行是版本字面量；第二行是依赖别名，声明了完整的 Maven 坐标 `com.azure:azure-sdk-bom` 并通过 `version.ref` 指向上面的版本字面量。

在 `build.gradle` 第 534-536 行附近，`iceberg-azure` 子项目通过如下方式消费：

```groovy
compileOnly platform(libs.azuresdk.bom)
compileOnly "com.azure:azure-storage-file-datalake"
compileOnly "com.azure:azure-identity"
```

- `platform(...)` 引入 BOM，让它接管后续 `com.azure:*` 依赖的版本选择。
- 两个 `com.azure:*` 子模块不再写版本号，统一由 BOM 决定，因此 BOM 一升，二者（及其传递依赖如 `azure-core`、`azure-storage-common`、`azure-storage-blob`、`azure-storage-internal-avro` 等）全部同步升级。
- `compileOnly` 保证这些依赖只参与编译期 API、不会被打包到 Iceberg 产物里；最终用户在运行期自行提供匹配版本的 Azure SDK 实现。

由于本提交未触及任何 Java/Scala 源码、测试代码或 build.gradle 的依赖声明结构，可以确认 1.2.18 → 1.2.20 在 Iceberg 用到的 API 子集上源码兼容，本次升级纯属"换版本号"。

## 小结

这是 Dependabot 的常规依赖升级，仅修改 `gradle/libs.versions.toml` 一行版本号，将 `com.azure:azure-sdk-bom` 从 `1.2.18` 升到 `1.2.20`，使 `iceberg-azure` 模块以 `compileOnly platform(...)` 形式引入的 Azure SDK 全套依赖同步上升两个补丁版本。升级未触及任何源码或测试代码，说明在用到的 API 子集上源码兼容。本提交与紧随其后的 0483（升级 Azurite 容器到 3.29.0）构成一组配套工作：先用新 SDK 验证编译，再用新 Azurite 验证集成测试，确保新版 Azure SDK 与 `ADLSFileIO` 之间端到端兼容。
