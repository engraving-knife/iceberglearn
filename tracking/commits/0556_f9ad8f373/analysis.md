# 提交 0556：Docs: Fix image on spec

## 提交信息

- **序号**：0556 / 4088
- **哈希**：f9ad8f3733fb7d6128b4f11705f89ae5d07b14eb
- **短哈希**：f9ad8f373
- **日期**：2024-03-02 13:03:35 -0800
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Docs: Fix image on spec (#9843)
- **PR/Issue**：#9843

## 总体目的

修复 Iceberg 规范文档（`format/spec.md`）中引用图片的相对路径错误。规范文档中 Overview 部分展示 Iceberg 快照结构示意图（`iceberg-metadata.png`）的链接路径不正确，导致图片无法正常显示。本提交通过更正图片路径，使规范文档在 ASF 站点及 GitHub 上均能正确渲染该示意图，保证文档可读性与准确性。

## 如何达成设计目的

通过将原本指向仓库根目录之上 `../../../img/` 的相对路径，调整为指向仓库内部的 `assets/images/` 路径，使图片资源定位符合 ASF 站点构建约定。新路径相对于 `format/spec.md` 解析为 `format/assets/images/iceberg-metadata.png`，符合项目静态资源存放规范，避免了跨目录越界引用导致在站点构建时资源找不到的问题。

## 修改详情

### `format/spec.md`

**修改目的**：修复 Overview 段落中 Iceberg 快照结构示意图的 Markdown 图片引用路径。

**工作逻辑**：仅修改一行 Markdown 图片语法。原写法为：

```
![Iceberg snapshot structure](../../../img/iceberg-metadata.png)
```

`../../../img/` 这种向上回溯三级目录的写法，在 GitHub 渲染、ASF Hugo 站点构建以及文档单独导出等场景下都无法正确解析到实际资源文件。修改后写法为：

```
![Iceberg snapshot structure](assets/images/iceberg-metadata.png)
```

新路径基于 `format/spec.md` 所在目录解析，指向 `format/assets/images/iceberg-metadata.png`，与 ASF 站点静态资源组织方式一致，确保图片在多种渲染环境下都能正确展示。

## 小结

- 本提交是一个文档维护性修复，单行改动，仅涉及 `format/spec.md` 一个文件。
- 影响范围：仅文档渲染，不涉及任何代码逻辑、API 或构建产物功能。
- 回迁到 1.4.x 的注意事项：1.4.x 分支若同样维护有规范文档，需要确认该图片资源是否同样以 `assets/images/` 形式存放；若 1.4.x 的目录结构不同，应验证路径相对解析是否正确。该修复无副作用，可安全回迁。
