# 提交 0768：Build: Bump nessie from 0.81.1 to 0.82.0 (#10318)

## 提交信息

- **序号**：0768 / 4088
- **哈希**：090fe2eca9e2e01cb96857052fa688be8530edec
- **短哈希**：090fe2eca
- **日期**：2024-05-16 14:05:19 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.81.1 to 0.82.0 (#10318)
- **PR/Issue**：#10318

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 构建中声明的 Nessie 版本从 `0.81.1` 升级到 `0.82.0`。Nessie 是 Iceberg 支持的 Catalog 实现之一（提供 Git 风格的版本化数据目录），Iceberg 在 `nessie` 模块中集成 Nessie 客户端与测试扩展。本次升级属于 semver 的 minor 版本升级（0.81.x → 0.82.x），按语义化版本约定包含向后兼容的功能更新与缺陷修复。升级目的是跟进 Nessie 上游发布，获取最新修复与功能，保持依赖新鲜度。

## 如何达成设计目的

### 升级范围

Nessie 在 Iceberg 中以集中式版本变量管理。`gradle/libs.versions.toml` 中定义 `nessie = "0.81.1"`，所有 Nessie 相关坐标（`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`）均通过版本引用（version catalog 的 ref 机制）复用该变量。因此只需修改一处版本号，所有 Nessie 依赖坐标同步升级。Dependabot 提交信息中列出的四个 `org.projectnessie.nessie:*` 依赖均为 `direct:production`、`version-update:semver-minor` 类型，确认是受版本变量控制的直接生产依赖。

### 升级方式

Dependabot 通过 TOML 版本目录的单行修改完成升级，符合 Gradle 版本目录（Version Catalog）的最佳实践：版本号集中管理、单一改动点、易于审查与回滚。升级过程不涉及任何代码改动，仅是依赖元数据更新。

### 兼容性

0.81.1 → 0.82.0 是 minor 版本升级，按 Nessie 的语义化版本约定应保持 API 向后兼容。本次升级不触及 Iceberg `nessie` 模块的任何 Java 代码或测试代码，说明升级未引入任何破坏性 API 变更（否则需同步调整 Iceberg 侧调用代码）。CI 中的 Nessie 集成测试（使用 `nessie-jaxrs-testextension` 等）会验证升级后的兼容性。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Nessie 版本变量。

**工作逻辑**：将版本目录中 `nessie = "0.81.1"` 修改为 `nessie = "0.82.0"`。该变量被同文件中以下坐标引用（本次 diff 未直接改动这些坐标行，它们通过 `{ version.ref = "nessie" }` 自动跟随）：

- `nessie-client`
- `nessie-jaxrs-testextension`
- `nessie-versioned-storage-inmemory-tests`
- `nessie-versioned-storage-testextension`

修改位于 `nessie` 版本声明行（`microprofile-openapi-api`、`mockito`、`mockserver` 等邻近依赖行之间），仅一行一字之改：`0.81.1` → `0.82.0`。

## 小结

- **成效**：将 Nessie 依赖从 0.81.1 升级到 0.82.0，跟进上游 minor 发布。得益于 Gradle 版本目录的集中式版本管理，仅需修改一处版本变量即可同步所有 Nessie 相关依赖。无代码改动，表明升级与现有 Iceberg Nessie 集成代码兼容。
- **影响范围**：影响构建依赖解析。具体影响 `nessie` 模块（Iceberg 的 Nessie Catalog 集成）的编译与测试依赖，以及任何使用 Nessie 客户端的测试扩展。对其他模块（core、spark、flink 等）无直接影响。运行时仅影响使用 Nessie Catalog 的用户。
- **回迁注意事项**：
  1. 这是纯依赖版本升级，回迁到 1.4.x 分支风险极低，建议直接回迁以保持依赖一致性。
  2. 回迁前需确认 1.4.x 分支的 Nessie 版本仍为 0.81.1（或更低）；若 1.4.x 已独立升级过 Nessie，需对比版本避免回退。
  3. 回迁后需确认 1.4.x 分支的构建环境能解析 Nessie 0.82.0（仓库可访问、签名校验通过）。
  4. 若 1.4.x 分支有针对 Nessie 0.81.x 的特定兼容补丁或测试调整，需评估这些补丁在 0.82.0 下是否仍需要。
  5. semver minor 升级通常向后兼容，但建议回迁后运行 `nessie` 模块完整测试套件确认无回归。
