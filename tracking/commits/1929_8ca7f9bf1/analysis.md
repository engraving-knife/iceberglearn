# 提交 1929：Build: Bump jetty from 11.0.24 to 11.0.25 (#12618)

## 提交信息

- **序号**：1929 / 4088
- **哈希**：8ca7f9bf1a3dafe3e86cbcf0c4e8b41519e728bb
- **短哈希**：8ca7f9bf1
- **日期**：2025-03-27 19:44:46 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jetty from 11.0.24 to 11.0.25 (#12618)
- **PR/Issue**：#12618

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，目的是将 Iceberg 项目依赖的 Eclipse Jetty 服务器/Servlet 库从 11.0.24 升级到 11.0.25。

Jetty 在 Iceberg 项目中作为直接的生产依赖（direct:production）使用，主要包含 `org.eclipse.jetty:jetty-server` 与 `org.eclipse.jetty:jetty-servlet` 两个制品。这两者通常被用于测试或本地服务场景（例如 S3FileIO 的测试依赖 Jetty 作为 mock 服务器）。从 11.0.24 到 11.0.25 是一个 semver-patch（补丁版本）升级，通常只包含 bug 修复与安全补丁，不引入 API 变更，理论上对调用方无破坏性影响。

此类依赖升级是仓库维护中由 Dependabot 自动执行的例行操作，目的是保持依赖处于最新补丁版本，以获取安全修复和稳定性改进。

## 如何达成设计目的

设计思路很简单：在集中管理依赖版本的 `gradle/libs.versions.toml` 中将 `jetty` 版本字符串从 `11.0.24` 修改为 `11.0.25`，所有引用该版本键的模块会自动传递新版本。同时更新 `open-api/LICENSE` 文件以同步依赖清单中第三方许可证文本的细节（补上文件末尾缺失的换行符）。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 Jetty 依赖版本。

**工作逻辑**：将 `[versions]` 区块中的 `jetty = "11.0.24"` 修改为 `jetty = "11.0.25"`。该键被 `jetty-server` 与 `jetty-servlet` 两个库引用，因此一次修改即可同时升级两个制品。

### `open-api/LICENSE` (修改, +1/-1 lines)

**修改目的**：同步 open-api 模块的第三方依赖许可证清单文本。

**工作逻辑**：在 LICENSE 文件末尾补上缺失的换行符（原本文件末尾以 `--...---` 分隔符结尾但缺少换行，即 `\ No newline at end of file`，修改后文件末尾正常换行）。这是由于依赖版本变化触发了许可证清单重新生成。

## 总结

本次提交为 Dependabot 自动执行的 Jetty 补丁版本升级（11.0.24 → 11.0.25），仅修改依赖版本声明与同步许可证文本，不涉及业务代码变更，属于例行的依赖维护。
