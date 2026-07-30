# 提交 1391：Build: Bump nessie from 0.99.0 to 0.100.0 (#11567)

## 提交信息

- **序号**：1391 / 4088
- **哈希**：491718c3e22eff592d6924c70496f5b4eeb7a978
- **短哈希**：491718c3e
- **日期**：2024-11-18（Mon Nov 18 11:33:42 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.99.0 to 0.100.0
- **PR/Issue**：#11567

## 总体目的

Nessie 是一个提供 Git 风格版本化数据目录的服务（NessieCatalog），Iceberg 的 `nessie` 模块通过 `nessie-client` 与 Nessie 服务端交互，实现基于分支/标签的表版本管理。在 `gradle/libs.versions.toml` 中，`nessie` 版本变量统一管控 4 个制品：

- `nessie-client`：生产依赖，NessieCatalog 使用的客户端 SDK；
- `nessie-jaxrs-testextension`：测试依赖，用于在测试中启动嵌入式 Nessie JAX-RS 服务；
- `nessie-versioned-storage-inmemory-tests`：测试依赖，提供内存版存储后端用于测试；
- `nessie-versioned-storage-testextension`：测试依赖，测试用存储扩展。

本提交是 Dependabot 发起的次版本升级（0.99.0 → 0.100.0），属于 `version-update:semver-minor` 类型。Nessie 0.100.0 是通向 1.0 正式版的重要里程碑版本，可能包含新功能、API 调整与 bug 修复。本次升级将 4 个制品版本统一从 0.99.0 提升到 0.100.0。

## 如何达成设计目的

Dependabot 检测到 `gradle/libs.versions.toml` 中 `nessie = "0.99.0"` 有新版本 0.100.0 发布，遂创建 PR 将版本变量升级。由于 4 个 nessie 制品均通过 `version.ref = "nessie"` 引用同一版本变量，一次修改即可同步升级全部依赖。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

**修改目的**：升级 Nessie 版本变量。

**工作逻辑**：

```diff
-nessie = "0.99.0"
+nessie = "0.100.0"
```

该变量被以下 4 个库定义引用（均为 `version.ref = "nessie"`）：

| 库别名 | Maven 坐标 | 用途 |
|--------|-----------|------|
| `nessie-client` | `org.projectnessie.nessie:nessie-client` | 生产依赖，NessieCatalog 客户端 |
| `nessie-jaxrs-testextension` | `org.projectnessie.nessie:nessie-jaxrs-testextension` | 测试依赖，嵌入式 JAX-RS 服务 |
| `nessie-versioned-storage-inmemory-tests` | `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests` | 测试依赖，内存存储后端 |
| `nessie-versioned-storage-testextension` | `org.projectnessie.nessie:nessie-versioned-storage-testextension` | 测试依赖，存储测试扩展 |

升级后，Gradle 解析依赖时会自动将这 4 个制品解析到 0.100.0 版本。由于是次版本（minor）升级，可能包含新的 API 或行为变更，但 Nessie 在 0.x 阶段遵循语义化版本约定的程度有限，需关注 NessieCatalog 相关测试是否通过。

## 小结

- **成效**：将 Nessie 4 个制品从 0.99.0 升级到 0.100.0，引入次版本新功能与修复。Nessie 0.100.0 是通向 1.0 的重要版本。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行变更，影响 `nessie` 模块的生产依赖（`nessie-client`）和测试依赖（3 个 testextension/inmemory-tests 制品）。次版本升级可能涉及 API 变更，需验证 NessieCatalog 相关测试。
- **回迁到 1.4.x 的注意事项**：
  1. 1.4.x 分支当前 Nessie 版本较旧（`0.71.0`），与 0.100.0 跨度较大，直接 cherry-pick 单个 Dependabot 升级可能因中间版本的 API 变更而编译失败。建议按顺序回迁中间的 Nessie 升级提交，或一次性升级到目标版本后修复适配。
  2. 需关注 `nessie-client` 的 API 变更：NessieCatalog 实现中对 Nessie 客户端 API 的调用可能需要适配（如 `NessieCatalog`、`NessieTableOperations` 等类）。
  3. 注意 1.4.x 的 `libs.versions.toml` 中库别名可能是 `nessie-versioned-storage-inmemory`（无 `-tests` 后缀），与 main 分支的 `nessie-versioned-storage-inmemory-tests` 不同，回迁时需确认实际的库别名。
  4. 升级后应运行 `nessie` 模块的完整测试套件，特别是 `TestNessieCatalog`、`TestNessieTable` 等集成测试，验证客户端与服务端的兼容性。
