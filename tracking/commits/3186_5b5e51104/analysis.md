# 提交 3186：Build/Release: fix RAT command (#15194)

## 提交信息

- **序号**：3186 / 4088
- **哈希**：5b5e51104429ae02be770449370a02f9794ee99b
- **短哈希**：5b5e51104
- **日期**：2026-01-31
- **作者**：Kevin Liu
- **提交说明**：Build/Release: fix RAT command (#15194)
- **PR/Issue**：#15194

## 总体目的

本提交修复 Iceberg 发布流程中用于许可证审计的 RAT（Apache Release Audit Tool）命令。RAT 用于在发布前扫描整个仓库，确保所有需要 Apache 许可头的源文件都带有合规的 `LICENSE` 头，是 Apache 项目发布流程的关键一环，由 `dev/check-license` 脚本调用。

具体问题有两方面。其一，`dev/check-license` 脚本里 RAT 调用行的参数写法有误：`--input-exclude-std GIT IDEA MAC --` 末尾多了一个 `--`，这个本应作为"选项终止符"的 token 在被换行符 `\` 续行后接 `--input-include-std` 时会破坏参数解析，导致 RAT 命令行为不符合预期或直接报错。其二，仓库中的 `site/`（文档站点）和 `open-api/`（OpenAPI 生成）目录使用 `uv`（一个快速的 Python 包管理器）构建，`uv` 会在这些目录下创建 `.venv` 虚拟环境目录。由于 `.venv` 是隐藏目录，且默认情况下 RAT 不扫描隐藏目录，此前这些目录并不在审计范围内；但随着 RAT 配置策略调整（启用 `HIDDEN_DIR` 标准包含集，让隐藏目录也进入扫描），这些由 `uv` 拉取的第三方 Python 包目录会因缺少 Apache 许可头而被误报为违规，从而阻断发布。

本提交通过两处协同修改一并解决：既修掉了脚本里多余的 `--`，又显式启用对隐藏目录的扫描（`--input-include-std HIDDEN_DIR`），同时把 `.venv` 目录加入 `.rat-excludes` 排除清单，使得隐藏目录中本应被检查的文件得以纳入审计，而合法的第三方虚拟环境内容则被豁免。

## 如何达成设计目的

改动集中在 `dev/` 目录下两个文件：脚本 `dev/check-license` 修正 RAT 命令的参数构造（删除多余的 `--`、新增 `--input-include-std HIDDEN_DIR` 选项行），配置 `dev/.rat-excludes` 追加 `**/.venv/**` 排除模式。两者配合，先扩大扫描范围（纳入隐藏目录），再用排除清单精确剔除不需要审计的 `.venv`，达到既修复命令又避免误报的目的。

## 修改详情

### `dev/.rat-excludes` (+1/-0 lines)

**修改目的**：将 `uv` 创建的 Python 虚拟环境目录排除出 RAT 许可头审计范围。

**工作逻辑**：
在已有排除项（如 `sitemap.xml`、`**/derby.log`、`.python-version`、`**/*_index.md`）之后新增一行 `**/.venv/**`。该模式匹配仓库任意路径下的 `.venv` 目录及其全部内容。因为 `.venv` 内是 `uv` 拉取的第三方 Python 依赖，这些包按其各自许可证发布，并不需要携带 Apache 许可头，故需显式排除，避免在启用隐藏目录扫描后产生误报。

### `dev/check-license` (+1/-1 lines)

**修改目的**：修正 RAT 调用的参数，并启用对隐藏目录的审计。

**工作逻辑**：
原命令行为：
```
--input-exclude-std GIT IDEA MAC -- \
--output-style missing-headers \
```
改为：
```
--input-exclude-std GIT IDEA MAC \
--input-include-std HIDDEN_DIR \
--output-style missing-headers \
```
两个变化：（1）删掉 `--input-exclude-std GIT IDEA MAC` 之后多余的 `--`。该 `--` 是 RAT 参数列表中一个多余的 token，会干扰命令行解析（提交说明将其定性为"fix rat command"，即命令本身被破坏），移除后每个 `--input-*`/`--output-*` 选项各自独立成行、边界清晰。（2）新增 `--input-include-std HIDDEN_DIR`，即把 `HIDDEN_DIR` 加入"标准包含集"，使 RAT 跨过"默认跳过隐藏目录"的行为，转而扫描隐藏目录中的文件。配合 `.rat-excludes` 对 `**/.venv/**` 的排除，结果是隐藏目录中需要审计的文件被检查，而 `.venv` 这类合法的第三方内容被跳过。

## 总结

本提交修复了发布流程中 RAT 命令的参数错误，并通过"扩大扫描范围 + 精确排除"的策略，使许可证审计既能覆盖此前被默认跳过的隐藏目录，又不误伤 `site/`、`open-api/` 下由 `uv` 管理的 Python 虚拟环境。改动虽小，但直接关系到 Apache 发布合规检查能否通过，是发布链路上的必要修复。
