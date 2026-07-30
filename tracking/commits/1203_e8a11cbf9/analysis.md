# 提交 1203：Build: Bump nessie from 0.97.1 to 0.99.0 (#11224)

## 提交信息

- **序号**：1203 / 4088
- **哈希**：e8a11cbf928e46a7549bda033f2d08ac42c349e0
- **短哈希**：e8a11cbf9
- **日期**：2024-10-01（Tue Oct 1 08:18:13 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.97.1 to 0.99.0 (#11224)
- **PR/Issue**：#11224

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，用于把 Iceberg 构建中使用的 Nessie 相关依赖从 `0.97.1` 升级到 `0.99.0`。Nessie 是一个提供事务化版本化数据目录的中间件（Iceberg 通过 `nessie-client` 等模块与之集成，作为可选的 Catalog 实现）。Dependabot 会按 semver-minor（次版本号）级别自动跟踪上游新版本，本次共跨两个次版本（0.97.1 → 0.98.0 → 0.99.0）。

升级的主要动机是：

1. 跟进 Nessie 上游的 bug 修复、功能增强与安全补丁；
2. 让 Iceberg 的 Nessie 集成始终保持与最新稳定版本的兼容性；
3. 避免长期滞后导致后续升级时出现破坏性变更叠加难以处理。

本次升级涉及的 4 个 Nessie artifact（commit message 中列出）：

- `org.projectnessie.nessie:nessie-client`（生产直接依赖，用于连接 Nessie 服务）
- `org.projectnessie.nessie:nessie-jaxrs-testextension`（测试用 JAX-RS 扩展）
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`（内存存储测试扩展）
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`（版本化存储测试扩展）

## 如何达成设计目的

Iceberg 的依赖版本统一集中在 `gradle/libs.versions.toml` 中维护（Gradle Version Catalog 机制），所有 Nessie 子模块共享同一个版本变量 `nessie`，因此只需把这一处版本号从 `0.97.1` 改为 `0.99.0`，整个构建中所有 Nessie 相关依赖会一并升级，避免版本不一致。

Dependabot 自动识别可升级的 artifact、生成 PR、跑 CI 验证后由维护者合入，是 Iceberg 项目的常规依赖维护流程。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

**修改目的**：把 Nessie 共享版本变量从 `0.97.1` 提升到 `0.99.0`。

**工作逻辑**：

```toml
-nessie = "0.97.1"
+nessie = "0.99.0"
```

该变量随后被 `nessie-client = { module = "org.projectnessie.nessie:nessie-client", version.ref = "nessie" }` 等 library 定义引用，所有这些 library 会同步使用新版本。本次升级跨两个次版本（0.98、0.99），属于 semver-minor 范畴，理论上不应包含破坏性 API 变更，但 Nessie 在 0.98/0.99 中可能调整了某些客户端 API 或存储测试扩展的行为，需要依赖 Iceberg CI 验证。

## 小结

- **成效**：把构建中所有 Nessie 相关依赖从 0.97.1 统一升级到 0.99.0，保持与上游同步。仅修改 1 行 1 个文件，无源代码改动。
- **影响范围**：仅影响构建配置；运行时影响的是 Iceberg 与 Nessie Catalog 的集成路径（生产代码 `nessie-client`）以及相关测试扩展。生产环境使用 Nessie Catalog 的用户在升级 Iceberg 后会随之升级 Nessie 客户端版本。
- **回迁到 1.4.x 的注意事项**：纯依赖版本升级，回迁安全。需确认 1.4.x 分支上的 `gradle/libs.versions.toml` 仍然使用单一 `nessie` 版本变量；若 1.4.x 已有其他 Nessie 相关的临时补丁或对 Nessie API 的特殊适配，需关注 0.98/0.99 的 release note 中是否有兼容性提示。建议回迁后跑一次 Nessie 相关模块的 CI（`nessie` 子模块测试）确认无回归。
