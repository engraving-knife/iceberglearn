# 提交 0379：Set `ghp_path` to `/` (#9493)

## 提交信息

- **序号**：0379
- **哈希**：66b1aa662761606d4d68d99371c62505e7ac2f1e
- **短哈希**：66b1aa662
- **日期**：2024-01-17 14:52:46 +0100
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Set `ghp_path` to `/` (#9493)
- **PR/Issue**：#9493

## 总体目的

这个提交修正了 Iceberg 仓库 `.asf.yaml` 中 GitHub Pages 的发布路径配置，把 `ghp_path` 从 `~`（YAML 的 null）改为 `/`（根路径），目的是让 ASF（Apache Software Foundation）的 GitHub Pages 自动发布管线能够正确识别站点根目录，避免因 `null` 值导致 Pages 站点无法正常发布或发布到错误路径。

`.asf.yaml` 是 Apache 软件基金会专属的仓库配置文件，由 ASF 的 `ghpub` 服务消费，用来在不直接调用 GitHub API 的前提下管理仓库的描述、标签、协作者、GitHub Pages 等设置。其中 `github:` 区块下的 `ghp_branch` 指定 Pages 的来源分支，`ghp_path` 指定该分支下作为站点根的子路径。

此前配置为 `ghp_path: ~`，在 YAML 中 `~` 表示 null。把发布路径设为 null 在语义上是模糊的：既不是根路径字符串 `/`，也不是任何具体子目录。ASF 的发布管线在解析 null 时可能行为不确定——可能被忽略、可能报错、也可能发布到非预期位置。改为显式的 `/` 后，意图明确无误：把 `gh-pages` 分支的根目录作为站点根发布。这与 `ghp_branch: gh-pages` 的设定配合，构成了"从 `gh-pages` 分支根目录发布 Pages 站点"的完整、无歧义配置。

这类基础设施配置看似微小，但直接关系到项目文档/官网能否在线上正常可见，是项目对外形象的入口。

## 如何达成设计目的

把 `.asf.yaml` 中 `github.ghp_path` 的值从 `~` 改为 `/`，用显式的根路径字符串替代语义模糊的 null。改动仅一行，且只动配置不动代码。

## 修改详情

### `.asf.yaml`

**修改目的**：明确 GitHub Pages 的发布路径为 `gh-pages` 分支的根目录，避免 `null` 值导致发布管线行为不确定。

**工作逻辑**：

在 `github:` 区块中，将：

```yaml
  ghp_branch: gh-pages
  ghp_path: ~
```

改为：

```yaml
  ghp_branch: gh-pages
  ghp_path: /
```

`ghp_branch: gh-pages` 保持不变，仍指定 `gh-pages` 分支作为 Pages 内容来源。`ghp_path` 由 `~`（null）改为 `/`，显式声明站点根为分支根目录。配合起来即：从 `gh-pages` 分支的根目录发布 GitHub Pages 站点。`/` 是一个合法、明确、无歧义的路径值，ASF 的 `ghpub` 管线可以稳定解析，不再依赖对 null 的隐式处理。

## 小结

一个针对 ASF 仓库发布基础设施的单行配置修复。把 GitHub Pages 发布路径从语义模糊的 null 改为明确的根路径 `/`，确保项目 Pages 站点稳定、可预期地发布。这类"配置正确性"修复虽然小，但往往是官网/文档能否在线上正常可见的关键，属于项目基础设施卫生的一部分。
