# 提交 1712：Docs: Update spark-quickstart note (#11996)

## 提交信息

- **序号**：1712 / 4088
- **哈希**：3e6da2e5437ffb3f643275927e5580cb9620256b
- **短哈希**：3e6da2e54
- **日期**：2025-02-10 10:05:24 +0100
- **作者**：xxchan
- **提交说明**：Docs: Update spark-quickstart note (#11996)
- **PR/Issue**：#11996

## 总体目的

修正 Spark 快速入门文档中关于 notebook 服务器启动的冗余说明。在 docker-compose 环境中，notebook 服务器已经由 docker-compose 自动启动，用户无需再通过 `docker exec` 命令手动启动。原文档让用户执行了一个不必要的命令，可能导致混淆。

提交说明指出："The notebook is already opened by the docker-compose, no need to open again."（notebook 已经由 docker-compose 打开，无需再次打开）。

## 如何达成设计目的

简化 `site/docs/spark-quickstart.md` 中的 note 提示，将原来指示用户手动运行 `docker exec` 命令启动 notebook 服务器的说明，改为直接告知用户 notebook 服务器已可用并提供了访问地址。

## 修改详情

### `site/docs/spark-quickstart.md`（修改, +1/-2 lines）

**修改目的**：移除冗余的 notebook 启动指令，简化用户操作指引。

**工作逻辑**：原文为两行说明："You can also launch a notebook server by running `docker exec -it spark-iceberg notebook`. The notebook server will be available at http://localhost:8888"，修改后简化为一行："You can also use the notebook server available at http://localhost:8888"。删除了手动启动命令，因为 docker-compose 配置已经自动启动了 notebook 服务器。

## 小结

- **成效**：文档更准确地反映了实际操作流程，用户无需执行多余命令即可使用 notebook 服务器。
- **影响范围**：仅影响网站快速入门文档，不影响代码逻辑。
- **回迁到 1.4.x 的注意事项**：可以安全回迁，纯文档修改无风险。需确认 1.4.x 分支的 docker-compose 配置是否也自动启动了 notebook 服务器，如果配置一致则可安全回迁。
