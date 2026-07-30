# 提交 1711：Build: Update LICENSE/NOTICE files with last dependency updates (#12214)

## 提交信息

- **序号**：1711 / 4088
- **哈希**：7add0f36251f8f2b113bb563b26d8d71432dc798
- **短哈希**：7add0f362
- **日期**：2025-02-10 08:47:54 +0100
- **作者**：JB Onofré
- **提交说明**：Build: Update LICENSE/NOTICE files with last dependency updates (#12214)
- **PR/Issue**：#12214

## 总体目的

更新 Kafka Connect 运行时模块的 LICENSE 和 NOTICE 文件，以反映最近依赖版本升级后的准确信息。Apache 项目要求在分发包中包含所有依赖的许可证和声明文件，当依赖版本发生变化时，这些文件也需要同步更新。

此前有多个依赖进行了版本升级（如 httpclient5 从 5.4.1 升级到 5.4.2、AWS SDK 从 2.30.11 升级到 2.30.16 等），但 LICENSE/NOTICE 文件尚未同步更新。此提交将 Kafka Connect 运行时模块（hive 和 main 两个变体）中的许可证文件与实际依赖版本对齐，确保 Apache 合规性要求得到满足。

## 如何达成设计目的

批量修改 Kafka Connect 运行时模块下两个变体（hive 和 main）各自的 LICENSE 和 NOTICE 文件，将文件中记录的依赖版本号更新为最新版本。这些文件通常由构建工具的许可证插件自动生成，此提交将生成后的文件提交到版本控制中。

## 修改详情

### `kafka-connect/kafka-connect-runtime/hive/LICENSE`（修改, ±78 lines）

**修改目的**：更新 hive 变体运行时的 LICENSE 文件中的依赖版本号。

**工作逻辑**：将文件中记录的各依赖版本号更新为实际使用的版本。主要更新包括：httpclient5 从 5.4.1 到 5.4.2、AWS SDK 系列组件从 2.30.11 到 2.30.16 等。每行格式为 `Group: <组名>  Name: <组件名>  Version: <版本号>`，仅版本号部分发生变化。

### `kafka-connect/kafka-connect-runtime/hive/NOTICE`（修改, ±74 lines）

**修改目的**：更新 hive 变体运行时的 NOTICE 文件中的依赖版本号。

**工作逻辑**：与 LICENSE 文件类似，NOTICE 文件中记录的依赖产品版本号同步更新为新版本。

### `kafka-connect/kafka-connect-runtime/main/LICENSE`（修改, ±78 lines）

**修改目的**：更新 main 变体运行时的 LICENSE 文件中的依赖版本号。

**工作逻辑**：与 hive 变体的 LICENSE 更新内容一致，同步更新所有依赖版本号。

### `kafka-connect/kafka-connect-runtime/main/NOTICE`（修改, ±74 lines）

**修改目的**：更新 main 变体运行时的 NOTICE 文件中的依赖版本号。

**工作逻辑**：与 hive 变体的 NOTICE 更新内容一致，同步更新所有依赖版本号。

## 小结

- **成效**：Kafka Connect 运行时模块的 LICENSE/NOTICE 文件与实际依赖版本保持一致，满足 Apache 项目的许可证合规要求。
- **影响范围**：仅影响 Kafka Connect 模块的许可证文件，不影响代码逻辑。
- **回迁到 1.4.x 的注意事项**：不建议直接回迁。LICENSE/NOTICE 文件的内容取决于该分支实际的依赖版本，1.4.x 分支的依赖版本可能与 main 分支不同。如果 1.4.x 分支也升级了相应依赖，应在 1.4.x 分支上重新生成 LICENSE/NOTICE 文件而非直接 cherry-pick。需注意此提交依赖前置的依赖升级提交（如 1709 的 httpclient5 升级等）。
