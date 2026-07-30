# 提交 1691 be0da5acd 分析

## 提交信息
- 哈希：be0da5acd14c9519fbd0267c41fa9d3ebd96b642
- 日期：2025-02-06 18:05:53 +0100
- 作者：JB Onofré（与 Fokko Driesprong 合著）
- 消息：Fix NOTICE and LICENSE in the gcp-bundle jar (#12144)

## 总体目的

本提交全面修正 `gcp-bundle` jar（Google Cloud Platform 集成的 uber/bundle jar，用于 GCS 等存储后端）的 LICENSE 和 NOTICE 文件，使其与 jar 中实际打包的第三方依赖及版本一致。与上一个提交（1690 修 spark-runtime）是同一作者、同一目的、不同 bundle 的配套修复，都是为 Apache 发布做法律合规准备。

`gcp-bundle` 打包了 Google Cloud Storage Java SDK 及其传递依赖（gRPC、Protobuf、Guava、Google Auth、HTTP Client、Jackson 等）。此前的 LICENSE/NOTICE 严重过时：

1. 几乎所有 Google Cloud 依赖的版本号停在旧版本：google-cloud-storage 2.22.5（实际已 2.47.0）、grpc 1.55.1（实际 1.69.0）、protobuf 3.23.2（实际 4.29.0）、guava 32.0.1-jre（实际 33.4.0-jre）、jackson-core 2.14.2（实际 2.18.2）等，数十个版本号需要更新。
2. 依赖集合发生变化：`com.google.code.findbugs:jsr305` 和 `io.opencensus:opencensus-proto` 已不再打包（OpenCensus 被 OpenTelemetry 取代），需删除；新增了一批 `io.opentelemetry` 依赖（api、sdk、context、logs、metrics、trace、autoconfigure-spi、gcp-resources、semconv）需补充。
3. NOTICE 文件缺少新增/变化依赖的 NOTICE 内容：grpc 1.69.0 的 NOTICE（含 OkHttp/Envoy/protoc-gen-validate/udpa 第三方说明）、perfmark-api 0.27.0 的 NOTICE（含 Catapult/Polymer/ASM）、conscrypt-openjdk-uber 2.5.2 的 NOTICE（含 Netty/Apache Harmony）。

作者是 Apache 成员 JB Onofré，Fokko 协作。这类修复是 Apache 发布法律审查（Legal Audit）的前置条件。

## 如何达成设计目的

逐条对照 gcp-bundle 的实际依赖清单（由 Gradle 解析得到），更新 LICENSE 和 NOTICE 两个静态文本文件：把每个依赖条目的版本号改成实际打包版本，删除已不打包的依赖条目，新增遗漏的依赖条目，并补充对应 NOTICE。由于文件是静态维护，靠人工核对修订。

### 修改详情

#### gcp-bundle/LICENSE（+178 行重构，大量版本号更新）
头部措辞从 "contains code from the following projects" 改为简洁的 "contains"。主要变更：
- 版本号更新（数十处）：jackson-core 2.14.2→2.18.2；api-common 2.13.0→2.42.1；gax/gax-grpc/gax-httpjson 2.30.0→2.59.1；google-api-client 2.2.0→2.7.1；gapic-google-cloud-storage-v2 2.22.5-alpha→2.47.0；proto-google-common-protos 2.21.0→2.50.1；proto-google-iam-v1 1.16.0→1.45.1；google-api-services-storage v1-rev20230301→v1-rev20241206；google-auth-library-* 1.18.0→1.30.1；auto-value-annotations 1.10.1→1.11.0；google-cloud-core/-core-grpc/-core-http 2.20.0→2.49.1；google-cloud-storage 2.22.5→2.47.0；gson 2.10.1→2.11.0；error_prone_annotations 2.18.0→2.36.0；failureaccess 1.0.1→1.0.2；guava 32.0.1-jre→33.4.0-jre；google-http-client-* 1.43.3→1.45.3；j2objc-annotations 2.8→3.0.0；google-oauth-client 1.34.1→1.37.0；protobuf-java/-java-util 3.23.2→4.29.0；re2j 1.6→1.7；commons-codec 1.15→1.17.1；grpc-* 1.55.1→1.69.0；perfmark-api 0.26.0→0.27.0；animal-sniffer-annotations 1.23→1.24；threetenbp 1.6.8→1.7.0。
- 删除：`com.google.code.findbugs:jsr305`（已不打包）、`io.opencensus:opencensus-proto`（被 OpenTelemetry 取代）。
- 新增 OpenTelemetry 系列依赖（均 Apache 2.0）：opentelemetry-api-incubator 1.45.0-alpha、opentelemetry-api 1.45.0、opentelemetry-context 1.45.0、opentelemetry-sdk 1.45.0、opentelemetry-sdk-common 1.45.0、opentelemetry-sdk-logs 1.45.0、opentelemetry-sdk-metrics 1.45.0、opentelemetry-sdk-trace 1.45.0、opentelemetry-sdk-extension-autoconfigure-spi 1.45.0、opentelemetry-gcp-resources 1.37.0-alpha、opentelemetry-semconv 1.27.0-alpha。

#### gcp-bundle/NOTICE（+269 行重构）
主要变更：
- jackson-core 版本号更新为 2.18.2。
- 删除旧的 commons-codec NOTICE（含 DoubleMetaphone 测试数据 Kevin Atkinson 版权、Beider-Morse 翻译版权）——commons-codec 升级后其 NOTICE 内容变化，且格式调整。
- grpc-netty-shaded 升级到 1.69.0，其 Netty Project NOTICE 重新格式化（用 `|` 前缀逐行标注，便于区分嵌入的第三方说明：Tomcat Native、Maven Wrapper、AIX netbsd、boringssl）。
- 新增对 gRPC 各子模块（grpc-alts/api/auth/context/core/googleapis/grpclb/protobuf/protobuf-lite/rls/services/stub/xds 均 1.69.0）的统一 NOTICE：Copyright 2014 The gRPC Authors，并嵌入 OkHttp、Envoy、protoc-gen-validate、udpa 等第三方说明。
- 新增 `io.perfmark:perfmark-api` 0.27.0 NOTICE：Copyright 2019 Google LLC，嵌入 Catapult、Polymer、ASM 第三方说明。
- 新增 `org.conscrypt:conscrypt-openjdk-uber` 2.5.2 NOTICE：Copyright 2016 The Android Open Source Project，嵌入 Netty、Apache Harmony 第三方说明。

## 小结

成效：使 gcp-bundle jar 的 LICENSE/NOTICE 与实际打包的 GCS SDK 及传递依赖（升级后的 grpc 1.69.0、protobuf 4.29.0、google-cloud-storage 2.47.0、新增的 OpenTelemetry 系列）完全一致，消除 Apache 发布法律审查中的不符项（过时版本号、遗漏依赖、多余依赖）。影响范围仅限 gcp-bundle 的法律文本，不影响代码或运行时行为。与 1690 一起构成发布前法律合规修复批次。

回迁到 1.4.x 的注意事项：与 1690 同理，是否能直接回迁取决于 1.4.x 的 gcp-bundle 实际依赖。1.4.x 时期的 GCS SDK 版本远低于 main（如可能仍是 google-cloud-storage 2.x 早期、grpc 1.5x），且 1.4.x 可能尚未引入 OpenTelemetry 依赖（仍用 OpenCensus）。直接套用 main 的 LICENSE/NOTICE 会声明 1.4.x 实际未打包的依赖、遗漏 1.4.x 实际打包的依赖，反而造成新的不一致。正确做法：在 1.4.x 上重新核对 gcp-bundle 实际依赖树，按本提交的"逐条核对版本号、增删依赖条目、补 NOTICE"思路单独维护。优先级：1.4.x 若要发布 gcp-bundle artifact 则发布前必须做等效核对，但不能直接 cherry-pick 本提交。
