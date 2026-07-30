# 提交 2969：Revert "Update configuration.md (#14771)" (#14780)

## 提交信息

- **序号**：2969 / 4088
- **哈希**：fc434997fbc63a3f1f47481c0878073b1ccf6359
- **短哈希**：fc434997f
- **日期**：2025-12-06
- **作者**：Manu Zhang
- **提交说明**：Revert "Update configuration.md (#14771)" (#14780)
- **PR/Issue**：#14780

## 总体目的

上一个提交（2968 / #14771）将 `docs/docs/configuration.md` 中 `format-version` 表项的规范文档链接从 `../../spec.md` 改为 `../../format/spec.md`，意图修复失效链接。但该改动随后被发现存在问题并被回退。从仓库文件结构看，根目录确实不存在 `spec.md` 而存在 `format/spec.md`，因此该路径在 GitHub 直接浏览时看似更"正确"；然而 `docs/docs/` 是 Iceberg 文档站点的源文档目录，其 Markdown 链接需要在站点渲染（mkdocs 构建）上下文中正确解析，而 #14771 的改动在站点渲染下反而破坏了链接。PR #14780 的讨论中维护者指出，这类链接问题本可通过本地运行 `make serve-dev`（见 `site/Makefile`）提前发现。本回退的目的是恢复回退前（即 `../../spec.md`）的状态，避免一个在站点渲染上下文下不正确的"修复"被合入，留待后续以更合适的方式统一处理 spec 链接。

## 如何达成设计目的

这是一个标准 `git revert`，把 #14771 对 `docs/docs/configuration.md` 的那一行链接改动原样反向恢复，不引入任何额外改动。

## 修改详情

### `docs/docs/configuration.md` (+1/-1 lines)

**修改目的**：回退 #14771 对 spec 链接的修改，恢复原始相对路径。

**工作逻辑**：
将 `format-version` 表项中的链接由 `[Spec](../../format/spec.md#format-versioning)` 还原为 `[Spec](../../spec.md#format-versioning)`，即恢复到 2968 之前的状态。锚点 `#format-versioning` 与其余描述文本不变。回退后该文件与 2968 提交之前完全一致。

## 总结

本提交回退了 #14771 对 `configuration.md` 中 `format-version` 表项 spec 链接的"修复"，恢复为原始的 `../../spec.md` 路径。原因在于该改动在文档站点渲染上下文下并不正确（维护者指出可借 `make serve-dev` 本地发现），说明文档相对链接的正确性取决于具体的渲染/构建上下文，不能仅凭仓库原始目录结构下结论。该回退避免了错误修复被合入，将 spec 链接的彻底修正留给后续更妥善的方案。
