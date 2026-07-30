# 提交 1610 17432475c 分析

## 提交信息
- 哈希：17432475c46d07835bb80fb4d707a3dbb13415f0
- 日期：2025-01-20 17:43:31 -0700
- 作者：Om Kenge
- 消息：Docs: Update Footer Copyright Year (#12011)

## 总体目的

本次提交将 Iceberg 官方文档站点（基于 MkDocs Material）页脚的版权年份从 `2024` 更新为 `2025`，使文档页面底部显示的版权声明与当前年份保持一致。

Apache 项目作为持续运营的开源项目，其版权声明中的年份通常需要逐年更新以反映“版权持续至当前年份”的法律惯例。进入 2025 年后，页脚仍显示 `Copyright © 2024` 会让站点显得维护滞后，因此本次更新属于常规的年度维护性文档修正。

这是一处纯文档展示层的改动，不涉及任何功能逻辑、构建配置或规范定义。

## 如何达成设计目的

设计思路是直接修改文档站点的页脚模板覆盖文件。MkDocs Material 主题允许通过 `overrides` 目录覆盖主题的部分模板，Iceberg 站点正是通过 `site/overrides/partials/footer.html` 自定义页脚内容，因此版权年份的修改只需定位到该文件中的对应文本并替换。

### 修改详情

#### site/overrides/partials/footer.html

文件中第 121 行附近的版权声明段落，将年份从 `2024` 改为 `2025`：

```diff
- trademarks of The Apache Software Foundation. Copyright © 2024 The
+ trademarks of The Apache Software Foundation. Copyright © 2025 The
```

该段落完整含义为：Apache Iceberg、Iceberg、Apache、Apache 羽毛 Logo 以及 Apache Iceberg 项目 Logo 是 Apache 软件基金会的注册商标或商标。版权所有 © 2025 Apache 软件基金会，依据 Apache 许可证授权。修改后，所有文档页面渲染时页脚都会显示 2025 年。

## 小结

本次提交成效为文档页脚版权年份与当前年份（2025）对齐，消除维护滞后的观感。影响范围仅限文档站点页脚模板的一处文本，无任何功能影响。

回迁到 1.4.x 分支的注意事项：
- 该改动为纯文档文本更新，回迁无风险，可直接应用。
- 回迁时需确认 1.4.x 分支的 `site/overrides/partials/footer.html` 当前显示的年份；若已是 2025 或更高年份则无需重复回迁，若仍为 2024 或更早则可应用本提交。
- 由于版权年份是逐年递增的维护项，若 1.4.x 维护时已进入更晚的年份，应直接更新为对应年份而非拘泥于 2025。
