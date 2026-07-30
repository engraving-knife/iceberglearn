# 提交 1222：Build: Bump junit-platform from 1.11.1 to 1.11.2 (#11266)

## 提交信息

- **序号**：1222 / 4088
- **哈希**：410477fd9e7e3f9e5519787d7b5f93789757350b
- **短哈希**：410477fd9
- **日期**：2024-10-12（Sat Oct 12 21:07:58 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump junit-platform from 1.11.1 to 1.11.2 (#11266)
- **PR/Issue**：#11266

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级提交。JUnit Platform 是 JUnit 5 的基础平台层，提供测试引擎的发现与执行框架。Iceberg 在 `gradle/libs.versions.toml` 中统一管理 `junit-platform` 版本号，该版本号同时被 `junit-platform-suite-api` 和 `junit-platform-suite-engine` 两个组件引用（用于测试套件 Suite API 和 Suite 引擎）。

本次将 `junit-platform` 从 1.11.1 升级到 1.11.2，属于补丁版本（semver-patch）升级，目的是获取最新的 bug 修复和稳定性改进，保持测试基础设施处于最新状态。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中 `junit-platform` 的版本声明，从 `"1.11.1"` 改为 `"1.11.2"`。由于 Iceberg 使用 Gradle 版本目录（Version Catalog）统一管理依赖，所有引用 `junit-platform` 版本号的库（`junit-platform-suite-api`、`junit-platform-suite-engine`）会自动同步到新版本，无需逐个修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 junit-platform 版本号。

**工作逻辑**：将第 67 行的版本声明从：

```toml
junit-platform = "1.11.1"
```

改为：

```toml
junit-platform = "1.11.2"
```

该版本变量被 `libs.versions.toml` 中以下库条目通过 `version.ref` 引用：
- `org.junit.platform:junit-platform-suite-api`
- `org.junit.platform:junit-platform-suite-engine`

这两个组件用于 Iceberg 测试代码中通过 `@Suite` 注解组织和运行测试套件。升级后，下次构建时 Gradle 会自动解析并拉取 1.11.2 版本的 JAR。

## 小结

- **成效**：junit-platform 从 1.11.1 升级到 1.11.2，获取补丁版本的 bug 修复与稳定性改进。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动，无代码逻辑变更。影响范围限于测试基础设施。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前使用的 junit-platform 版本较低（1.4.x 的 `libs.versions.toml` 中甚至可能未引入 1.11.x 系列）。此升级属于测试依赖的补丁版本更新，风险极低。如需回迁，直接将 1.4.x 的 `junit-platform` 版本号同步到 1.11.2 即可，但需确认 1.4.x 的 `junit`（JUnit Jupiter）版本与 platform 1.11.2 兼容。若 1.4.x 不使用 `@Suite` 测试套件功能，则此升级对 1.4.x 意义不大，可视情况决定是否回迁。
