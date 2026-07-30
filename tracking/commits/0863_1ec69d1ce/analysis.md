# 提交 0863：Docs: Allow Java 17 in contribute.md (#10545)

## 提交信息

- **序号**：0863 / 4088
- **哈希**：1ec69d1ce91fa32bd60df5aec8bf9238f5451ca8
- **短哈希**：1ec69d1ce
- **日期**：2024-06-20（Thu Jun 20 18:10:14 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Docs: Allow Java 17 in contribute.md (#10545)
- **PR/Issue**：#10545

## 总体目的

Iceberg 项目的 `README.md` 中已说明构建时支持 Java 17，但 `site/docs/contribute.md` 文档里"Building the Project Locally"一节仍只写了"Java 8 or Java 11"，没有把 Java 17 列入支持列表。两份文档之间用词不一致，会让贡献者误以为只能用 Java 8/11 构建项目，从而错误地排除 Java 17 这一事实上同样受支持的构建时 JVM 选项。

本提交的目的是同步 `contribute.md` 与 `README.md` 的措辞，把 Java 17 显式列入构建支持的 JVM 版本，让贡献者文档准确反映项目当前实际支持的构建环境，减少文档间的不一致与潜在困惑。

## 如何达成设计目的

实现方式非常直接：仅修改 `site/docs/contribute.md` 中"Building the Project Locally"小节的第一句话，把 `Java 8 or Java 11` 改为 `Java 8, 11, or 17`，与 `README.md` 中已有的措辞保持一致。无需改动任何代码、构建脚本或 CI 配置。

## 修改详情

### `site/docs/contribute.md`

**修改目的**：把贡献者文档中构建支持的 JVM 版本从 `Java 8 or Java 11` 更新为 `Java 8, 11, or 17`，与 `README.md` 用词对齐。

**工作逻辑**：仅修改一行 Markdown 文本，diff 如下：

```diff
 ## Building the Project Locally

-Iceberg is built using Gradle with Java 8 or Java 11.
+Iceberg is built using Gradle with Java 8, 11, or 17.
```

后续行（构建命令、跳过测试命令等）未变。

## 小结

- **成效**：消除了 `contribute.md` 与 `README.md` 关于构建时支持 JVM 版本的措辞不一致，让贡献者文档正确列出 Java 17 作为支持选项。
- **影响范围**：仅一个文档文件 `site/docs/contribute.md`，1 行改动，无任何代码、构建或配置变更，无功能影响。
- **回迁到 1.4.x 的注意事项**：本提交是纯文档措辞修正，**安全可回迁**到 1.4.x 分支，且建议回迁以保持 1.4.x 分支文档与 main 一致。回迁时需确认 1.4.x 分支的 `contribute.md` 在该位置文字未因其他改动而漂移；如果 1.4.x 分支的 `contribute.md` 已显著重构则需手动应用对应措辞。该改动不依赖任何其他提交。
