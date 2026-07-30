# 提交 2694：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#14202)

## 提交信息

- **序号**：2694 / 4088
- **哈希**：29920daf522184438914e69da9e7c680b612edc1
- **短哈希**：29920daf5
- **日期**：2025-09-29 08:58:23 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.httpcomponents.client5:httpclient5 from 5.5 to 5.5.1 (#14202)
- **PR/Issue**：#14202

## 总体目的

Dependabot 自动将 `org.apache.httpcomponents.client5:httpclient5` 从 `5.5` 升级到 `5.5.1`。该库是 Apache HttpComponents Client 5.x 系列，提供 HTTP/HTTPS 客户端能力，Iceberg 在与 REST Catalog、S3、Azure 等存储/服务交互时可能间接或直接使用此依赖。

本次为 semver-patch 升级，Dependabot 元数据标注为 `direct:production`、`version-update:semver-patch`，属直接生产依赖的小版本更新，通常包含 bug 修复与安全补丁，保持 API 兼容。提交说明附带了上游 `RELEASE_NOTES.txt` 与 commits 对比链接，方便审查者查阅具体修复内容。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `httpcomponents-httpclient5` 别名的版本号，从 `5.5` 改为 `5.5.1`。该别名为整个构建统一引用点，改动一处即可让所有依赖该客户端的模块升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Apache HttpComponents Client 5 版本。

**工作逻辑**：将 `httpcomponents-httpclient5 = "5.5"` 改为 `httpcomponents-httpclient5 = "5.5.1"`。后续构建会自动解析并拉取新版本，相关模块获得 5.5.1 的修复与改进。

## 总结

Dependabot 自动将 Apache HttpClient 5 从 5.5 升级到 5.5.1，属常规补丁级依赖维护，旨在获取上游修复（可能含安全补丁）。改动仅一行版本号，通过 Gradle 版本目录集中生效，风险低。
