# 提交 1758：Kafka: Pin Kafka-Connect version to fix integration tests (#12340)

## 提交信息

- **序号**：1758 / 4088
- **哈希**：08d0b50bbb22baf4cbfc1b505fb65502e43d743e
- **短哈希**：08d0b50bb
- **日期**：2025-02-19 18:47:15 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Kafka: Pin Kafka-Connect version to fix integration tests (#12340)
- **PR/Issue**：#12340

## 总体目的

本提交旨在修复 Kafka-Connect 集成测试因 Docker 镜像版本未固定而失败的问题。

在 docker-compose 配置中，`kafka` 和 `connect` 两个服务使用的是 `confluentinc/cp-kafka` 和 `confluentinc/cp-kafka-connect` 镜像，未指定版本标签（tag）。这意味着每次运行集成测试时都会拉取 latest（最新）版本的镜像。当 Confluent 发布新版本镜像时，可能引入不兼容的变更或行为变化，导致集成测试不稳定或失败。

通过将两个镜像的版本固定为 `7.8.1`，确保测试环境的一致性和可重复性，避免因上游镜像更新而导致的测试失败。

## 如何达成设计目的

提交修改 docker-compose.yml 文件，为 `kafka` 和 `connect` 两个服务的 Docker 镜像添加版本标签 `7.8.1`，从使用 `latest`（默认）改为使用固定版本。

## 修改详情

### `kafka-connect/kafka-connect-runtime/docker/docker-compose.yml`（修改, +2/-2 lines）

**修改目的**：固定 Kafka 和 Kafka-Connect 的 Docker 镜像版本，确保集成测试环境一致。

**工作逻辑**：

1. **kafka 服务**：将镜像从 `confluentinc/cp-kafka`（无版本标签，拉取 latest）改为 `confluentinc/cp-kafka:7.8.1`（固定版本 7.8.1）。

2. **connect 服务**：将镜像从 `confluentinc/cp-kafka-connect`（无版本标签，拉取 latest）改为 `confluentinc/cp-kafka-connect:7.8.1`（固定版本 7.8.1）。

两个服务使用相同的 Confluent Platform 版本 7.8.1，确保 Kafka broker 和 Kafka Connect worker 之间的版本兼容性。

## 小结

- **成效**：通过固定 Docker 镜像版本为 7.8.1，解决了集成测试因上游镜像更新而不稳定的问题，确保了测试环境的可重复性。
- **影响范围**：仅影响 Kafka-Connect 模块的集成测试环境配置，不影响生产代码逻辑。
- **回迁到 1.4.x 的注意事项**：如果 1.4.x 分支的 Kafka-Connect 集成测试也遇到类似的版本漂移问题，可以回迁此修复。需注意 1.4.x 分支的 docker-compose.yml 结构是否与此一致。版本 7.8.1 是一个较旧但稳定的版本，如果 1.4.x 分支需要更新的 Kafka 版本，可考虑使用其他合适的固定版本号。
