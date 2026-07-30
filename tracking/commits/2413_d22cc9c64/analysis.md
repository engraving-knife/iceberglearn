# 提交 2413：docs: add subpage for REST Catalog Spec in "Specification" (#13521)

## 提交信息

- **序号**：2413 / 4088
- **哈希**：d22cc9c6413c5637fcfad9deeabb66ed5f62fd01
- **短哈希**：d22cc9c64
- **日期**：2025-07-24 22:04:35 -0700
- **作者**：Kevin Liu
- **提交说明**：docs: add subpage for REST Catalog Spec in "Specification" (#13521)
- **PR/Issue**：#13521

## 总体目的

本提交为 Iceberg 文档网站的"Specification"（规范）部分添加了一个专门的 REST Catalog Spec 子页面。

在此之前，文档导航中 "REST Catalog Spec" 条目直接外链到 Swagger UI 编辑器（`https://editor-next.swagger.io/?url=...`），用户点击后会跳离 Iceberg 文档站点。这种体验不够理想：用户无法在文档站内获得 REST Catalog Spec 的概述和背景信息，直接跳转到 Swagger UI 也不够友好。

本提交创建了一个专门的文档页面 `rest-catalog-spec.md`，提供了 REST Catalog API 的概述、OpenAPI 规范链接、Swagger UI 交互式探索链接，以及 REST Catalog Protocol 的设计动机和优势说明。同时将导航中的外链替换为指向这个新文档页面的内链。

## 如何达成设计目的

1. 新建 `site/docs/rest-catalog-spec.md` 文档页面，包含 REST Catalog API 的简介和交互式探索链接
2. 修改 `site/nav.yml` 导航配置，将 "REST Catalog Spec" 从外链改为指向新的文档页面

## 修改详情

### `site/docs/rest-catalog-spec.md` (新文件，+42 lines)

**修改目的**：创建 REST Catalog Spec 的专属文档页面。

**工作逻辑**：该页面包含两个主要部分：
- **REST Catalog API Specification**：介绍 Iceberg 定义的基于 REST 的 Catalog API，提供 OpenAPI YAML 规范的 GitHub 链接和 Swagger UI 交互式探索链接
- **REST Catalog Protocol**：解释 REST Catalog 协议的设计动机——随着 Iceberg 支持越来越多的语言和引擎，可插拔 catalog 需要在多种语言中重复实现，商业产品难以支持多种不同的 catalog 和客户端。REST Catalog 协议提供了一个通用的 API 来与任何 Iceberg catalog 交互。页面列出了 REST 协议的五大优势：语言/引擎兼容性、改进的可靠性（基于变更的提交支持服务端去冲突和重试）、简化的元数据管理、高级功能（延迟快照加载、多表提交、缓存）、安全性（凭据分发或远程签名）

### `site/nav.yml` (+1/-1 lines)

**修改目的**：更新文档导航，将 REST Catalog Spec 从外链改为内链。

**工作逻辑**：将导航中 `- REST Catalog Spec: https://editor-next.swagger.io/?url=...` 替换为 `- REST Catalog Spec: rest-catalog-spec.md`，使其指向新创建的文档页面，而非直接跳转到外部 Swagger UI。

## 总结

本提交是一个纯文档改进，为 REST Catalog Spec 创建了专属文档页面，提供了更好的文档导航体验和更丰富的背景信息。用户现在可以在 Iceberg 文档站内先了解 REST Catalog 协议的设计动机和优势，再通过链接访问 OpenAPI 规范或 Swagger UI 进行交互式探索。
