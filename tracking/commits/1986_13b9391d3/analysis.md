# 提交 1986：Doc: Update hive-quickstart.md (#12746)

## 提交信息

- **序号**：1986 / 4088
- **哈希**：13b9391d32401af45825a3bf816d1d61a2ba6a21
- **短哈希**：13b9391d3
- **日期**：2025-04-11 16:54:29 +0200
- **作者**：Georgi Ivanov
- **提交说明**：Doc: Update hive-quickstart.md (#12746)
- **PR/Issue**：#12746

## 总体目的

本提交修正 Hive 快速入门文档中 Docker 容器启动命令的平台架构参数，使其在 Apple Silicon（M 系列）Mac 上能正确运行。

原文档建议在 M 系列 Mac 上使用 `--platform linux/amd64` 启动 Hive 容器。然而 `apache/hive` 镜像实际支持 arm64 架构，在 Apple Silicon 上强制使用 amd64 会通过 QEMU 模拟运行，导致性能下降甚至兼容性问题。正确的做法是在 M 系列 Mac 上使用原生 `--platform linux/arm64`。本提交更正该参数，并补充说明 Intel Mac（amd64）与 Apple Silicon Mac（arm64）的架构差异，帮助用户根据自身机器选择正确的 `--platform` 值。

## 如何达成设计目的

编辑 `site/docs/hive-quickstart.md`，将 `--platform linux/amd64` 改为 `--platform linux/arm64`，并增加架构选择的说明文字。

## 修改详情

### `site/docs/hive-quickstart.md` (修改, +4/-2 lines)

**修改目的**：修正 M 系列 Mac 的容器平台架构参数并补充说明。

**工作逻辑**：
- 在 `docker run` 命令前补充一段说明：为兼容 Intel（x86_64）与 Apple Silicon（M1/M2/M3）Mac，可用 `--platform` 指定架构；Apple Silicon 使用 arm64，Intel 使用 amd64。
- 将 `docker run` 命令中的 `--platform linux/amd64` 改为 `--platform linux/arm64`（针对 M 系列 Mac 的推荐值）。

## 总结

文档修正提交，将 `hive-quickstart.md` 中 M 系列 Mac 启动 Hive 容器的 `--platform` 参数由 `linux/amd64` 改为原生的 `linux/arm64`，并补充 Intel/Apple Silicon 架构选择说明，避免在 Apple Silicon 上不必要的 x86 模拟。
