# 提交 0076：Doc: Fix Iceberg Javadoc link (#8885)

## 提交信息

- **序号**：0076 / 4088
- **哈希**：046fa74ca5476f3ca238167b5d247370669a07bf
- **短哈希**：046fa74ca
- **日期**：2023-10-19 23:01:15 +0200
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Doc: Fix Iceberg Javadoc link (#8885)
- **PR/Issue**：#8885

## 总体目的

这个提交修复了 Iceberg 项目 README 中指向 Java API 文档（Javadoc）的链接。README 中通过 `[iceberg-javadocs]` 引用标记了 Javadoc 入口，原本指向 `https://iceberg.apache.org/javadoc/main`，但该路径已不可用或不再是文档发布的正确地址，因此本提交将其改为 `https://iceberg.apache.org/javadoc/latest`。

这一变更属于文档维护类提交，背景是 Iceberg 官方站点的 Javadoc 发布策略调整：在多分支（如 `main`、`branch-1.4` 等）并行维护的情况下，`/javadoc/main` 这一带分支名的固定路径不能稳定地代表"最新发布版本"的文档，而 `/javadoc/latest` 作为站点维护的"最新版本"别名，能够始终指向当前推荐的稳定 Javadoc。对于首次接触 Iceberg 的使用者而言，README 是最重要的入口之一，指向一个失效或滞后的文档地址会显著降低上手体验，因此修正该链接具有直接的用户价值。

此外，本提交顺带清除了 README 中 `**NOTE**` 行末尾的多余空白字符。这虽是无关紧要的格式整理，但有助于保持文档整洁，避免某些 Markdown 渲染器或 diff 工具因行尾空白产生不必要的噪音。

## 如何达成设计目的

整体改动极其精简，只修改 `README.md` 两处：将 Javadoc 链接引用定义中的 URL 由 `main` 改为 `latest`，并去除 `**NOTE**` 行末尾空白。改动不涉及任何代码逻辑、构建配置或测试，纯文档层面。

## 修改详情

### `README.md`

**修改目的**：修正指向 Iceberg Javadoc 的链接，并清理一处行尾空白。

**工作逻辑**：

1. Javadoc 链接引用定义：将 `[iceberg-javadocs]: https://iceberg.apache.org/javadoc/main` 改为 `[iceberg-javadocs]: https://iceberg.apache.org/javadoc/latest`。README 第 40 行附近的正文 `[Java API javadocs][iceberg-javadocs] are available for the main.` 通过该引用定义解析为可点击链接，修改后该链接将正确跳转到站点维护的"最新版本"Javadoc 页面。

2. 行尾空白清理：`**NOTE**` 这一行原本带有尾随空格，提交后改为无尾随空格。这是纯格式整理，不改变渲染效果。

## 小结

本提交通过将 README 中失效的 Javadoc 链接从 `/javadoc/main` 改为 `/javadoc/latest`，确保了文档入口始终指向官方站点维护的最新版本 Javadoc，是一次小而具体的文档可用性修复。
