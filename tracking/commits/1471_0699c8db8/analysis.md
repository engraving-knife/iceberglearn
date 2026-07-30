# 提交 1471：Add C++ to the list of languages in `doap.rdf` (#11714)

## 提交信息

- **序号**：1471 / 4088
- **哈希**：0699c8db8579d7f12c188f9af2c2ab6e923fa84d
- **短哈希**：0699c8db8
- **日期**：2024-12-09（Mon Dec 9 14:07:38 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Add C++ to the list of languages in `doap.rdf` (#11714)
- **PR/Issue**：#11714

## 总体目的

`doap.rdf`（Description of a Project，RDF 格式）是 Apache 软件基金会（ASF）用于描述各顶级项目的元数据文件。其中 `<programming-language>` 元素列出项目所使用的编程语言，Apache 项目列表站点（`projects.apache.org`）会读取这些信息并在项目页面展示，同时用于按语言分类聚合各项目。

Iceberg 早期主要是一个 Java 项目，随着生态发展，社区陆续增加了 Python（pyiceberg）、Go、Rust 等语言的客户端实现。近年来 Iceberg 社区也在推进 C++ 客户端的工作，因此本提交将 `C++` 加入 `doap.rdf` 的编程语言列表，使 Apache 项目站点能正确反映 Iceberg 已支持 C++ 语言生态。提交说明中引用了 Apache 项目站点接受的语言值校验列表（`https://projects.apache.org/validation.json`），确认 `C++` 是合法的取值。

## 如何达成设计目的

直接编辑仓库根目录的 `doap.rdf` 文件，在 `<programming-language>` 元素列表中（已有 Java、Python、Go、Rust）追加一行 `<programming-language>C++</programming-language>`。这是纯元数据文件更新，无代码逻辑。

## 修改详情

### `doap.rdf`

**修改目的**：在项目描述文件的编程语言列表中新增 C++。

**工作逻辑**：在已有的 `<programming-language>` 元素列表末尾（Rust 之后、`<category>` 之前）追加一行：
```xml
     <programming-language>Python</programming-language>
     <programming-language>Go</programming-language>
     <programming-language>Rust</programming-language>
+    <programming-language>C++</programming-language>
     <category rdf:resource="https://projects.apache.org/category/big-data" />
```
这样 Iceberg 项目的编程语言列表变为：Java、Python、Go、Rust、C++。文件其余部分（项目名、描述、发布版本、仓库地址、分类等）保持不变。

## 小结

- **成效**：`doap.rdf` 的编程语言列表现包含 C++，Apache 项目列表站点将据此展示 Iceberg 支持 C++ 语言生态，提升了 C++ 客户端在 Iceberg 项目中的可见性。
- **影响范围**：仅 `doap.rdf` 一个文件，新增 1 行，无代码、构建或运行时逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是项目元数据文件更新，与产品版本功能无关，对 1.4.x 运行时无任何影响。`doap.rdf` 由 main 分支统一维护，1.4.x 分支**无需回迁**。即使 1.4.x 分支的 `doap.rdf` 不包含 C++，也不影响其发布产物或项目元数据的正确性（项目站点始终读取 main 分支的 `doap.rdf`）。
