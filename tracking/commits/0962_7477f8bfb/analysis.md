# 提交 0962：API, Build: Fix typo in comments in `Table` and `gradlew` (#10744)

## 提交信息

- **序号**：0962 / 4088
- **哈希**：7477f8bfbcf9cded7129bbf8ca7e91bcaa81c1ae
- **短哈希**：7477f8bfb
- **日期**：2024-07-22 10:36:05 -0600
- **作者**：dongwang
- **提交说明**：API, Build: Fix typo in comments in `Table` and `gradlew` (#10744)
- **PR/Issue**：#10744

## 总体目的

本提交修复仓库中两处注释里的拼写/笔误问题，属于纯文档注释类改动，不涉及任何运行时逻辑。第一处位于 `api` 模块的核心接口 `Table.java`：其中两个 Javadoc 注释把对应 API 的行为描述得不够准确——把"删除文件（delete files）"的 API 写成了"替换文件（replace files）"，把"过期快照（expire snapshots）"的 API 写成了"管理快照（manage snapshots）"。这会误导阅读 Javadoc 的开发者，使其对 `newDelete()` 和 `expireSnapshots()` 的实际语义产生误解。

第二处位于 Gradle 包装器脚本 `gradlew` 的注释中：原注释列出"不允许包含 shell 片段"的变量时把 `JAVA_OPTS` 重复写了两遍（`DEFAULT_JVM_OPTS, JAVA_OPTS, JAVA_OPTS, and optsEnvironmentVar`），明显是笔误，应为 `DEFAULT_JVM_OPTS, JAVA_OPTS, and optsEnvironmentVar`。

这类小改动虽不影响功能，但属于开源协作中的常见清理工作，可以提升文档准确性，避免后续读者被误导。

## 如何达成设计目的

实现思路非常直接：逐字定位注释中表述错误的词组，替换为与对应 API 实际语义一致的措辞；对 `gradlew` 注释则删除多余重复的 `JAVA_OPTS` 项。不修改任何代码逻辑、不调整任何接口签名，仅限于注释文本。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Table.java`

**修改目的**：修正 `Table` 接口中两处 Javadoc 注释，使其准确描述对应 API 的行为。

**工作逻辑**：

1. 在 `newDelete()` 方法的 Javadoc 中，将 "to replace files in this table and commit" 改为 "to delete files in this table and commit"。`DeleteFiles` API 的语义本就是删除文件，原注释写成 "replace" 与接口名 `DeleteFiles` 不符，容易让读者误以为这是替换文件的操作。

2. 在 `expireSnapshots()` 方法的 Javadoc 中，将 "to manage snapshots in this table and commit" 改为 "to expire snapshots in this table and commit"。`ExpireSnapshots` 接口的作用是过期快照，原注释用过于宽泛的 "manage" 一词，没有准确传达"过期"这一具体动作。

### `gradlew`

**修改目的**：修正 Gradle 包装器脚本注释中重复列出 `JAVA_OPTS` 的笔误。

**工作逻辑**：原注释为 `DEFAULT_JVM_OPTS, JAVA_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments`，其中 `JAVA_OPTS` 出现两次。删除其中一次，得到 `DEFAULT_JVM_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments`。此处 `gradlew` 脚本由 Gradle 发行版自带，本提交仅同步修正该注释，未对脚本逻辑做任何改动。

## 小结

- **成效**：修正了 `Table.java` 中两处易引起误解的 Javadoc 表述以及 `gradlew` 脚本注释中的重复词，使文档与代码实际语义保持一致。
- **影响范围**：仅涉及两个文件的注释：`api/src/main/java/org/apache/iceberg/Table.java` 与 `gradlew`，无任何功能代码或构建逻辑变化。
- **回迁到 1.4.x 的注意事项**：此类纯注释修正无任何风险，**完全可以也推荐回迁到 1.4.x 分支**，有助于维护分支上文档的准确性。需注意 1.4.x 分支上 `Table.java` 的行号/上下文可能略有差异，cherry-pick 时如有冲突可手动应用文本替换。
