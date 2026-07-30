# 提交 1606 c19223d35 分析

## 提交信息
- 哈希：c19223d3594dc61e445073cdb26bb8d2a3afbeef
- 日期：2025-01-19 22:14:48 +0100
- 作者：dependabot[bot]
- 消息：Build: Bump com.google.cloud:libraries-bom from 26.52.0 to 26.53.0 (#12003)

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 Google Cloud Libraries BOM（Bill of Materials）`com.google.cloud:libraries-bom` 从 `26.52.0` 升级到 `26.53.0`。Google Cloud Libraries BOM 是一个依赖管理清单，统一规定了 Google Cloud 各客户端库（如 GCS Storage、BigQuery、Auth 等）的兼容版本组合。Iceberg 通过该 BOM 管理与 Google Cloud 交互所需的依赖版本，主要体现在 `gcp` 模块（用于访问 Google Cloud Storage 作为底层文件系统）和 `gcp` bundle 模块中。

本次升级为次版本（Minor）升级（26.52.0 → 26.53.0），通常会对齐 Google Cloud 各客户端库的新版本，可能引入新功能、bug 修复或对齐底层 gRPC/HTTP 传输层。Dependabot 通过 Pull Request #12003 提交该升级建议。

升级 BOM 的核心价值在于：通过单一版本号统一管理多个 Google Cloud 子库的版本，确保各子库之间的兼容性，避免手动指定单一子库版本时产生版本冲突。这对于 GCSFileIO 等 GCP 集成模块的稳定性至关重要。

## 如何达成设计目的

设计思路与其它依赖升级一致：通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 BOM 的版本号，所有通过 `platform(libs.google.libraries.bom)` 引入该 BOM 的模块（主要是 `gcp` 模块和 `gcp-bundle`）在构建时会自动使用 BOM 中规定的新版本子库。

### 修改详情

#### gradle/libs.versions.toml

修改了第 47 行附近的版本声明：

- `google-libraries-bom = "26.52.0"` → `google-libraries-bom = "26.53.0"`

`google-libraries-bom` 是版本目录中用于引用 `com.google.cloud:libraries-bom` 的键名。在 Iceberg 的 `gcp` 模块构建脚本中，通过 `platform(libs.google.libraries.bom)` 的方式引入该 BOM，从而让 `com.google.cloud:google-cloud-storage`、`com.google.cloud:google-cloud-core` 等依赖的版本由 BOM 统一管理，无需在 build.gradle 中显式指定每个子库的版本。

升级 BOM 版本号后，所有由该 BOM 管理的 Google Cloud 子库都会在构建解析时同步到 26.53.0 对应的兼容版本集合，保证 GCP 模块依赖版本的一致性。

## 小结

本次提交将 Google Cloud Libraries BOM 升级至 26.53.0，使 GCP 模块依赖的 Google Cloud 子库版本对齐到新的兼容组合，获取上游 bug 修复与功能改进。修改范围仅涉及版本目录一行，不触碰业务代码。

回迁到 1.4.x 分支的注意事项：
- 该 BOM 影响 `gcp` 模块和 `gcp-bundle`，回迁后需重点回归测试 GCSFileIO 的读写、认证、分页列举等路径。
- 次版本升级可能引入底层 gRPC/HTTP 传输层变更，建议关注 26.53.0 中 `google-cloud-storage` 子库的 Release Notes，确认无破坏性变更。
- 若 1.4.x 的 GCP bundle 是按特定子库版本打包发布的，回迁后需重新构建 bundle 以反映新版本。
