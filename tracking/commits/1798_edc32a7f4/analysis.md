# 提交 1798：Update release version to 1.8.1 in doap.rdf (#12408)

## 提交信息

- **序号**：1798 / 4088
- **哈希**：edc32a7f4f5b350364bc365d9bf68eba0c1d37e7
- **短哈希**：edc32a7f4
- **日期**：2025-02-28 08:59:58 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Update release version to 1.8.1 in doap.rdf (#12408)
- **PR/Issue**：#12408

## 总体目的

此提交用于在 Iceberg 仓库根目录的 `doap.rdf` 文件中更新项目的最新发布版本信息，从 1.8.0 更新为 1.8.1。

`doap.rdf`（Description of a Project，RDF 格式）是 Apache 项目用于向 Apache 项目基础设施（如 https://projects.apache.org/）描述项目元数据的文件，其中 `<release>` 段落声明项目的最新发布版本与发布日期。1.8.1 于 2025 年 2 月 28 日发布，因此需把该文件中的版本名、创建日期、修订号同步更新为 1.8.1，使 Apache 项目页面显示的最新版本正确。

这是发布流程中的元数据同步步骤，属于纯配置/元数据类修改。

## 如何达成设计目的

通过修改 `doap.rdf` 中 `<release>/<Version>` 下的三个字段达成目标：
- `<name>` 从 `1.8.0` 改为 `1.8.1`；
- `<created>` 从 `2025-02-13` 改为 `2025-02-28`（1.8.1 的发布日期）；
- `<revision>` 从 `1.8.0` 改为 `1.8.1`。

## 修改详情

### `doap.rdf`（修改, +3/-3 lines）

**修改目的**：将 DOAP 元数据中的最新发布版本更新为 1.8.1。

**工作逻辑**：文件中 `<release>` 段落原为：

```xml
<release>
  <Version>
    <name>1.8.0</name>
    <created>2025-02-13</created>
    <revision>1.8.0</revision>
  </Version>
</release>
```

修改后为：

```xml
<release>
  <Version>
    <name>1.8.1</name>
    <created>2025-02-28</created>
    <revision>1.8.1</revision>
  </Version>
</release>
```

注意 DOAP 通常只保留最新一个 release 记录，因此是替换而非追加。

## 小结

- **成效**：Apache 项目基础设施读取的 `doap.rdf` 元数据同步到 1.8.1，projects.apache.org 等页面显示的最新 Iceberg 版本与发布日期正确。
- **影响范围**：仅影响根目录 `doap.rdf` 一个元数据文件，不涉及代码、构建或测试。
- **回迁到 1.4.x 的注意事项**：这是针对 1.8.1 发布的元数据更新，**不建议回迁到 1.4.x 分支**。原因：1.4.x 分支的 `doap.rdf` 应反映 1.4.x 自身的最新发布版本，回迁 1.8.1 的版本信息会让 Apache 项目页面错误地显示 1.8.1 为该分支的最新版本。若 1.4.x 有自己的维护版本发布，应基于实际版本号单独更新 doap.rdf。
