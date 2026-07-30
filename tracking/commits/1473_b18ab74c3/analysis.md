# 提交 1473：Add `curl` to the `iceberg-rest-fixture` Docker image (#11705)

## 提交信息

- **序号**：1473 / 4088
- **哈希**：b18ab74c3c2ecddc8f17c9bdff625996a8ee600c
- **短哈希**：b18ab74c3
- **日期**：2024-12-09（Mon Dec 9 14:24:36 2024 +0100）
- **作者**：dominikhei <105610163+dominikhei@users.noreply.github.com>
- **提交说明**：Add `curl` to the `iceberg-rest-fixture` Docker image (#11705)
- **PR/Issue**：#11705
- **共作者**：Fokko Driesprong <fokko@apache.org>

## 总体目的

Iceberg 在 `docker/iceberg-rest-fixture/Dockerfile` 维护了一个用于演示和测试的 REST Catalog fixture Docker 镜像，基于 `azul/zulu-openjdk:17-jre-headless` 这个精简的 JRE 基础镜像，内置一个使用 SQLite 内存库的 Iceberg REST 服务（端口 8181）。这个镜像常被开发者用来本地拉起一个 Iceberg REST Catalog 做联调。

由于基础镜像 `17-jre-headless` 是精简版，并未预装 `curl` 等常见调试工具，开发者在容器内无法用 `curl` 直接探测 REST 服务的 `/v1/config` 端点是否就绪，只能借助外部工具，调试不便。

本提交的目的有两点：
1. 在镜像内安装 `curl`，方便使用者在容器内直接发起 HTTP 请求调试 REST API；
2. 顺带为镜像添加 `HEALTHCHECK` 指令，让 Docker（以及编排系统如 Kubernetes、docker-compose）能够自动判断容器内 REST 服务是否真正就绪，而不是仅靠 JVM 进程是否存活来判定健康状态——后者会误判：JVM 还在但 REST 服务尚未监听端口、或 REST 服务因配置异常无法启动时，旧镜像仍会标记为 healthy。

## 如何达成设计目的

直接编辑 `docker/iceberg-rest-fixture/Dockerfile`：
1. 在创建用户的同时，通过 `apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*` 一行式安装 curl 并清理 apt 缓存，避免镜像体积膨胀。
2. 在 `ENV REST_PORT=8181` 之后新增 `HEALTHCHECK` 指令，调用刚安装的 `curl --fail http://localhost:$REST_PORT/v1/config`，配合 `--retries=10 --interval=1s` 两个参数实现"启动期快速重试"的探测策略。

## 修改详情

### `docker/iceberg-rest-fixture/Dockerfile`

**修改目的**：为镜像安装 curl 并配置健康检查。

**工作逻辑**：

1. **安装 curl（同时清理 apt 缓存）**：在原有 `RUN` 指令末尾追加：

```dockerfile
RUN  set -xeu && \
     groupadd iceberg --gid 1000 && \
     useradd iceberg --uid 1000 --gid 1000 --create-home && \
     apt-get update && \
     apt-get install -y --no-install-recommends curl && \
     rm -rf /var/lib/apt/lists/*
```

- `set -xeu` 确保任何命令失败都会终止构建并打印执行命令。
- `--no-install-recommends` 避免安装推荐但非必需的包，控制镜像大小。
- `rm -rf /var/lib/apt/lists/*` 在同一 `RUN` 层内清理 apt 下载的索引，确保最终镜像层不携带这些临时文件——这是 Docker 镜像构建的最佳实践，避免后续层即使删除文件仍保留在中间层中导致镜像膨胀。

2. **新增 HEALTHCHECK**：

```dockerfile
HEALTHCHECK --retries=10 --interval=1s \
  CMD curl --fail http://localhost:$REST_PORT/v1/config || exit 1
```

- `--interval=1s` 每秒探测一次，这在容器刚启动时能让 Docker 快速感知服务是否就绪。
- `--retries=10` 连续 10 次失败才标记为 unhealthy，相当于给服务最长 ~10 秒的启动容忍期，足够 REST 服务初始化。
- `curl --fail` 让 curl 在 HTTP 状态码 ≥ 400 时返回非零退出码，从而触发 `|| exit 1` 把容器标记为 unhealthy；`/v1/config` 是 Iceberg REST Catalog 的标准配置端点，能反映服务是否真正可用（而不是只返回 404 之类）。
- 这里 `$REST_PORT` 由前面的 `ENV REST_PORT=8181` 注入，Docker 在 `HEALTHCHECK` 的 `CMD` 中会展开环境变量。

注意 `HEALTHCHECK` 放在 `USER iceberg:iceberg` 之前，但 `HEALTHCHECK` 指令本身不涉及文件权限，运行时由 Docker daemon 以容器内当前用户（`iceberg`）执行 `CMD`，由于 curl 已全局安装，普通用户也能调用。

## 小结

- **成效**：`iceberg-rest-fixture` 镜像现可在容器内使用 `curl` 直接调试 REST API，并具备 `HEALTHCHECK` 自动健康探测能力，便于 docker-compose / Kubernetes 等编排系统准确判断服务就绪状态。
- **影响范围**：仅 `docker/iceberg-rest-fixture/Dockerfile` 一个文件，新增 8 行；不影响 Iceberg Java 代码与发布产物。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支若也维护同样的 Docker fixture 镜像且存在调试/健康检查诉求，可考虑回迁；若 1.4.x 该 Dockerfile 结构不同（例如基镜像、用户创建步骤不同），需要相应调整安装命令的位置。这是基础设施改进，对 1.4.x 运行时无任何影响，**可选回迁**。
