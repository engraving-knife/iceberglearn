# 提交 1709：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#12209)

## 提交信息

- **序号**：1709 / 4088
- **哈希**：964c01bcb37b028c077f22458a6aef3c9a970df0
- **短哈希**：964c01bcb
- **日期**：2025-02-10 07:39:13 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#12209)
- **PR/Issue**：#12209

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级提交。Apache HttpComponents Client 5（httpclient5）从 5.4.1 升级到 5.4.2，属于补丁版本（semver-patch）更新。

httpclient5 是 Iceberg 项目中用于 HTTP 通信的直接生产依赖（direct:production），可能被用于访问远程元数据存储、REST catalog 等场景。Dependabot 会定期扫描项目依赖，发现新版本后自动创建 PR 进行升级。此次升级的目的是保持依赖的最新状态，获取 5.4.2 版本中包含的 bug 修复和安全补丁。

根据发布说明，5.4.2 是一个维护性版本，主要包含缺陷修复和改进，不涉及破坏性 API 变更，因此升级风险很低。

## 如何达成设计目的

通过修改 Gradle 版本目录（Version Catalog）文件 `gradle/libs.versions.toml` 中 httpclient5 的版本声明，从 `5.4.1` 改为 `5.4.2`。Gradle 版本目录是集中管理依赖版本的方式，修改此文件后，所有引用该版本变量的模块都会自动使用新版本，无需逐模块修改。

## 修改详情

### `gradle/libs.versions.toml`（修改, ±2 lines）

**修改目的**：升级 httpclient5 依赖版本。

**工作逻辑**：将第 51 行的 `httpcomponents-httpclient5 = "5.4.1"` 修改为 `httpcomponents-httpclient5 = "5.4.2"`。这是版本目录中对 httpclient5 版本号的集中声明位置，所有子模块通过引用此变量来获取版本号。

## 小结

- **成效**：将 httpclient5 从 5.4.1 升级到 5.4.2，获取最新的 bug 修复和改进。
- **影响范围**：所有使用 httpclient5 的模块，但由于是补丁版本升级，API 兼容，影响面很小。
- **回迁到 1.4.x 的注意事项**：可以安全回迁，只需修改版本号即可。但需确认 1.4.x 分支中该依赖的版本号是否一致，以及是否有其他依赖联动。属于低风险的依赖更新，建议回迁以保持依赖安全。
