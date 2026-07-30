# 提交 0583：Update site to 1.5.0 docs

## 提交信息

- **序号**：0583 / 4088
- **哈希**：dc5cac4ac42f79dd9a820d971fcc003210169711
- **短哈希**：dc5cac4ac
- **日期**：2024-03-11（Mon Mar 11 16:21:15 2024 -0500）
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Update site to 1.5.0 docs (#9931)
- **PR/Issue**：#9931

## 总体目的

Iceberg 官方文档站点（https://iceberg.apache.org/）使用 MkDocs（配合 mkdocs-multidocs-plugin 这类 include 机制）构建，支持同时托管多个版本的文档快照：一个 "latest"（指向当前开发分支的最新文档）和若干历史版本的归档文档。每发布一个新版本，都需要：

1. 把新版本的文档快照固化到 `site/docs/docs/<version>/` 目录下，生成对应的 `mkdocs.yml`；
2. 在站点级 `site/nav.yml` 中将该版本加入 "Docs" 导航列表，让用户可在版本选择器中切换；
3. 在站点级 `site/mkdocs.yml` 的 `extra.icebergVersion` 中把"当前最新版本号"更新为新版本，供文档模板中通过变量插值展示"最新版本"信息（如安装指引、依赖版本提示等）。

本提交的目的就是完成 1.5.0 正式发布（2024-03-11）后的站点文档切换工作：把 `icebergVersion` 从 `1.4.3` 更新为 `1.5.0`，并在导航里新增 1.5.0 文档入口，使官网能够正确展示 1.5.0 的文档与版本信息。

注：实际生成 `site/docs/docs/1.5.0/` 目录下的文档快照通常由发布脚本（release script）单独完成，本提交只做站点级配置的"启用"动作。

## 如何达成设计目的

实现思路是仅修改两个站点级 YAML 配置文件，不触碰文档正文内容：

1. **`site/mkdocs.yml`**：修改 `extra.icebergVersion` 自定义变量值。MkDocs 的 `extra` 配置项允许向模板暴露任意键值，文档主题（mkdocs-material）和 Markdown 文件中通过 `{{ config.extra.icebergVersion }}` 或类似插值语法引用该值。把值改为 `1.5.0` 后，所有引用该变量的页面（例如安装页提示"最新版本"、Java API 依赖版本提示、Quickstart 中的 `<iceberg.version>` 标签等）都会同步显示 1.5.0。

2. **`site/nav.yml`**：在 `Docs` 导航组的版本列表头部（紧跟 `latest` 之后、`1.4.3` 之前）插入一行 `1.5.0` 条目，通过 `!include docs/docs/1.5.0/mkdocs.yml` 引用 1.5.0 文档快照的 mkdocs 配置。MkDocs 的 include 机制会把子配置中的页面合并进主导航，形成版本选择器中的可点击入口。把 1.5.0 放在 `latest` 之后、`1.4.3` 之前，符合"新版本靠前"的版本排列约定。

## 修改详情

### `site/mkdocs.yml`

**修改目的**：将站点全局模板变量 `icebergVersion` 从 `1.4.3` 升级为 `1.5.0`，使所有引用该变量的文档页面（安装指引、Quickstart、版本提示等）同步显示 1.5.0 为最新版本。

**工作逻辑**：

```diff
 extra:
-  icebergVersion: '1.4.3'
+  icebergVersion: '1.5.0'
   social:
     - icon: fontawesome/regular/comments
       link: 'https://iceberg.apache.org/community/'
```

`extra` 是 MkDocs 用于向模板注入自定义变量的配置块。`icebergVersion` 是 Iceberg 自定义的变量，文档源文件和主题模板中通过该变量名引用最新版本号。修改值后，构建产物中所有出现"最新版本"字样的位置都会自动变为 `1.5.0`，无需逐页修改 Markdown，实现"一处改、处处生效"。

### `site/nav.yml`

**修改目的**：在文档站点的版本导航中新增 1.5.0 入口，使用户能在版本选择器中切换到 1.5.0 文档快照。

**工作逻辑**：

```diff
   - Docs:
     - latest: '!include docs/docs/latest/mkdocs.yml'
+    - 1.5.0: '!include docs/docs/1.5.0/mkdocs.yml'
     - 1.4.3: '!include docs/docs/1.4.3/mkdocs.yml'
     - 1.4.2: '!include docs/docs/1.4.2/mkdocs.yml'
     - 1.4.1: '!include docs/docs/1.4.1/mkdocs.yml'
```

- `Docs` 是顶级导航项之一，下面列出的每一项对应一个可切换的版本入口；
- `latest` 始终指向 main 分支最新构建的文档快照（开发中版本）；
- 新增的 `1.5.0` 项通过 `!include docs/docs/1.5.0/mkdocs.yml` 加载 1.5.0 版本固化的文档子配置，渲染为独立的 1.5.0 文档站点；
- 把 1.5.0 放在 `latest` 之后、`1.4.3` 之前，使版本选择器按"最新开发版 → 最新发布版 → 历史发布版（降序）"的顺序排列，符合用户查找习惯；
- 下方保留 1.4.3 / 1.4.2 / 1.4.1 等历史版本入口，保证旧版本用户仍能查阅对应文档。

## 小结

- **成效**：完成 1.5.0 发布后的站点文档切换工作——版本选择器新增 1.5.0 入口，全局模板变量 `icebergVersion` 同步为 1.5.0，所有引用该变量的页面（Quickstart、安装指引、依赖版本提示等）正确显示 1.5.0。
- **影响范围**：仅修改 `site/mkdocs.yml` 和 `site/nav.yml` 两个站点级配置文件，共 2 行改动（1 处修改 + 1 处新增）。不影响仓库的 Java/Spark/Flink 等运行时代码，仅影响文档站点构建产物。
- **回迁到 1.4.x 的注意事项**：**不应回迁到 1.4.x 分支**。原因有二：
  1. **`site/` 目录在 1.4.x 分支上不存在**（site 目录仅在 main 分支维护），cherry-pick 会因找不到目标文件而失败；
  2. 即使强行迁移，1.4.x 维护分支的文档站点应展示 1.4.x 系列版本（如 1.4.3、1.4.4），不应展示 1.5.0——把 1.5.0 文档入口加到 1.4.x 分支会误导用户认为 1.4.x 分支已发布 1.5.0，造成版本信息混乱。1.4.x 分支的文档发布应通过各自分支的 site 配置独立维护。
