# 提交 1149：Docs: Update Project links to include contributing and REST spec (#11114)

## 提交信息

- **序号**：1149
- **哈希**：8b4b2c1975ab2c46663e84a9cfbe3a254e172054
- **短哈希**：8b4b2c197
- **日期**：2024-09-11（Wed Sep 11 23:54:10 2024 -0700）
- **作者**：Daniel Weeks <dweeks@apache.org>
- **提交说明**：Docs: Update Project links to include contributing and REST spec (#11114)
- **PR/Issue**：#11114

## 总体目的

Iceberg 官方文档站（基于 mkdocs，导航配置在 `site/nav.yml`）的"Project"导航区原本只有 Community、Spec、View spec、Puffin spec、AES GCM Stream spec 等链接。社区希望提升两件事的可发现性：

1. **贡献指南**：contribute.md 文档早已存在，但未在导航中露出，新贡献者不易发现如何参与项目。
2. **REST Catalog 规范**：仓库中维护了完整的 OpenAPI 规范文件 `open-api/rest-catalog-open-api.yaml`，但文档站未提供直观入口，使用者只能自行到仓库查找。社区希望直接以 Swagger Editor 在线渲染该 YAML，让使用者能交互式浏览 REST API。

本提交在 `site/nav.yml` 的 Project 区段新增"Contributing"与"REST Catalog Spec"两个导航条目，并把原"Spec"重命名为"Table Spec"以与新增的 REST Catalog Spec 区分。

## 如何达成设计目的

通过编辑 mkdocs 导航配置 `site/nav.yml` 完成：

1. 在 `Project:` 区段、`Community: community.md` 之后插入两行：
   - `Contributing: contribute.md` —— 指向已有的贡献指南文档；
   - `REST Catalog Spec: <Swagger Editor URL>` —— 指向 Swagger Editor 在线渲染 main 分支的 `rest-catalog-open-api.yaml`，URL 为 `https://editor-next.swagger.io/?url=https://raw.githubusercontent.com/apache/iceberg/main/open-api/rest-catalog-open-api.yaml`。
2. 将原 `Spec: spec.md` 重命名为 `Table Spec: spec.md`，让"Spec"特指表规范，避免与新增的 REST Catalog Spec 混淆。

这是纯导航配置改动，无任何代码或文档内容变更。

## 修改详情

### `site/nav.yml`

**修改目的**：在 Project 导航区新增 Contributing 与 REST Catalog Spec 链接，并把原 Spec 改名为 Table Spec。

**工作逻辑**：

修改前的 `Project` 区段：
```yaml
  - Project:
    - Community: community.md
    - Spec: spec.md
    - View spec: view-spec.md
    - Puffin spec: puffin-spec.md
    - AES GCM Stream spec: gcm-stream-spec.md
    ...
```

修改后：
```yaml
  - Project:
    - Community: community.md
    - Contributing: contribute.md
    - REST Catalog Spec: https://editor-next.swagger.io/?url=https://raw.githubusercontent.com/apache/iceberg/main/open-api/rest-catalog-open-api.yaml
    - Table Spec: spec.md
    - View spec: view-spec.md
    - Puffin spec: puffin-spec.md
    - AES GCM Stream spec: gcm-stream-spec.md
    ...
```

要点：
- **`Contributing: contribute.md`** 指向仓库内已有的 `contribute.md` 文档（mkdocs 会渲染该 md 文件）；
- **`REST Catalog Spec`** 直接使用外链 URL，mkdocs 会作为外部链接打开。URL 由两部分组成：
  - `https://editor-next.swagger.io/` —— Swagger Editor 在线版（next 版本，支持 OpenAPI 3.x）；
  - `?url=https://raw.githubusercontent.com/apache/iceberg/main/open-api/rest-catalog-open-api.yaml` —— 通过 query 参数让 Swagger Editor 直接加载 main 分支的 YAML 文件，无需用户手动粘贴。
- **`Spec` → `Table Spec`** 仅改导航显示文本，目标文件 `spec.md` 不变，避免与新增的 REST Catalog Spec 在名称上混淆。

## 小结

- **成效**：文档站 Project 导航区新增"Contributing"与"REST Catalog Spec"两个入口，提升贡献指南与 REST Catalog 规范的可发现性；同时把原"Spec"改名为"Table Spec"以避免歧义。贡献者现在能直接从导航进入贡献指南，使用者能直接在 Swagger Editor 中交互式浏览最新的 REST Catalog OpenAPI 规范。
- **影响范围**：仅 `site/nav.yml` 一个文件，+3/-1 行。无任何代码或文档内容变更，纯导航配置。
- **回迁到 1.4.x 的注意事项**：
  - 这是文档站导航配置改进，与产品版本功能无关。
  - 1.4.x 作为维护分支，其文档站通常使用独立的 `nav.yml`，**不需要也无法回迁**——文档站导航由 main 分支统一维护并作用于整个 iceberg.apache.org 站点，1.4.x 的发布产物（jar 包）不依赖此配置。
  - 注意 `REST Catalog Spec` 链接指向的是 main 分支的 YAML（`raw.githubusercontent.com/apache/iceberg/main/...`），这是有意为之——规范是"living document"，始终展示最新版本。1.4.x 的发布版本若有独立的规范分支，可考虑在 1.4.x 文档站额外加一条指向 1.4.x tag 的链接，但通常不必要。
  - `contribute.md` 文档需确认在 1.4.x 中已存在；若不存在，回迁此导航条目会指向 404。但同样，1.4.x 一般不单独维护文档站导航。
