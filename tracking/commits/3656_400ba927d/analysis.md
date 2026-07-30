# 提交 3656：AWS: Fix LICENSE/NOTICE compliance for aws-bundle (#16196)

## 提交信息

- **序号**：3656 / 4088
- **哈希**：400ba927de303c12c69b368bbdea209ee2c82c5c
- **短哈希**：400ba927d
- **日期**：2026-05-06 14:12:20 -0700
- **作者**：Kevin Liu
- **提交说明**：AWS: Fix LICENSE/NOTICE compliance for aws-bundle (#16196)
- **PR/Issue**：#16196

## 总体目的

这个提交修复了 `aws-bundle` 的 LICENSE 和 NOTICE 文件的合规性问题。

Apache 项目发布分发包时，必须遵守各依赖的许可证要求，在 LICENSE 和 NOTICE 文件中正确声明所有打包依赖的许可证信息。此前 `aws-bundle` 的 LICENSE/NOTICE 文件存在以下问题：
1. 遗漏了多个传递依赖的许可证声明：Mozilla Public Suffix List（via Apache HttpComponents）、FastDoubleParser（via Jackson/AWS SDK）、fast_float（bundled by FastDoubleParser）、bigint（bundled by FastDoubleParser）、AWS Analytics Accelerator S3。
2. Reactive Streams 的许可证标注不规范（"MIT" 应为 "MIT-0"）。

这些问题可能导致 Apache 发布版本无法通过许可证审查（legal check）。本提交补全了所有缺失的许可证声明，使 aws-bundle 符合 Apache 许可证合规要求。

## 如何达成设计目的

在 `aws-bundle/LICENSE` 中新增遗漏依赖的完整许可证文本，并修正现有条目的格式；在 `aws-bundle/NOTICE` 中新增 AWS Analytics Accelerator S3 的 NOTICE 声明。

## 修改详情

### `aws-bundle/LICENSE` (+472/-3 lines)

**修改目的**：补全缺失的依赖许可证声明，修正现有条目格式。

**工作逻辑**：
1. 修正 Reactive Streams 许可证：从 "MIT / MIT No Attribution" 改为 "MIT-0"。
2. checkerframework checker-qual 条目格式微调。
3. 新增以下依赖的完整许可证文本：
   - Mozilla Public Suffix List（via Apache HttpComponents）— MPL 2.0 许可证全文
   - FastDoubleParser（via Jackson JSON Processor, via AWS SDK third-party-jackson-core）— MIT 许可证
   - fast_float（bundled by FastDoubleParser）— Apache 2.0 / Boost Software License / MIT 三选一
   - bigint（bundled by FastDoubleParser）— MIT 许可证

### `aws-bundle/NOTICE` (+5 lines)

**修改目的**：新增 AWS Analytics Accelerator S3 的 NOTICE 声明。

**工作逻辑**：
```
This product bundles AWS Analytics Accelerator S3 with the following in its NOTICE file:
| Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
```

## 总结

这个提交修复了 aws-bundle 的 LICENSE/NOTICE 合规性问题，补全了 Mozilla Public Suffix List、FastDoubleParser、fast_float、bigint、AWS Analytics Accelerator S3 等传递依赖的许可证声明，并修正了 Reactive Streams 的许可证标注。这是 Apache 项目发布前的必要合规修复，确保分发包能通过许可证审查。
