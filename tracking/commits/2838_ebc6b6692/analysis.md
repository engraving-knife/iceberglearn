# 提交 2838：Docs: 2.13 scala runtime addition to multi-engine support page (#14288)

## 提交信息

- **序号**：2838 / 4088
- **哈希**：ebc6b669293d5e10979adf2ac8f50c6ec604634b
- **短哈希**：ebc6b6692
- **日期**：2025-11-06 08:53:06 -0800
- **作者**：Ron Kapoor（与 Kevin Liu 共同作者）
- **提交说明**：Docs: 2.13 scala runtime addition to multi-engine support page (#14288)
- **PR/Issue**：#14288

## 总体目的

Iceberg 的"多引擎支持"文档页（`multi-engine-support.md`）以表格形式列出各 Spark 版本对应的 `iceberg-spark-runtime` jar 下载链接。原本 Spark 3.2/3.3/3.4/3.5 行只给出了 Scala 2.12 版本的 runtime jar 链接，但 Iceberg 实际上也为这些 Spark 版本发布了 Scala 2.13 版本的 runtime jar。

用户若使用 Scala 2.13 编译的 Spark，从该表格找不到对应的 jar 链接，造成困惑。该提交为 Spark 3.2/3.3/3.4/3.5 四行补充 Scala 2.13 的 runtime jar 下载链接，使文档与实际发布物对齐。Spark 4.0 行原本就只列 2.13（因 Spark 4.0 仅支持 2.13），无需改动。

## 如何达成设计目的

1. 在 `multi-engine-support.md` 的 Spark runtime 表中，对 3.2/3.3/3.4/3.5 四行的"runtime jar"单元格，在原 2.12 链接后追加一个 2.13 链接，两个链接以逗号分隔。
2. 链接 URL 形如 `https://search.maven.org/remotecontent?filepath=org/apache/iceberg/iceberg-spark-runtime-<spark>_<scala>/<version>/iceberg-spark-runtime-<spark>_<scala>-<version>.jar`，其中 3.3 与 3.4 的 2.13 链接用了 `repo1.maven.org` 域名（可能与该 jar 在 search.maven.org 上的可用性有关），其余沿用 search.maven.org。
3. 顺手把 3.4 行的列对齐空格修正（`1.10.0   |` → `1.10.0                 |`），让表格更整齐。

## 修改详情

### `site/docs/multi-engine-support.md` (+4/-4 lines)

**修改目的**：补充 Scala 2.13 runtime jar 链接并修正对齐。

**工作逻辑**：
- Spark 3.2 行：在 2.12 链接后追加 `, [iceberg-spark-runtime-3.2_2.13](.../1.4.3/iceberg-spark-runtime-3.2_2.13-1.4.3.jar)`。
- Spark 3.3 行：追加 2.13 链接（使用 `repo1.maven.org`）。
- Spark 3.4 行：追加 2.13 链接（使用 `repo1.maven.org`），并修正版本号列对齐。
- Spark 3.5 行：追加 2.13 链接，URL 中版本号用 `{{ icebergVersion }}` 占位（与 2.12 链接一致，由 mkdocs 在构建时替换为当前版本）。
- Spark 4.0 行不变（本就只有 2.13）。

## 总结

该提交是纯文档修改，为 multi-engine support 页面的 Spark 3.2/3.3/3.4/3.5 行补充 Scala 2.13 runtime jar 下载链接，使文档完整反映 Iceberg 实际发布的 Scala 2.13 制品，方便使用 Scala 2.13 Spark 的用户找到正确依赖。同时修正了一处表格对齐。
