# 提交 3658：GCP: Fix LICENSE, NOTICE, and runtime-deps for gcp-bundle (#16182)

## 提交信息

- **序号**：3658 / 4088
- **哈希**：e4028bf6a012691933441387a893eb921a780438
- **短哈希**：e4028bf6a
- **日期**：2026-05-06 14:13:07 -0700
- **作者**：Kevin Liu
- **提交说明**：GCP: Fix LICENSE, NOTICE, and runtime-deps for gcp-bundle (#16182)
- **PR/Issue**：#16182

## 总体目的

这个提交修复了 `gcp-bundle` 的 LICENSE、NOTICE 和 runtime-deps 的合规性问题，并调整了 slf4j 依赖的排除方式。

GCP bundle 依赖 Google Cloud SDK，其中包含大量传递依赖（gRPC、Netty、OpenTelemetry、Woodstox 等），许多传递依赖的许可证声明此前缺失。本提交补全了所有缺失的许可证和 NOTICE 声明，使 gcp-bundle 符合 Apache 发布的许可证合规要求。同时与 azure-bundle 一致，将 slf4j 排除从 shadowJar 级别移到 configuration 级别。

GCP bundle 的改动量最大（+1455/-129 行），因为 GCP SDK 的传递依赖最多，涉及 GPL/LGPL 类许可证（如 Mozilla Public Suffix List 的 MPL 2.0）、BoringSSL 的特殊声明等。

## 如何达成设计目的

1. 在 LICENSE 中补全所有缺失依赖的完整许可证文本（16 个新增 bundled product 声明）。
2. 在 NOTICE 中补充相关 NOTICE 声明。
3. 将 slf4j 排除从 shadowJar dependencies 移到 configurations.implementation，并从 runtime-deps.txt 移除 slf4j-api。

## 修改详情

### `gcp-bundle/LICENSE` (+1393/-46 lines)

**修改目的**：补全缺失的依赖许可证声明。

**工作逻辑**：新增以下依赖的许可证文本：
- FastDoubleParser、fast_float、bigint（via Jackson）
- Google Cloud Open-Telemetry Operations Exporters for Java — Apache 2.0
- Mozilla Public Suffix List（via Google Guava）— MPL 2.0 全文
- Apache Tomcat Native（via gRPC-netty-shaded）— Apache 2.0
- BoringSSL（via gRPC-netty-shaded）— OpenSSL/ISC 特殊声明
- checkerframework checker-qual 和 checker-compat-qual — MIT
- Common Expression Language (CEL) specification（shaded by gRPC-xds）— Apache 2.0
- xDS data plane API definitions（shaded by gRPC-xds）— Apache 2.0
- UDPA (Universal Data Plane API) definitions（shaded by gRPC-xds）— Apache 2.0
- JCTools（via Netty and OpenTelemetry）— Apache 2.0
- WeakConcurrentMap（via OpenTelemetry）— Apache 2.0
- MSV xsdlib、isorelax、RELAX NG Datatype API（bundled by Woodstox）— BSD-like

### `gcp-bundle/NOTICE` (+87/-52 lines)

**修改目的**：补充相关依赖的 NOTICE 声明，重组现有声明。

### `gcp-bundle/build.gradle` (+2/-3 lines)

**修改目的**：改进 slf4j 排除方式。

**工作逻辑**：在 `configurations.implementation` 新增 `exclude group: 'org.slf4j'`，移除 shadowJar 中的 dependencies 排除块。

### `gcp-bundle/runtime-deps.txt` (+0/-1 line)

**修改目的**：移除 slf4j-api 条目。

## 总结

这个提交修复了 gcp-bundle 的 LICENSE/NOTICE 合规性问题，补全了 16 个传递依赖的许可证声明（包括 MPL 2.0、BoringSSL 等特殊许可证）。这是四个 bundle 修复中改动量最大的，因为 GCP SDK 传递依赖最多。同时改进了 slf4j 排除方式，确保 slf4j 不被打包进 bundle。
