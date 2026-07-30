# 提交 1437：Doc: Fix some Javadoc URLs. (#11666)

## 提交信息

- **序号**：1437 / 4088
- **哈希**：e9f24f896e4d5fd1e87831f7b95df7b343d0f693
- **短哈希**：e9f24f896
- **日期**：2024-11-27（Wed Nov 27 20:48:10 2024 +0800）
- **作者**：Yujiang Zhong <42907416+zhongyujiang@users.noreply.github.com>
- **提交说明**：Doc: Fix some Javadoc URLs. (#11666)
- **PR/Issue**：#11666
- **作用模块**：`format/spec.md`（Iceberg 规范文档）

## 总体目的

Iceberg 的 `format/spec.md` 是 Iceberg 表格式规范文档，其中包含指向各版本 Javadoc 的链接，链接中使用了模板变量 `{{ icebergVersion }}`（由网站构建时替换为具体版本），用以指向当前版本的 Javadoc 页面。这些链接原先使用了 `index.html?org/apache/iceberg/...` 的查询参数形式（即 `index.html?<全限定类名>`）。

这种 `index.html?<query>` 形式在新版 Javadoc（Javadoc 工具生成的现代站点）中并不是正确的解析路径——现代 Javadoc 的成员页面是按包路径组织的静态文件，正确的链接应该是直接路径形式 `org/apache/iceberg/<类名>.html`，而非通过 `index.html` 加查询参数。原先的链接形式会导致点击后跳转到 Javadoc 入口页或无法精确定位到目标类。

本提交修复两处此类 Javadoc 链接，将其改为正确的直接路径形式，提升文档可用性。

## 如何达成设计目的

直接修改 `format/spec.md` 中两处链接，将 `index.html?org/apache/iceberg/<Class>.html` 改为 `org/apache/iceberg/<Class>.html`（去掉 `index.html?` 前缀，保留 `{{ icebergVersion }}` 与全限定路径后缀 `.html`）。这是最小化的文档修复，不涉及规范内容、代码或构建逻辑。

## 修改详情

### `format/spec.md`

**修改目的**：修复两处指向 `HadoopTableOperations` 与 `BaseMetastoreTableOperations` 的 Javadoc 链接。

**工作逻辑**：

第 981 行附近（File System Tables 章节的 Notes）：
```markdown
-1. The file system table scheme is implemented in [HadoopTableOperations](../javadoc/{{ icebergVersion }}/index.html?org/apache/iceberg/hadoop/HadoopTableOperations.html).
+1. The file system table scheme is implemented in [HadoopTableOperations](../javadoc/{{ icebergVersion }}/org/apache/iceberg/hadoop/HadoopTableOperations.html).
```

第 997 行附近（Metastore Tables 章节的 Notes）：
```markdown
-1. The metastore table scheme is partly implemented in [BaseMetastoreTableOperations](../javadoc/{{ icebergVersion }}/index.html?org/apache/iceberg/BaseMetastoreTableOperations.html).
+1. The metastore table scheme is partly implemented in [BaseMetastoreTableOperations](../javadoc/{{ icebergVersion }}/org/apache/iceberg/BaseMetastoreTableOperations.html).
```

两处改动一致：去掉 `index.html?` 前缀，使链接直接指向 `org/apache/iceberg/...` 路径下的类页面。共 2 行改动（2 增 2 删）。

## 小结

- **成效**：修复 `format/spec.md` 中两处 Javadoc 链接（`HadoopTableOperations`、`BaseMetastoreTableOperations`），从错误的 `index.html?<path>` 查询参数形式改为正确的直接路径形式，使链接能精确指向目标类页面。
- **影响范围**：仅 `format/spec.md` 一个文件，2 行改动；纯文档修复，无代码、构建或测试影响。
- **回迁到 1.4.x 的注意事项**：这是文档链接修复，与产品版本功能无关。回迁价值较低（仅修复点击体验）。若 1.4.x 的 `format/spec.md` 也存在相同的错误链接形式，可一并修复；但即便不回迁也不影响 1.4.x 的运行时行为或发布产物。文档通常跟随 main 分支统一发布到网站，1.4.x 维护分支的 `spec.md` 不一定单独发布，回迁非必需。
