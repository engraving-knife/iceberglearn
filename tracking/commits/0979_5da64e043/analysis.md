# 提交 0979：Update iceberg version on site to 1.6.0 (#10783)

## 提交信息

- **序号**：0979 / 4088
- **哈希**：5da64e043377636202388773869e43f5b347cb39
- **短哈希**：5da64e043
- **日期**：2024-07-25 08:54:41 -0600
- **作者**：JB Onofré
- **提交说明**：Update iceberg version on site to 1.6.0 (#10783)
- **PR/Issue**：#10783

## 总体目的

Iceberg 官方文档站点（基于 MkDocs 构建）通过 `site/mkdocs.yml` 中的 `extra` 变量向所有页面暴露版本号常量（如 `icebergVersion`、`nessieVersion`），用于在文档各处统一展示"当前推荐版本"。站点同时通过 `site/nav.yml` 维护多版本文档的导航下拉列表（nightly、latest、1.5.2、1.5.1、1.5.0 等），供用户切换查看不同版本的文档快照。

1.6.0 发布后，需要：

1. 将站点全局变量 `icebergVersion` 从 1.5.2 更新为 1.6.0，使文档各处展示的"推荐版本"同步；
2. 顺带将 `nessieVersion` 从 0.77.1 更新为 0.92.1（与 1.6.0 的依赖升级清单一致）；
3. 在导航中新增 1.6.0 文档入口，使用户能浏览 1.6.0 版本的文档快照。

本提交与 0977 号提交（发布 1.6.0）配套，是发布流程中文档站点层面的收尾。

## 如何达成设计目的

直接编辑 `site/mkdocs.yml` 和 `site/nav.yml` 两个文件：在 mkdocs.yml 的 `extra` 段更新两个版本变量；在 nav.yml 的 Docs 导航列表中，在 `latest` 与 `1.5.2` 之间插入 `1.6.0` 条目，指向 `docs/docs/1.6.0/mkdocs.yml`。

## 修改详情

### `site/mkdocs.yml`

**修改目的**：更新站点全局版本变量至 1.6.0。

**工作逻辑**：在 `extra` 段中：

```diff
-  icebergVersion: '1.5.2'
-  nessieVersion: '0.77.1'
+  icebergVersion: '1.6.0'
+  nessieVersion: '0.92.1'
```

`icebergVersion` 由 1.5.2 升至 1.6.0；`nessieVersion` 由 0.77.1 升至 0.92.1（与 1.6.0 发布说明中 "Bump Nessie to 0.92.1" 一致）。其余变量（flinkVersion 等）不变。

### `site/nav.yml`

**修改目的**：在文档导航中新增 1.6.0 版本入口。

**工作逻辑**：在 Docs 导航列表中，`latest` 条目之后、`1.5.2` 条目之前新增一行：

```diff
     - latest: '!include docs/docs/latest/mkdocs.yml'
+    - 1.6.0: '!include docs/docs/1.6.0/mkdocs.yml'
     - 1.5.2: '!include docs/docs/1.5.2/mkdocs.yml'
```

该条目通过 `!include` 引用 1.6.0 版本的 mkdocs 配置，使站点生成 1.6.0 文档快照导航。

## 小结

- **成效**：文档站点的全局版本变量更新为 1.6.0（含 Nessie 0.92.1），并在多版本文档导航中新增 1.6.0 入口，完成 1.6.0 发布后站点层面的同步。
- **影响范围**：仅 `site/mkdocs.yml`、`site/nav.yml` 两个站点配置文件，3 行新增、2 行修改。无代码影响。
- **回迁到 1.4.x 的注意事项**：该提交属于 main 分支 1.6.0 发布流程，**不应回迁到 1.4.x 分支**。1.4.x 维护分支的站点配置应登记 1.4.x 系列版本，回迁会造成站点版本展示混乱。
