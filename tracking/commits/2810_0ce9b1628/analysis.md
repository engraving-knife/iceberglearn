# 提交 2810：infra: cleanup remove unused functions from site/ script (#14466)

## 提交信息

- **序号**：2810 / 4088
- **哈希**：0ce9b16280b9c496b97557b3bf7973d627b337bc
- **短哈希**：0ce9b1628
- **日期**：2025-11-01 16:35:13 -0700
- **作者**：Kevin Liu
- **提交说明**：infra: cleanup remove unused functions from site/ script (#14466)
- **PR/Issue**：#14466

## 总体目的

本提交属于基础设施清理工作。Iceberg 项目的站点文档构建脚本 `site/dev/common.sh` 中存在一些历史遗留的、已经不再被调用的辅助函数。这些死代码不仅增加了维护负担，还会让阅读者误以为这些函数仍在被使用，从而对脚本行为产生错误的理解。

随着站点构建流程的演进（例如在后续提交 2820、2824 中引入了 virtualenv 与更快的 lint 流程），原有的 `pull_remote`、`push_remote`、`search_exclude_versioned_docs` 等函数已经不再被任何脚本调用。开发者 Kevin Liu 在梳理站点脚本时发现这些未使用的函数，决定将其清理掉，以保持脚本的精简和准确。

这类清理是典型的"代码卫生"工作：移除死代码、降低维护成本、让脚本更聚焦于真正在使用的功能。

## 如何达成设计目的

设计思路非常直接：通过审阅 `site/dev/common.sh` 中定义的所有函数，找出在当前站点构建流程中没有被任何脚本调用的函数，然后将其整体删除（包括函数定义、注释和文档说明）。

本次清理共移除了三个函数：
1. `pull_remote`：用于从指定分支拉取远程更新
2. `push_remote`：用于将本地分支推送到远程
3. `search_exclude_versioned_docs`：用于将版本化文档从搜索索引中排除

删除后不影响脚本其他功能的正常运行，因为这些函数未被调用。

## 修改详情

### `site/dev/common.sh` (+0/-50 lines)

**修改目的**：移除三个未使用的函数定义，精简脚本。

**工作逻辑**：
- 删除 `pull_remote()` 函数（约16行）：原本用于执行 `git pull "${REMOTE}" "${BRANCH}"`，从指定分支拉取更新。包含参数校验（`assert_not_empty`）和日志输出。现已无调用方。
- 删除 `push_remote()` 函数（约16行）：原本用于执行 `git push "${REMOTE}" "${BRANCH}"`，推送本地变更到远程分支。同样包含参数校验和日志。现已无调用方。
- 删除 `search_exclude_versioned_docs()` 函数（约18行）：原本通过 Python 脚本修改版本化文档目录下的 `.md` 文件，在文件头部插入 `search:\n  exclude: true\n` 配置，使这些文档不被搜索引擎索引。该功能已不再需要。

这三个函数被完整删除，包括其上方的注释说明（如参数说明、功能描述等），保持代码整洁。

## 总结

本提交是纯粹的死代码清理，移除了 `site/dev/common.sh` 中三个未被调用的函数（`pull_remote`、`push_remote`、`search_exclude_versioned_docs`），共删除 50 行代码。这类清理有助于降低脚本维护成本，让站点构建脚本更加聚焦于实际使用的功能，避免阅读者产生误解。属于低风险的代码卫生改进。
