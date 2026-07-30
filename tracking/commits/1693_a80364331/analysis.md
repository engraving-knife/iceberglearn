# 提交 1693 a80364331 分析

## 提交信息
- 哈希：a80364331a09401ba27bb2eb904310ebefc684f7
- 日期：2025-02-06 09:38:37 -0800
- 作者：JB Onofré
- 消息：Fix NOTICE and LICENSE in the flink-runtime jar (#12145)

## 总体目的

本提交修复 `flink/v1.20/flink-runtime` 打包 jar 中 LICENSE 与 NOTICE 文件的内容，使其与该 jar 实际包含的第三方依赖保持一致。Apache 发布物要求 LICENSE/NOTICE 必须准确反映所打包的依赖及其传递依赖的许可证信息，否则无法通过 Apache 发行投票。

具体而言，flink-runtime jar 在依赖升级后实际包含的第三方组件已经变化，但 LICENSE/NOTICE 文件没有同步更新。本提交做了三类调整：移除已不再打包的组件条目、为新引入的依赖补充许可证声明、对已有但格式不规范的条目（如 Project Nessie）按规范重写并扩充其传递 NOTICE 内容。

## 如何达成设计目的

通过逐条核对 flink-runtime jar 的实际依赖树，作者手工修订了 LICENSE 和 NOTICE 两个纯文本文件。没有代码改动，仅是文档合规性修复。改动遵循 Apache 发行物规范：每个第三方组件在 LICENSE 中给出名称、版权、主页、许可证及（非 Apache 许可证时的）许可证全文；在 NOTICE 中按原项目 NOTICE 文件的内容进行二级行引用（以 `|` 前缀标注）。

### 修改详情

#### flink/v1.20/flink-runtime/LICENSE
共 102 行变更（删 79 增 23 净减约 56 行），具体改动：
- 删除 "Animal Sniffer Annotations" 条目（MIT 许可证全文一并删除）。该组件已不再被打包。
- 删除 "Apache Yetus audience annotations" 条目。该组件已不再被打包。
- 重写 "Project Nessie" 条目：原条目以 "includes ... with the following in its NOTICE file" 形式引用了 Dremio 2015-2017 的简短声明；新条目改为标准的 "contains Project Nessie" 格式，版权更新为 "Copyright 2015-2025 Dremio Corporation"，补充主页 https://projectnessie.org/ 与 Apache 2.0 许可证。
- 新增 "RoaringBitmap" 条目（Apache 2.0）。
- 新增 "Eclipse Microprofile OpenAPI" 条目（Apache 2.0）。
- 新增 "Luben Zstd" 条目（BSD-2 License），并附带完整的 BSD-2 许可证文本。

#### flink/v1.20/flink-runtime/NOTICE
共 335 行变更（净增约 311 行），具体改动：
- 删除 "Apache ORC" 的 NOTICE 引用块（含 Hewlett-Packard 版权声明）。ORC 不再被打包。
- 删除 "Apache Yetus" 的 NOTICE 引用块。Yetus 不再被打包。
- 大幅扩充 "Project Nessie" 的 NOTICE 引用块：从原来简短的 Dremio 2015-2017 声明，扩展为完整的 Nessie NOTICE 文档，包含：
  - Nessie 主体声明（Copyright 2015-2025 Dremio Corporation）
  - 子引用：Apache Polaris (incubating) 的 NOTICE（Copyright 2024 ASF，含 Snowflake Inc. 捐赠说明）
  - 子引用：The Netty Project 的完整 NOTICE（Copyright 2014 The Netty Project），并进一步包含 Netty 内嵌的 JSR-166y（Public Domain）、Base64（Public Domain）、Webbit（BSD）、SLF4J 等子组件说明

## 小结

本次修改纯属发行物合规性修复，无代码逻辑变更，风险极低。修复后 flink-runtime jar 的 LICENSE/NOTICE 与实际依赖一致，能够通过 Apache 发行投票的 license/rat 检查。

回迁到 1.4.x 的注意事项：
1. 此修复针对 flink v1.20 的 flink-runtime jar，1.4.x 分支若仍维护 v1.20 且依赖列表与 main 一致，可直接 cherry-pick；若 1.4.x 的 flink-runtime 依赖列表不同（例如未引入 Nessie 或仍依赖 ORC/Yetus），则需要根据 1.4.x 实际依赖树重新核对，不能盲目套用。
2. 后续提交 1694（#12188）会做类似的 Flink LICENSE/NOTICE 更新，回迁时注意两提交的先后顺序与覆盖范围，避免重复或冲突。
3. Project Nessie 的 NOTICE 中嵌套了 Apache Polaris、Netty 等子声明，回迁时需整块复制，不可截断。
