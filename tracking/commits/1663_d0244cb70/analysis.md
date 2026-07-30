# 提交 1663：AWS: Fix LICENSE and NOTICE in aws-bundle jar (#12142)

## 提交信息

- **序号**：1663 / 4088
- **哈希**：d0244cb70433371a8c682a845f1e7ba847777add
- **短哈希**：d0244cb70
- **日期**：2025-01-31（Fri Jan 31 23:22:15 2025 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：AWS: Fix LICENSE and NOTICE in aws-bundle jar (#12142)
- **PR/Issue**：#12142

## 总体目的

`aws-bundle` 是 Iceberg 提供的便利模块，把 AWS SDK for Java v2 及其传递依赖（Netty 等）打包成一个 uber jar，并附带 `LICENSE` 与 `NOTICE` 文件以符合 Apache 许可证合规要求——必须在分发的 jar 中包含自身及所有第三方组件的 LICENSE 与 NOTICE。由于 `aws-bundle` 把第三方依赖 shade/打包进 jar，其 LICENSE/NOTICE 必须准确反映**实际打包的依赖及其版本**。

此前的 LICENSE/NOTICE 存在三类合规问题：

1. **版本号过时**：LICENSE/NOTICE 中记录的 AWS SDK 版本是 `2.28.5`、Netty 版本是 `4.1.112.Final`，但实际打包的依赖已升级到 AWS SDK `2.30.6`（提交 1649 升级 BOM）、Netty `4.1.115.Final`。版本不一致违反合规要求；
2. **NOTICE 缺失 Netty 的 NOTICE 内容**：Netty 作为传递依赖被打包进 jar，其自身的 NOTICE 文件包含大量第三方归属信息（JSR166、Base64、Webbit、SLF4J、Apache Harmony、jbzip2、JCTools、HPACK 等），这些归属必须在分发 jar 的 NOTICE 中体现。原 NOTICE 只有 AWS SDK 的 NOTICE，缺少 Netty 的完整 NOTICE；
3. **NOTICE 残留过时的 commons-codec 归属**：NOTICE 中有一段 commons-codec 的归属（DoubleMetaphone 测试数据、Beider-Morse 音译算法来源），但 commons-codec 已不再是 aws-bundle 的传递依赖（或其 NOTICE 已变更），该段应移除。

本提交修正这些问题，使 LICENSE/NOTICE 与实际打包依赖一致，满足 Apache 发行物许可合规要求。

## 如何达成设计目的

1. **LICENSE 版本同步**：把 LICENSE 中所有 Netty 组件版本从 `4.1.112.Final` 改为 `4.1.115.Final`，所有 AWS SDK 组件版本从 `2.28.5` 改为 `2.30.6`，与实际依赖版本对齐；
2. **NOTICE 版本同步**：把 NOTICE 中所有 `NOTICE for Group: software.amazon.awssdk ... Version: 2.28.5` 改为 `2.30.6`，`third-party-jackson-core` 版本同步；
3. **NOTICE 移除 commons-codec 段**：删除 commons-codec 的 NOTICE 归属段（DoubleMetaphone 测试数据版权、Beider-Morse 算法来源说明）；
4. **NOTICE 新增 Netty 段**：新增完整的 Netty 项目 NOTICE，列出 Netty 所有 netty-* 组件（buffer/codec/codec-http/codec-http2/common/handler/resolver/transport/transport-classes-epoll/transport-native-unix-common，版本 4.1.115.Final），并附 Netty 自身 NOTICE 的全部第三方归属声明（涵盖 JSR166、Base64、Webbit、SLF4J、Apache Harmony、jbzip2、libdivsufsort、JCTools、JZlib、Compress-LZF、lz4、lzma-java、zstd-jni、jfastlz、Protocol Buffers、Bouncy Castle、Snappy、JBoss Marshalling、Caliper、Commons Logging、Log4J、Aalto XML、HPACK 三个变体、Commons Lang、Maven Wrapper、dnsinfo.h、Brotli4j 等）。

## 修改详情

### `aws-bundle/LICENSE`（修改，+46 / -46）

**修改目的**：同步第三方依赖版本号。

**工作逻辑**：逐条把 LICENSE 中记录的依赖版本号更新：
- `io.netty` 各组件（netty-codec/netty-codec-http/netty-codec-http2/netty-common/netty-handler/netty-resolver/netty-transport/netty-transport-classes-epoll/netty-transport-native-unix-common）：`4.1.112.Final` → `4.1.115.Final`；
- `software.amazon.awssdk` 各组件（annotations/apache-client/arns/auth/aws-core/aws-json-protocol/aws-query-protocol/aws-xml-protocol/checksums/checksums-spi/crt-core/dynamodb/endpoints-spi/glue/http-auth/http-auth-aws/http-auth-aws-crt/http-auth-aws-eventstream/http-auth-spi/http-client-spi/iam/identity-spi/json-utils/kms/lakeformation/metrics-spi/netty-nio-client/profiles/protocol-core/regions/retries/retries-spi/s3/sdk-core/sso/sts/utils）：`2.28.5` → `2.30.6`。

每条记录的格式（Group/Name/Version/Project URL/License）与 License 类型（Apache 2.0）不变，仅版本号字段更新。

### `aws-bundle/NOTICE`（修改，+323 / -46）

**修改目的**：同步版本号、移除过时归属、补齐 Netty NOTICE。

**工作逻辑**：

1. **移除 commons-codec 段**：删除原 NOTICE 开头处的 commons-codec 归属段，包括：
   - DoubleMetaphoneTest 测试数据来源（Copyright 2002 Kevin Atkinson，来自 aspell.net）；
   - Beider-Morse 音译算法来源（Copyright 2008 Alexander Beider & Stephen P. Morse，从 PHP 翻译）。

2. **AWS SDK 版本同步**：所有 `NOTICE for Group: software.amazon.awssdk Name: <module> Version: 2.28.5` 改为 `Version: 2.30.6`（覆盖 30+ 模块）；`third-party-jackson-core` 版本从 `2.28.5` 改为 `2.30.6`。AWS SDK 的版权声明（Copyright Amazon.com, Inc. or its affiliates）与 Jackson 相关说明不变。

3. **新增 Netty NOTICE 段**：在文件末尾新增完整的 Netty 项目 NOTICE，包含：
   - 10 个 Netty 组件的 `NOTICE for Group: io.netty ...` 列表（版本 4.1.115.Final）；
   - Netty 项目说明（https://netty.io/，Copyright 2014 The Netty Project，Apache 2.0 许可）；
   - Netty 自身 NOTICE 中列出的全部第三方归属与可选依赖，每个条目含 LICENSE 类型与 HOMEPAGE：
     - JSR166 扩展（Public Domain）、Base64（Public Domain）、Webbit（BSD）、SLF4J（MIT）、Apache Harmony（Apache 2.0）、jbzip2（MIT）、libdivsufsort（MIT）、JCTools（ASL2）、JZlib（BSD）、Compress-LZF（Apache 2.0）、lz4（Apache 2.0）、lzma-java（Apache 2.0）、zstd-jni（BSD）、jfastlz（MIT）、Protocol Buffers（New BSD）、Bouncy Castle（MIT）、Snappy（New BSD）、JBoss Marshalling（Apache 2.0）、Caliper（Apache 2.0）、Commons Logging（Apache 2.0）、Log4J（Apache 2.0）、Aalto XML（Apache 2.0）、HPACK 三个变体（Twitter Apache 2.0、Benfield MIT、Tsujikawa MIT）、Commons Lang（Apache 2.0）、Maven Wrapper（Apache 2.0）、dnsinfo.h（Apple Public Source License 2.0）、Brotli4j（Apache 2.0）。

## 小结

- **成效**：修正 `aws-bundle` jar 的 LICENSE/NOTICE 与实际打包依赖的不一致，补齐缺失的 Netty NOTICE 第三方归属，移除过时的 commons-codec 归属，使 Iceberg 的 AWS 便利模块满足 Apache 发行物的许可合规要求，可合法分发。
- **影响范围**：仅 `aws-bundle` 模块的 LICENSE/NOTICE 文本，不影响代码或运行时行为。这些文件会打包进发布的 aws-bundle jar。合规性修复对下游用户重新分发该 jar 具有法律意义。
- **回迁到 1.4.x 的注意事项**：纯合规文档修复，回迁安全但需注意版本匹配。1.4.x 的 aws-bundle 实际依赖版本可能与 main 不同（1.4.x 可能仍用旧版 AWS SDK/Netty），直接照搬 main 的 LICENSE/NOTICE 会导致版本号与实际不符。正确做法是根据 1.4.x 实际打包的依赖版本（运行 `./gradlew :aws-bundle:dependencies` 确认）生成对应的 LICENSE/NOTICE，只回迁"补齐 Netty NOTICE、移除过时 commons-codec 段"的结构性修复，版本号保持 1.4.x 实际值。若 1.4.x 已发布含错误 LICENSE/NOTICE 的 aws-bundle，可能需要发布修正版。
