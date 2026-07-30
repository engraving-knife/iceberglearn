# 提交 0063：Build: Document missing `docker.sock` on OSX (#8766)

## 提交信息

- **序号**：0063 / 4088
- **哈希**：cb20bdbea7b299b2948e908a7775ce90818e6a92
- **短哈希**：cb20bdbea
- **日期**：2023-10-17
- **作者**：JB Onofré
- **提交说明**：Build: Document missing `docker.sock` on OSX (#8766)
- **PR/Issue**：#8766

## 总体目的

该提交为 Iceberg 的 `README.md` 增加一段针对 macOS（OSX）开发者的环境提示，说明在 macOS 上运行测试套件前需要手工创建一个指向 Docker socket 的符号链接。

背景是：Iceberg 的部分集成测试（例如 Flink/Spark/Hive 等引擎模块的集成测试、MiniCluster、容器化的 catalog 测试等）依赖 Docker 来拉起外部依赖（如 MySQL/Postgres metastore、Hive Metastore、MinIO 等）。测试代码与 Gradle 插件（典型如 `com.avast.gradle.docker-compose` 之类）默认去探测标准的 Unix domain socket 路径 `/var/run/docker.sock`。然而在 macOS + Docker Desktop 环境下，Docker Desktop 出于权限与沙盒考虑，并不会在 `/var/run/docker.sock` 创建 socket，而是放在用户目录下（如 `$HOME/.docker/run/docker.sock`），导致测试探测不到 Docker 而报错或被跳过，新手开发者常常在这一步卡住却找不到原因。

该提交选择以"文档"而非"代码"的方式解决——在 README 中明确给出 `sudo ln -s` 命令，让 macOS 开发者一次性建立 `/var/run/docker.sock` 到真实 socket 的符号链接。这是一种低成本、不引入构建复杂度的处理方式：不修改测试代码去额外探测多个 socket 路径，而是依赖开发者按提示准备环境。

## 如何达成设计目的

设计上就是在 README 的"模块说明"段落之后、"Engine Compatibility"小节之前，插入一个 Markdown 分隔线 + `**NOTE**` 提示块，写明问题与解决方案（一条 `sudo ln -s` 命令）。改动纯文档，零代码影响。

## 修改详情

### [README.md](file:///Users/fengxiaohang/trae/iceberglearn/README.md)

**修改目的**：提示 macOS 开发者测试需要 Docker，且在 OSX 上需要手工创建 docker socket 符号链接。

**工作逻辑**：在 README 第 77 行后（即列举 `iceberg-mr`、`iceberg-pig` 模块说明之后），新增 10 行 NOTE 块：

```markdown
---
**NOTE**

The tests require Docker to execute. On MacOS (with Docker Desktop), you might need to create a symbolic name to the docker socket in order to be detected by the tests:

```
sudo ln -s $HOME/.docker/run/docker.sock /var/run/docker.sock
```
---
```

要点有三：

1. 明确"测试需要 Docker 执行"这一前提，避免开发者以为测试可在无 Docker 环境下完整跑过；
2. 点明问题只出现在 "MacOS (with Docker Desktop)" 这一特定环境，Linux 上 `/var/run/docker.sock` 通常本就存在，无需此操作；
3. 给出可直接复制的 `sudo ln -s` 命令，把用户主目录下 `$HOME/.docker/run/docker.sock` 软链到标准路径 `/var/run/docker.sock`。`sudo` 是必要的，因为 `/var/run` 通常需要 root 写权限。

`---` 分隔线让该 NOTE 在 Markdown 渲染中视觉上独立成块，提升可读性。该 NOTE 紧跟模块列表、位于"Engine Compatibility"小节之前，处于开发者初次阅读 README 时容易扫到的位置。

## 小结

该提交以最小代价（10 行文档）缓解了 macOS 开发者运行 Iceberg 测试套件时常见的 Docker socket 探测问题，降低了项目本地构建的入门门槛，是开发者体验层面的一次小而实用的改进。
