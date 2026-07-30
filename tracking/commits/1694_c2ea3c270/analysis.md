# 提交 1694 c2ea3c270 分析

## 提交信息
- 哈希：c2ea3c2706bc83580992d6ae2947bdfa2d4c8103
- 日期：2025-02-06 15:17:46 -0800
- 作者：JB Onofré
- 消息：Flink: Update LICENSE/NOTICE in flink-runtime Jars (#12188)

## 总体目的

本提交是上一个提交（1693，#12145）的延续，把 LICENSE/NOTICE 修复从 flink v1.20 扩展到 v1.18 和 v1.19 两个版本的 flink-runtime jar，并对 v1.20 做了少量补充修正。目的是让所有维护中的 Flink 版本的 flink-runtime jar 都通过 Apache 发行物的 license/rat 检查。

三个版本的 flink-runtime jar 依赖构成基本相同，因此 LICENSE/NOTICE 内容也趋于一致。提交 1693 只修了 v1.20，本提交把同样的修订应用到 v1.18/v1.19，并修复 v1.20 遗漏的 Airlift Slice（应删除）和 Codehale Metrics（应新增）条目。

## 如何达成设计目的

把 v1.20 在 1693 中已验证的 LICENSE/NOTICE 模板复制到 v1.18 和 v1.19，再针对各版本实际依赖做微调。仍然是纯文档修订，无代码改动。

### 修改详情

#### flink/v1.18/flink-runtime/LICENSE
120 行变更。改动内容与 1693 对 v1.20 的改动一致，并额外：
- 删除 "Airlift Slice" 条目（Copyright 2013-2020 Slice authors）——该依赖在 v1.18 中已不再打包。
- 新增 "Codehale Metrics" 条目（Copyright 2010-2013 Coda Hale, Yammer.com, 2014-2021 Dropwizard Team，Apache 2.0）。
- 删除 "Animal Sniffer Annotations"（MIT 全文）、"Apache Yetus audience annotations"。
- 重写 "Project Nessie" 条目为标准格式（Copyright 2015-2025 Dremio Corporation）。
- 新增 RoaringBitmap、Eclipse Microprofile OpenAPI、Luben Zstd（BSD-2 全文）条目。
- 修复文件末尾缺少换行符的问题（"No newline at end of file" → 正常换行）。

#### flink/v1.18/flink-runtime/NOTICE
335 行变更（净增约 311 行），与 v1.20 在 1693 中的 NOTICE 改动完全一致：
- 删除 Apache ORC、Apache Yetus 的 NOTICE 引用块。
- 大幅扩充 Project Nessie 的 NOTICE 引用块，包含 Apache Polaris (incubating)、The Netty Project 及 Netty 内嵌的 JSR-166y、Base64、Webbit、SLF4J 等子组件说明。

#### flink/v1.19/flink-runtime/LICENSE
120 行变更，内容与 v1.18 完全相同。

#### flink/v1.19/flink-runtime/NOTICE
335 行变更，内容与 v1.18 完全相同。

#### flink/v1.20/flink-runtime/LICENSE
16 行变更（补充 1693 遗漏的条目）：
- 删除 "Airlift Slice" 条目。
- 新增 "Codehale Metrics" 条目。

## 小结

本次提交把 LICENSE/NOTICE 合规性修复统一覆盖到 v1.18、v1.19、v1.20 三个 Flink 版本，确保三个 flink-runtime jar 的发行物检查一致通过。无代码逻辑变更。

回迁到 1.4.x 的注意事项：
1. 与 1693 强相关，回迁时建议两提交一起回迁，避免 v1.20 出现中间态（1693 已修但 1694 未修）。
2. v1.18/v1.19 的实际依赖必须与 main 一致才能直接套用；若 1.4.x 的 flink-runtime 依赖列表不同（特别是 Airlift Slice、Codehale Metrics、Project Nessie、RoaringBitmap、Zstd 这些组件的存在性），需重新核对。
3. v1.20 在本提交中只做了 Airlift Slice 删除与 Codehale Metrics 新增，是 1693 的补丁，回迁顺序不能颠倒。
