# 提交 1793：Docs: Describe how to handle versioned docs/javadoc during a release (#12413)

## 提交信息

- **序号**：1793 / 4088
- **哈希**：7553b0f89ce47cfd1b47727785701405f78eaa2d
- **短哈希**：7553b0f89
- **日期**：2025-02-28 10:40:48 +0530
- **作者**：Eduard Tudenhoefner
- **提交说明**：Docs: Describe how to handle versioned docs/javadoc during a release (#12413)
- **PR/Issue**：#12413

## 总体目的

此提交用于完善 Iceberg 的发布流程文档 `site/docs/how-to-release.md`，主要做两件事：

1. **更新过时的版本号示例**：原文档中创建源码发布包、二进制发布包的示例命令和输出仍使用很老的 `0.13.0` 版本号，与当前 1.8.x 发布周期严重脱节，容易误导发布经理。本次将其统一更新为 `1.8.0`，使示例贴近实际发布场景。

2. **新增 “版本化文档与 Javadoc 处理” 章节**：发布新版本时，需要把对应版本的文档和 Javadoc 归档到 `iceberg` 仓库的 `docs` 和 `javadoc` 分支，供站点提供历史版本浏览。原文档缺少这部分操作说明，导致发布流程不完整。本次新增 “Versioned Docs” 与 “Versioned Javadoc” 两个小节，给出从源码包拷贝文档、用 `./gradlew refreshJavadoc` 生成 Javadoc、向对应分支提 PR 的完整步骤。

这是纯文档类修改，目的是让发布流程文档更准确、更完整。

## 如何达成设计目的

通过两类编辑达成目标：

1. **批量替换版本号**：将 `0.13.0`、`0.13.0-rc1` 等旧版本字符串替换为 `1.8.0`、`1.8.0-rc0`，涉及命令示例、控制台输出、SVN 路径、tarball 文件名、dist.apache.org URL 等多处。注意 RC 编号也从 `rc1` 改为 `rc0`（当前规范从 rc0 开始计数）。

2. **新增版本化文档/Javadoc 章节正文**：在 “site/ 目录说明” 段落之后、“How to Verify a Release” 章节之前，插入两个新的小节，分别说明版本化文档和版本化 Javadoc 的处理流程，并引用真实 PR（#12411、#12412）作为示例。

## 修改详情

### `site/docs/how-to-release.md`（修改, +47/-16 lines）

**修改目的**：更新发布文档的版本号示例并补充版本化文档/Javadoc 处理流程。

**工作逻辑**：

1. **版本号更新部分**（多处）：
   - 源码发布命令示例 `dev/source-release.sh -v 0.13.0 -r 0` → `-v 1.8.0 -r 0`；
   - 控制台输出示例中 `apache-iceberg-0.13.0-rc1` → `apache-iceberg-1.8.0-rc0`，`Add version.txt for release 0.13.0` → `... 1.8.0`，SVN 中 `tmp/apache-iceberg-0.13.0-rc1/...` 系列路径全部改为 `1.8.0-rc0`；
   - tarball 解压示例 `tar xzf apache-iceberg-0.13.0.tar.gz` / `cd apache-iceberg-0.13.0` → `1.8.0`；
   - dist.apache.org 发布候选 URL 同步更新。

2. **新增 “Versioned Docs” 小节**：
   - 说明源码包中的版本化文档位于 `site/docs`；
   - 操作步骤：检出 `iceberg` 仓库的 `docs` 分支，执行 `cp -R apache-iceberg-1.8.0/site/docs 1.8.0` 把文档拷贝到以版本号命名的目录；
   - 提交 PR 到 `docs` 分支，参考 PR #12411。

3. **新增 “Versioned Javadoc” 小节**：
   - 进入源码包目录执行 `./gradlew refreshJavadoc` 生成 Javadoc，输出位于 `site/docs/javadoc/1.8.0`；
   - 检出 `iceberg` 仓库的 `javadoc` 分支，执行 `cp -R apache-iceberg-1.8.0/site/docs/javadoc/1.8.0 1.8.0`；
   - 提交 PR 到 `javadoc` 分支，参考 PR #12412。

## 小结

- **成效**：发布流程文档的版本号示例与当前 1.8.x 周期一致，并补全了版本化文档与 Javadoc 的归档操作说明，使发布经理能按文档完整完成站点资源发布。
- **影响范围**：仅影响站点文档 `site/docs/how-to-release.md`，不涉及代码、构建脚本或测试。
- **回迁到 1.4.x 的注意事项**：纯文档修复，无前置依赖，回迁无风险。但需注意：文档中版本号示例为 `1.8.0`，若回迁到 1.4.x 分支，可能需要把示例版本号调整为与 1.4.x 周期相符的版本（如 `1.4.x` 对应的发布版本），否则示例与分支版本不符。版本化文档/Javadoc 的操作流程本身通用，可直接套用。
