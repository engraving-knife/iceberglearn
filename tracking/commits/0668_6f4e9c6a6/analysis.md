# 提交 0668：Docs: Update releases.md for Spark scala versions

## 提交信息

- **序号**：0668 / 4088
- **哈希**：6f4e9c6a68d9b8e344f78624b912d3a8efa7a1f7
- **短哈希**：6f4e9c6a6
- **日期**：2024-04-09 02:55:39 -0500
- **作者**：liko <liko9@users.noreply.github.com>
- **提交说明**：Docs: Update releases.md for Spark scala versions (#10104)
- **PR/Issue**：#10104

## 总体目的

本提交是一个**文档类改动**：在 Iceberg 发布说明页面（`site/docs/releases.md`）中，将 Spark runtime Jar 的下载链接格式从"合并显示两个 Scala 版本"改为"每个 Scala 版本单独一行"，使用户能更清晰地识别和下载对应 Scala 版本的 Jar 包。

### 背景

Iceberg 为 Spark 提供了针对不同 Scala 版本（2.12 和 2.13）编译的 runtime Jar 包。在本次提交之前，releases.md 中每个 Spark 版本（3.5、3.4、3.3）只列一行，用 `--` 分隔同时展示 2.12 和 2.13 两个版本的下载链接，例如：
```
* [{{ icebergVersion }} Spark 3.5\_2.12 runtime Jar](...) -- [3.5\_2.13](...)
```
这种格式存在两个问题：
1. **可读性差**：两个 Scala 版本的链接挤在一行，用户容易混淆。
2. **语义不清晰**：`--` 后面的 `3.5\_2.13` 链接没有完整的描述文字，不够直观。

本次提交将每个 Scala 版本拆分为独立的列表项，并在描述中明确标注 "with Scala 2.12" 或 "with Scala 2.13"。

## 如何达成设计目的

将每个 Spark 版本原来的一行（含两个 Scala 版本链接）拆分为两行（每个 Scala 版本一行），并在描述中添加 "with Scala 2.x" 字样以明确区分。修改覆盖 Spark 3.5、3.4、3.3 三个版本，共 3 行变为 6 行。

## 修改详情

### `site/docs/releases.md`

**修改目的**：改善 Spark runtime Jar 下载链接的可读性，明确区分不同 Scala 版本。

**工作逻辑**：

对 Spark 3.5、3.4、3.3 三个版本，各进行如下修改：

**修改前**（每个版本一行，两个 Scala 版本用 `--` 分隔）：
```
* [{{ icebergVersion }} Spark 3.5\_2.12 runtime Jar](url_2.12) -- [3.5\_2.13](url_2.13)
```

**修改后**（每个版本拆为两行，每行一个 Scala 版本）：
```
* [{{ icebergVersion }} Spark 3.5\_with Scala 2.12 runtime Jar](url_2.12)
* [{{ icebergVersion }} Spark 3.5\_with Scala 2.13 runtime Jar](url_2.13)
```

关键变化：
1. 每个 Scala 版本从共用一行变为独占一行，作为独立的列表项。
2. 描述文字从 `Spark 3.5\_2.12` 改为 `Spark 3.5\_with Scala 2.12`，使用 "with Scala" 更清晰地表达"Spark 3.5 配合 Scala 2.12"的语义。
3. 移除了 `--` 分隔符和缩写的 `[3.5\_2.13]` 链接文字，改为完整的描述。

Flink 版本的下载链接保持不变。

## 小结

- **成效**：成功改善了 Spark runtime Jar 下载页面的可读性，用户现在能更清晰地选择对应 Scala 版本的 Jar 包。
- **影响范围**：仅影响网站文档页面 `site/docs/releases.md`，不涉及任何代码修改。3 行变为 6 行（净增 3 行）。
- **回迁到 1.4.x 的注意事项**：纯文档改动，回迁无风险。直接 cherry-pick 即可。
