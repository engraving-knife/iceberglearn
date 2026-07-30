# 提交 2055：Docs: Fix version doc release step (#12922)

## 提交信息

- **序号**：2055 / 4088
- **哈希**：5ed88a5db0ffaea3fdd836c375fdce8d420d5956
- **短哈希**：5ed88a5db
- **日期**：2025-04-29 15:43:31 +0200
- **作者**：Ajantha Bhat
- **提交说明**：Docs: Fix version doc release step (#12922)
- **PR/Issue**：#12922

## 总体目的

发布指南 `site/docs/how-to-release.md` 中关于"版本化文档（Versioned Docs）"和"版本化 Javadoc"的步骤在 1.9.0 发布实践中暴露出若干不准确与遗漏：原步骤假设版本化文档位于源码 tarball 的 `site/docs` 下，但实际版本化文档位于发布 tag 的 `docs/` 目录；缺少将拷贝过来的 `mkdocs.yml` 中站点名从 `docs/latest` 改为 `docs/<version>` 的提示；缺少生成 Javadoc 需 JDK 17+ 的提示；缺少发布后更新站点（站点 mkdocs.yml 中的版本号、文档链接、release notes）的步骤。

本提交修正发布指南，使后续 release manager 能按照正确的步骤产出 1.9.x 及以后版本的文档与站点。

## 如何达成设计目的

通过修改 `how-to-release.md` 中 `Versioned Docs` 与 `Versioned Javadoc` 两节，并新增 `Site update` 节，覆盖三方面改进：
1. 修正版本化文档的来源说明与拷贝命令（从 `iceberg/docs` 拷贝而非 `apache-iceberg-X.Y.Z/site/docs`）。
2. 补充关键注意事项：复制后需把 `mkdocs.yml` 站点名改为对应版本；生成 Javadoc 需 JDK 17+。
3. 新增发布后站点更新步骤，指向 PR #12242 作为参考范式。

## 修改详情

### `site/docs/how-to-release.md` (修改, +11/-3 lines)

**修改目的**：修正与补全版本化文档/Javadoc 的发布步骤。

**工作逻辑**：
- `Versioned Docs` 节：将描述改为"版本化文档位于发布 tag 的 `docs/` 目录"，给出 GitHub tag 链接示例；拷贝命令由 `cp -R apache-iceberg-1.8.0/site/docs 1.8.0` 改为 `cp -R iceberg/docs 1.8.0`；新增"注意：将复制后的 `mkdocs.yml` 中站点名从 `docs/latest` 改为 `docs/1.8.0`"。
- `Versioned Javadoc` 节：在 `./gradlew refreshJavadoc` 之前新增"注意：生成 Javadoc 需使用 JDK 17+"。
- 新增 `Site update` 节：指引按 PR #12242 的范式提交站点更新 PR，更新 Iceberg 版本号、新版本文档链接与 release notes。

## 总结

本提交是对发布文档的勘误与补全：修正版本化文档来源路径、补充 `mkdocs.yml` 站点名修改与 JDK 17+ 要求，并新增站点更新步骤。纯文档变更，不影响代码。
