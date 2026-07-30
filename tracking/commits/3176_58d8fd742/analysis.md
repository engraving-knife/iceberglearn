# 提交 3176：Docs: fix iceberg-cpp website link (#15177)

## 提交信息

- **序号**：3176 / 4088
- **哈希**：58d8fd742f23d41d99a8b4885924a102cc936789
- **短哈希**：58d8fd742
- **日期**：2026-01-29
- **作者**：Junwang Zhao
- **提交说明**：Docs: fix iceberg-cpp website link (#15177)
- **PR/Issue**：#15177

## 总体目的

Iceberg 项目官网（基于 mkdocs 构建）的导航配置中，C++ 客户端库的入口链接此前直接指向 GitHub 仓库地址 `https://github.com/apache/iceberg-cpp/`。随着 iceberg-cpp 项目拥有了独立的官方文档站点，继续将用户引导到 GitHub 仓库页面已不再合适——用户在导航中点击 "C++" 时期望看到的是文档而非源码仓库。

本提交将该链接更正为官方文档站点 `https://cpp.iceberg.apache.org/`，与同级别其他语言客户端（Python `py.iceberg.apache.org`、Rust `rust.iceberg.apache.org`、Go `go.iceberg.apache.org`）保持一致的命名与导航体验。这是一处纯文档维护类修改，确保各语言子项目入口的统一性与专业性。

## 如何达成设计目的

改动涉及两个 mkdocs 导航配置文件：`site/mkdocs-dev.yml`（开发环境导航）和 `site/nav.yml`（正式导航）。两者均在 "Libraries" 一节的 C++ 条目中将 URL 从 GitHub 仓库替换为 `cpp.iceberg.apache.org` 子域名，改动范围极小但确保了开发与正式两套配置同步更新。

## 修改详情

### `site/mkdocs-dev.yml` (+1/-1 lines)

**修改目的**：将开发环境导航中 C++ 库链接从 GitHub 仓库改为官方文档站点。

**工作逻辑**：
在 `nav` 配置的 Libraries 列表中，将 `- C++: https://github.com/apache/iceberg-cpp/` 修改为 `- C++: https://cpp.iceberg.apache.org/`，使开发预览环境的导航与正式站点一致。

### `site/nav.yml` (+1/-1 lines)

**修改目的**：将正式导航中 C++ 库链接从 GitHub 仓库改为官方文档站点。

**工作逻辑**：
与 `mkdocs-dev.yml` 完全相同的改动，确保正式生成的站点文档导航指向 `https://cpp.iceberg.apache.org/`。两个文件需保持同步，避免开发与正式环境出现不一致的链接。

## 总结

本提交是一处轻量的文档维护修正，将 iceberg-cpp 的导航入口从 GitHub 源码仓库更新为独立文档站点，使其与 Python、Rust、Go 等其他语言客户端的链接风格保持统一，提升了开发者浏览文档时的体验与一致性。
