# 提交 1695 81c4aee2d 分析

## 提交信息
- 哈希：81c4aee2dc2e7651cb79e86230f52fca745a099c
- 日期：2025-02-06 15:18:29 -0800
- 作者：JB Onofré
- 消息：Spark 3.3, 3.4: Update LICENSE/NOTICE for spark-runtime (#12189)

## 总体目的

本提交对 Spark 3.3 与 Spark 3.4 两个版本的 spark-runtime jar 的 LICENSE/NOTICE 文件进行大规模清理与更新，使其与 jar 实际打包的第三方依赖一致，通过 Apache 发行物的 license/rat 检查。

spark-runtime jar 的依赖构成在近期版本发生过显著变化：许多旧依赖（Paranamer、Airlift Slice、findbugs-annotations、j2objc annotations、Animal Sniffer、Carrot Search HPPC、Lucene-via-HPPC、Apache Yetus 等）已不再打包，同时部分仍打包的组件版权年份需要更新。此前 LICENSE/NOTICE 没有同步清理，导致声明与实际依赖脱节。本提交把 spark-runtime 的 LICENSE/NOTICE 收敛到与实际依赖树匹配的状态。

## 如何达成设计目的

通过逐条核对 spark-runtime jar 实际依赖，作者手工修订 LICENSE 与 NOTICE 两个纯文本文件，删除已不存在的条目、更新版权年份、补充新引入的组件。Spark 3.3 与 3.4 的 spark-runtime 依赖构成相同，因此两个版本的修改内容完全一致。无代码改动。

### 修改详情

#### spark/v3.3/spark-runtime/LICENSE（139 行变更，净减约 93 行）
- 修正 "Apache Avro" 条目：版权从 "2014-2017 The Apache Software Foundation" 改为 "Copyright 2010-2019 The Apache Software Foundation"，主页从错误的 parquet.apache.org 修正为 avro.apache.org。
- 删除 "Paranamer" 条目（含 BSD 许可证全文，约 35 行）。
- 更新 "Apache Parquet" 版权为 "2014-2024 The Apache Software Foundation"。
- 更新 "Apache Thrift" 版权为 "2006-2017 The Apache Software Foundation"。
- 更新 "Apache ORC" 版权为 "2013 and onwards The Apache Software Foundation"。
- 更新 "Apache Hive's storage API via ORC" 版权为 "2008-2020 The Apache Software Foundation"。
- 删除 "Airlift Slice" 条目。
- 删除 "findbugs-annotations by Stephen Connolly" 条目。
- 删除 "Google j2objc Annotations" 条目。
- 删除 "Animal Sniffer Annotations" 条目（含 MIT 许可证全文）。
- 删除 "Carrot Search Labs HPPC" 条目。
- 删除 "code from Apache Lucene via Carrot Search HPPC" 条目。
- 删除 "Apache Yetus audience annotations" 条目。
- 更新 "Project Nessie" 版权为 "2015-2025 Dremio Corporation"。
- 新增 "Eclipse MicroProfile OpenAPI" 条目（Apache 2.0）。
- 修复文件末尾缺少换行符问题。

#### spark/v3.3/spark-runtime/NOTICE（179 行变更，净减约 130 行）
- 删除 "Apache ORC" 的 NOTICE 引用块（含 Hewlett-Packard 子声明）。
- 删除 "Carrot Search Labs HPPC" 的 NOTICE/ACKNOWLEDGEMENT 引用块（含 Lucene、Fastutil、Koloboke 致谢）。
- 删除 "Apache Yetus" 的 NOTICE 引用块。
- 删除 "Apache Arrow" 的 NOTICE 引用块（含 SFrame、Feather、DyND 等子声明）。

#### spark/v3.4/spark-runtime/LICENSE
139 行变更，内容与 v3.3 完全相同。

#### spark/v3.4/spark-runtime/NOTICE
179 行变更，内容与 v3.3 完全相同。

## 小结

本次提交把 Spark 3.3/3.4 spark-runtime jar 的 LICENSE/NOTICE 大幅瘦身，移除了约 10 个已不再打包的第三方组件声明，并修正了多个组件的版权年份与主页错误。修改后声明与实际依赖一致，能够通过 Apache 发行投票。

回迁到 1.4.x 的注意事项：
1. spark-runtime 的依赖构成与 1.4.x 实际打包内容必须一致才能直接套用。若 1.4.x 的 spark-runtime 仍依赖 Paranamer、Airlift Slice、findbugs-annotations、j2objc、Animal Sniffer、Carrot Search HPPC、Apache Yetus 等组件，则不能删除对应条目。
2. Avro 主页错误的修正（parquet → avro）属于事实性修正，可独立回迁。
3. Apache Arrow NOTICE 块的删除需要确认 spark-runtime 是否真的不再传递依赖 Arrow；Spark 内部仍可能使用 Arrow，需以实际 jar 内容为准。
4. Project Nessie 版权年份更新与 Eclipse MicroProfile OpenAPI 新增条目，回迁时需确认这两个组件在 1.4.x spark-runtime 中确实存在。
