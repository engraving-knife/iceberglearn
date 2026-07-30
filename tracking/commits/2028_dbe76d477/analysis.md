# 提交 2028：Site: Add `format/` to site-ci (#12869)

## 提交信息

- **序号**：2028 / 4088
- **哈希**：dbe76d477dc9443a3063fc1b661bd6427a8d9a3b
- **短哈希**：dbe76d477
- **日期**：2025-04-22 14:46:44 -0500
- **作者**：Fokko Driesprong
- **提交说明**：Site: Add `format/` to site-ci (#12869)
- **PR/Issue**：#12869

## 总体目的

本提交将 `format/` 目录加入站点 CI（site-ci）GitHub Actions 工作流的触发路径，使得 `format/spec.md` 等规范文档变更时能自动触发站点部署。

站点 CI 工作流 `site-ci.yml` 配置了 `push` 事件的路径触发器（`paths`），此前仅包含 `docs/**` 和 `site/**`。Iceberg 的规范文档 `format/spec.md` 是站点内容的一部分（规范文档的变更如 2017、2020、2024 等提交会反映到站点），但 `format/**` 路径未在触发列表中，导致修改 `spec.md` 后站点不会自动重新部署。本提交补充该路径。

## 如何达成设计目的

在 `.github/workflows/site-ci.yml` 的 `paths` 列表中新增 `format/**`。

## 修改详情

### `.github/workflows/site-ci.yml` (修改, +1/-0 lines)

**修改目的**：让 spec.md 变更触发站点 CI。

**工作逻辑**：在 `push` 事件的 `paths` 列表中，于 `docs/**` 和 `site/**` 之后新增 `format/**`，使得 `format/` 目录下任何文件变更都会触发站点部署工作流。

## 总结

本提交在站点 CI 工作流的路径触发器中新增 `format/**`，确保 `format/spec.md` 等规范文档变更时自动触发站点重新部署。共 1 个文件、+1/-0 行，纯 CI 配置改进。
