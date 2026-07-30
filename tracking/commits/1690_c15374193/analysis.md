# 提交 1690 c15374193 分析

## 提交信息
- 哈希：c153741935997271f896863afbd8a1ece1c56405
- 日期：2025-02-06 18:03:34 +0100
- 作者：JB Onofré
- 消息：Fix NOTICE and LICENSE in the spark-runtime jar (#12160)

## 总体目的

本提交修复 Spark v3.5 `spark-runtime` uber/bundle jar 中的 LICENSE 和 NOTICE 文件，使其准确反映 jar 中实际打包的第三方依赖。Apache 发布物要求 LICENSE/NOTICE 必须与 jar 内容一致，否则会通不过 Apache 法律审查（Legal Audit），阻塞发布。

`spark-runtime` 是一个 shaded/uber jar，把 Iceberg Spark 运行所需的所有第三方依赖打到一个 jar 里以便用户直接使用。因此它的 LICENSE/NOTICE 需要逐一列出每个被包含的第三方库及其许可证。随着 Iceberg 依赖随版本演进（升级、移除、新增依赖），bundle jar 的实际内容与维护在仓库里的静态 LICENSE/NOTICE 文件逐渐脱节：

- 一批曾经打包的依赖已不再包含：Paranamer、Airlift Slice、findbugs-annotations、Google j2objc Annotations、Animal Sniffer Annotations、Carrot Search Labs HPPC、Apache Lucene (via HPPC)、Apache Yetus audience annotations、Apache Arrow 等。这些条目需要从 LICENSE/NOTICE 中删除，否则会"声明了未包含的内容"。
- 一些依赖的版权年份过期或错误（如 Avro 写成了 parquet.apache.org 主页、Parquet 版权停在 2014-2017、Thrift 停在 2006-2010、ORC 停在 2013-2019、Nessie 停在 2020）。
- 新增的依赖未列出：Eclipse MicroProfile OpenAPI（已打入 runtime 但 LICENSE/NOTICE 缺失）。
- Nessie 升级后其 NOTICE 内容变化（新增 Apache Polaris (incubating) 的引用，版权更新到 2015-2025）。

作者是 Apache 成员/发布管理者 JB Onofré，这类法律文件修复是发布前的必要工作。

## 如何达成设计目的

逐条核对 bundle jar 实际内容，更新 LICENSE 和 NOTICE 两个静态文本文件：删除不再打包的依赖条目、修正版权年份与主页 URL、新增遗漏的依赖条目。由于是静态维护的文件，没有自动化机制，靠人工对照依赖清单修订。

### 修改详情

#### spark/v3.5/spark-runtime/LICENSE（-113 行，约净减 100+ 行）
主要变更：
- 修正 Apache Avro 条目：版权改为 "Copyright 2010-2019 The Apache Software Foundation"，主页改为 https://avro.apache.org/（原错误写成 parquet.apache.org）。
- 删除 Paranamer 条目（含其 BSD 许可证全文）。
- Apache Parquet 版权更新为 2014-2024。
- Apache Thrift 版权更新为 2006-2017。
- Apache ORC 版权更新为 "2013 and onwards"。
- Apache Hive storage API via ORC 版权更新为 2008-2020。
- 删除 Airlift Slice 条目。
- 删除 findbugs-annotations 条目。
- 删除 Google j2objc Annotations 条目。
- 删除 Animal Sniffer Annotations 条目（含 MIT 许可证全文）。
- 删除 Carrot Search Labs HPPC 条目。
- 删除 Apache Lucene (via Carrot Search HPPC) 条目。
- 删除 Apache Yetus audience annotations 条目。
- Project Nessie 版权更新为 2015-2025。
- 新增 Eclipse MicroProfile OpenAPI 条目（Apache 2.0）。

#### spark/v3.5/spark-runtime/NOTICE（-127 行）
主要变更：
- 删除 Apache ORC 的 NOTICE 条目（ASF 项目，其 NOTICE 内容已包含在主 NOTICE 中，无需重复）。
- 删除 Carrot Search Labs HPPC 的 NOTICE（含对 Lucene/Fastutil/Koloboke 的致谢）。
- 删除 Apache Yetus 的 NOTICE。
- 删除 Apache Arrow 的 NOTICE（含 SFrame/Feather/DyND/LLVM/google-lint/mman-win32/LevelDB/CMake/multibuild/Ibis 等大量第三方说明）——Arrow 已不在 runtime 中。
- 更新 Project Nessie 的 NOTICE：版权 2015-2025 Dremio，并新增对 Apache Polaris (incubating) 的引用（Polaris 由 Snowflake 捐赠）。
- 新增 Eclipse MicroProfile OpenAPI 的 NOTICE（基于 Swagger Core，版权 Arthur De Magalhaes）。

## 小结

成效：使 spark-runtime jar 的 LICENSE/NOTICE 与实际打包内容一致，消除 Apache 发布法律审查中的不符项（虚假声明已移除依赖、遗漏声明新增依赖、过时版权年份）。影响范围仅限 spark-runtime 的法律文本，不影响代码或运行时行为。这类修复对能否成功发布 Spark runtime artifact 至关重要。

回迁到 1.4.x 的注意事项：是否回迁取决于 1.4.x 的 spark-runtime 实际打包内容。1.4.x 是较早的分支，其 bundle 依赖列表与 main（面向 Spark 3.5）很可能不同（1.4.x 可能同时维护 Spark 3.3/3.4/3.5 的 runtime）。直接套用 main 的 LICENSE/NOTICE 会不准确——例如 1.4.x 可能仍打包 Arrow/HPPC/Yetus 等在 main 已移除的依赖，反之 main 新增的 MicroProfile OpenAPI 在 1.4.x 可能不存在。正确做法是：在 1.4.x 上重新核对各 spark-runtime jar 的实际依赖，按本提交的"核对并修正"思路单独维护。如果 1.4.x 已知有相同的法律审查问题，可参考本提交的修订项，但必须按 1.4.x 实际 bundle 内容裁剪。优先级：发布前必须，但需因地制宜。
