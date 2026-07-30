# 提交 1050：Build: Bump nessie from 0.94.4 to 0.95.0 (#10910)

## 提交信息

- **序号**：1050 / 4088
- **哈希**：8bc1dde5cb587a840476004477e9f5e827ae5d61
- **短哈希**：8bc1dde5c
- **日期**：2024-08-12（Mon Aug 12 17:59:27 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.94.4 to 0.95.0 (#10910)
- **PR/Issue**：#10910

## 总体目的

Nessie 是一个提供 Git 风格版本化数据目录的服务，Iceberg 通过 `nessie-client` 等模块与之集成，作为可选的 Catalog 后端。dependabot 检测到 Nessie 有新 minor 版本 0.95.0（旧版本 0.94.4），本提交是例行把 Iceberg 依赖的 Nessie 相关构件（`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`）统一升级到 0.95.0，获取新版本的功能改进与修复。

由于 Nessie 是 minor 版本升级（0.94.x → 0.95.x），可能伴随 API 变更，因此需要在升级后跑一遍 nessie 集成测试确认兼容性。

## 如何达成设计目的

通过修改 Gradle 版本目录中 `nessie` 的版本声明，从 `0.94.4` 改为 `0.95.0`。该版本号是 Nessie 相关构件的统一基线，被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 四个构件引用，Gradle 会按此基线统一解析这些构件的版本。这是 minor 版本升级，可能引入新的 API/行为，但 dependabot 通常已在 PR 中验证过构建。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Nessie 依赖基线从 0.94.4 升到 0.95.0。

**工作逻辑**：将 `nessie = "0.94.4"` 改为 `nessie = "0.95.0"`。该变量被上述四个 Nessie 构件通过 `${libs.nessie}` 或类似方式引用，统一升级保证这些构件版本一致，避免混合版本导致的二进制不兼容。同文件中相邻的 `mockito`、`mockserver` 等保持不变。

## 小结

- **成效**：把 Iceberg 依赖的 Nessie 相关构件（client、jaxrs-testextension、versioned-storage-inmemory-tests、versioned-storage-testextension）从 0.94.4 统一升级到 0.95.0，获取该 minor 版本的功能改进与修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行修改（但影响 4 个 Nessie 构件的版本）。
- **回迁到 1.4.x 的注意事项**：Nessie 是 minor 版本升级，可能伴随 API/行为变更，**回迁需谨慎**。回迁前应：(1) 确认 1.4.x 的 Nessie 集成测试在 0.95.0 下能通过；(2) 若 1.4.x 已固定在 0.94.x 且 Nessie 客户端 API 有破坏性变更，可能需要同步调整 Nessie Catalog 实现代码；(3) 若 1.4.x 无升级诉求或 Nessie 集成不在维护范围内，可保持原版本不动。建议仅在 1.4.x 明确需要 0.95.0 的修复时才回迁，否则可跳过。
