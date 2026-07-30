# 提交 0398：Build: Bump nessie from 0.76.2 to 0.76.3

## 提交信息

- **序号**：0398
- **哈希**：d740347343a4e03d747aeedc307c5d505415707c
- **短哈希**：d74034734
- **日期**：Mon Jan 22 08:32:40 2024 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.76.2 to 0.76.3 (#9537)

  Bumps `nessie` from 0.76.2 to 0.76.3.

  Updates `org.projectnessie.nessie:nessie-client` from 0.76.2 to 0.76.3
  Updates `org.projectnessie.nessie:nessie-jaxrs-testextension` from 0.76.2 to 0.76.3
  Updates `org.projectnessie.nessie:nessie-versioned-storage-inmemory` from 0.76.2 to 0.76.3
  Updates `org.projectnessie.nessie:nessie-versioned-storage-testextension` from 0.76.2 to 0.76.3

  updated-dependencies:
  - dependency-name: org.projectnessie.nessie:nessie-client (direct:production, version-update:semver-patch)
  - dependency-name: org.projectnessie.nessie:nessie-jaxrs-testextension (direct:production, version-update:semver-patch)
  - dependency-name: org.projectnessie.nessie:nessie-versioned-storage-inmemory (direct:production, version-update:semver-patch)
  - dependency-name: org.projectnessie.nessie:nessie-versioned-storage-testextension (direct:production, version-update:semver-patch)

  Signed-off-by: dependabot[bot] <support@github.com>
  Co-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **PR/Issue**：#9537

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖版本升级提交，将 Apache Iceberg 项目所依赖的 Nessie 系列组件从 0.76.2 升级到 0.76.3。

Nessie（Project Nessie）是一个面向数据湖的事务型目录服务（transactional catalog），它为 Iceberg 等表格式提供版本化的元数据管理能力，支持分支、标签、提交等 Git 风格的版本控制语义。Iceberg 与 Nessie 有紧密的集成关系：Iceberg 提供了 NessieCatalog 实现，允许用户将表元数据存储在 Nessie 服务中。因此 Iceberg 的依赖清单和测试套件中都引入了多个 Nessie 组件。

本次升级是 semver-patch（补丁版本）级别的小版本升级（0.76.2 → 0.76.3），按照语义化版本约定，这类升级仅包含 bug 修复和内部改进，不引入破坏性变更。Dependabot 通过单一版本变量 `nessie = "0.76.x"` 统一管理以下四个组件的版本：

1. `nessie-client`：Nessie 的客户端库，用于与 Nessie 服务通信。
2. `nessie-jaxrs-testextension`：基于 JAX-RS 的测试扩展，用于在测试中启动内嵌 Nessie 服务。
3. `nessie-versioned-storage-inmemory`：内存版存储后端，主要用于测试场景。
4. `nessie-versioned-storage-testextension`：版本化存储的测试扩展。

由于 Iceberg 使用 Gradle 版本目录（libs.versions.toml）集中管理依赖版本，只需修改一处版本号声明即可同步升级所有相关组件，体现了版本目录模式在依赖治理上的优势。

## 如何达成设计目的

Dependabot 扫描到 `gradle/libs.versions.toml` 中 nessie 版本存在可用更新后，自动创建 PR 并将版本号从 "0.76.2" 改为 "0.76.3"。由于所有 Nessie 子组件都通过同一个版本变量引用，单行修改即可完成全部四个组件的升级。提交信息中详细列出了受影响的依赖列表、依赖类型（direct:production）和更新类型（version-update:semver-patch），便于维护者评估变更范围。

## 修改详情

### gradle/libs.versions.toml

**修改目的**：将 Nessie 版本变量从 0.76.2 升级到 0.76.3，进而使所有引用该变量的 Nessie 组件同步升级。

**工作逻辑**：在 Gradle 版本目录文件中，将第 69 行附近的 `nessie = "0.76.2"` 修改为 `nessie = "0.76.3"`。这是整个变更的唯一实质修改。版本目录中定义的 `nessie` 变量会被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory`、`nessie-versioned-storage-testextension` 等库引用，因此这一处改动会自动传播到所有这些依赖，保证它们版本一致。

## 小结

这是一个由 Dependabot 自动化维护的依赖补丁升级提交，体现了 Iceberg 项目对依赖及时更新以获取 bug 修复和安全改进的工程实践。通过版本目录集中管理 Nessie 系列组件版本，使升级操作极为简洁——单行修改即可完成。这类补丁升级风险低、收益明确，是保持项目依赖健康度的重要一环。
