# 提交 0939：Update references to `main` branch (#10705)

## 提交信息

- **序号**：0939 / 4088
- **哈希**：12f2e14c3b0d78b248cce3fdbd2741f0789a0065
- **短哈希**：12f2e14c3
- **日期**：2024-07-16（Tue Jul 16 12:33:45 2024 +0200）
- **作者**：Piotr Findeisen \<piotr.findeisen@gmail.com\>
- **提交说明**：Update references to `main` branch (#10705)
- **PR/Issue**：#10705

## 总体目的

本提交将 Iceberg 仓库若干文档中仍引用旧 `master` 分支名的地方更新为 `main`。Iceberg 仓库此前已完成从 `master` 到 `main` 的默认分支重命名，但部分文档（位于 `site/docs/` 下的网站文档）中的链接与示例仍残留 `master` 字样。这些残留会导致：

1. **链接失效或指向过时分支**：文档中以 `https://github.com/apache/iceberg/blob/master/...` 形式构造的链接，在 `master` 分支被重命名后可能无法正确解析（GitHub 通常会做重定向，但依赖重定向并非长期可靠，且影响阅读体验）。
2. **示例误导**：发布流程文档中以 `[master ca8bb7d0]` 形式展示的 git 提交示例，会让新贡献者误以为默认分支仍叫 `master`，在本地操作时产生困惑。
3. **与仓库实际状态不一致**：仓库默认分支已是 `main`，文档应与实际状态保持一致，避免混淆。

本次更新把残留的 `master` 引用统一改为 `main`，使文档与仓库实际分支命名一致。

## 如何达成设计目的

实现方式是逐个修改三处文档文件中涉及 `master` 的文本：

1. `site/docs/benchmarks.md`：把 JMH 基准测试 GH action 输入说明中的分支名示例 `master` 改为 `main`。
2. `site/docs/concepts/catalog.md`：把指向 REST Catalog OpenAPI 规范的 GitHub 链接中的 `blob/master/` 改为 `blob/main/`。
3. `site/docs/how-to-release.md`：把指向 `.asf.yaml` 的 GitHub 链接中的 `blob/master/` 改为 `blob/main/`，并把发布流程示例输出中的 `[master ca8bb7d0]` 改为 `[main ca8bb7d0]`。

均为纯文本替换，不涉及任何代码或构建逻辑。

## 修改详情

### `site/docs/benchmarks.md`

**修改目的**：将 JMH 基准测试说明中分支名示例从 `master` 更新为 `main`。

**工作逻辑**：单行文本替换：

```diff
-* The branch name to run benchmarks against, such as `master` or `my-cool-feature-branch`
+* The branch name to run benchmarks against, such as `main` or `my-cool-feature-branch`
```

这是描述 "JMH Benchmarks" GH action 输入的说明文本，告诉用户可以填写 `main` 或自定义特性分支名来运行基准测试。

### `site/docs/concepts/catalog.md`

**修改目的**：将指向 REST Catalog OpenAPI 规范的 GitHub 链接中的分支名从 `master` 改为 `main`。

**工作逻辑**：单行链接替换：

```diff
-... follows the [Iceberg REST Open API specification](https://github.com/apache/iceberg/blob/master/open-api/rest-catalog-open-api.yaml).
+... follows the [Iceberg REST Open API specification](https://github.com/apache/iceberg/blob/main/open-api/rest-catalog-open-api.yaml).
```

使链接直接指向 `main` 分支下的规范文件，避免依赖 GitHub 的 `master` → `main` 重定向。

### `site/docs/how-to-release.md`

**修改目的**：将发布流程文档中两处 `master` 引用更新为 `main`。

**工作逻辑**：两处替换：

```diff
-... collaborators in [.asf.yaml](https://github.com/apache/iceberg/blob/master/.asf.yaml)
+... collaborators in [.asf.yaml](https://github.com/apache/iceberg/blob/main/.asf.yaml)
```

```diff
-[master ca8bb7d0] Add version.txt for release 0.13.0
+[main ca8bb7d0] Add version.txt for release 0.13.0
```

第一处是 `.asf.yaml` 文件的 GitHub 链接分支名修正；第二处是发布流程示例控制台输出中的 git 提交摘要，把分支名 `master` 改为 `main`，与当前默认分支名一致。

## 小结

- **成效**：将 `site/docs/` 下三处文档中残留的 `master` 分支引用统一更新为 `main`，使文档与仓库实际默认分支名保持一致，避免链接失效与示例误导。
- **影响范围**：仅 `site/docs/benchmarks.md`、`site/docs/concepts/catalog.md`、`site/docs/how-to-release.md` 三个文档文件，共 4 处文本替换；不影响任何代码或构建。
- **回迁到 1.4.x 的注意事项**：这是纯文档更新，回迁到 1.4.x 完全无风险。是否回迁取决于 1.4.x 分支的 `site/docs/` 是否仍含 `master` 引用；若已含且默认分支已切换为 `main`，则建议回迁以保持文档准确。cherry-pick 不会产生任何冲突。
