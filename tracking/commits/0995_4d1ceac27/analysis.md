# 提交 0995：Docs: Use link addresses instead of descriptions in releases.md (#10815)

## 提交信息

- **序号**：0995 / 4088
- **哈希**：4d1ceac275859df0ab16a8f919a76fcd3b97d622
- **短哈希**：4d1ceac27
- **日期**：2024-07-30（Tue Jul 30 22:25:56 2024 +0100）
- **作者**：liu yang <lurnagaodahua@gmail.com>
- **提交说明**：Docs: Use link addresses instead of descriptions in releases.md (#10815)
- **PR/Issue**：#10815

## 总体目的

`site/docs/releases.md` 是 Iceberg 官网发布的版本发行说明文档，其中每条改动都以 markdown 链接形式引用对应的 GitHub PR，格式约定为 `[#编号](https://github.com/apache/iceberg/pull/编号)`。在 1.6.0 版本的条目中有两处链接的 URL 部分错误地写成了 PR 标题描述文本，而非标准的 PR 链接地址，导致渲染出的链接指向无效 URL（相对路径 `Expose table incremental scan for appends API in SerializableTable` 与 `Mark 502 and 504 statuses as retryable to the REST exponential retry strategy`）。

本提交修正这两处错误，把链接 URL 统一替换为正确的 `https://github.com/apache/iceberg/pull/<编号>` 地址，使发行说明中所有 PR 引用格式一致且可点击访问。

## 如何达成设计目的

直接编辑 `site/docs/releases.md`，定位两处错误链接并替换 URL 文本，不动链接显示文本与 PR 编号。这是纯文档修正，无逻辑改动。

## 修改详情

### `site/docs/releases.md`

**修改目的**：修复 1.6.0 发行说明中两处格式错误的 PR 链接。

**工作逻辑**：两处改动如下：

```diff
-    - Expose table incremental scan for appends API in SerializableTable ([\#10682](Expose table incremental scan for appends API in SerializableTable))
+    - Expose table incremental scan for appends API in SerializableTable ([\#10682](https://github.com/apache/iceberg/pull/10682))
...
-    - REST Catalog: Mark 502 and 504 statuses as retryable to the REST exponential retry strategy ([\#9885](Mark 502 and 504 statuses as retryable to the REST exponential retry strategy))
+    - REST Catalog: Mark 502 and 504 statuses as retryable to the REST exponential retry strategy ([\#9885](https://github.com/apache/iceberg/pull/9885))
```

即把 `#10682` 与 `#9885` 两条的链接 URL 由描述文本改为标准 PR 地址。

## 小结

- **成效**：修复了发行说明中两处无效的 PR 链接，使其指向正确的 GitHub PR 页面，格式与文档其余部分保持一致。
- **影响范围**：仅 `site/docs/releases.md` 一个文件，2 行改动，无代码或构建逻辑变更。
- **回迁到 1.4.x 的注意事项**：该提交针对 main 分支 1.6.0 发行说明的链接错误。1.4.x 维护分支的 `releases.md` 记录的是 1.4.x 系列发行说明，内容与 main 不同，且 1.6.0 条目不会出现在 1.4.x 分支上，因此**无需也不应回迁**。若 1.4.x 分支的 `releases.md` 中存在同类链接格式错误，应单独在该分支上修正，而非 cherry-pick 本提交。
