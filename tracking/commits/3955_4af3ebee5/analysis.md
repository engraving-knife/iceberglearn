# 提交 3955：Build: Support toggling log levels in iceberg-rest-fixture Docker image (#16725)

## 提交信息

- **序号**：3955 / 4088
- **哈希**：4af3ebee5d12a964a9c19721cc5ffebc219a44fc
- **短哈希**：4af3ebee5
- **日期**：2026-06-26 13:05:18 -0700
- **作者**：Sejal Gupta
- **提交说明**：Build: Support toggling log levels in iceberg-rest-fixture Docker image (#16725)
- **PR/Issue**：#16725

## 总体目的

这次提交为 `iceberg-rest-fixture` Docker 镜像添加了日志级别配置能力。iceberg-rest-fixture 是 Iceberg 提供的 REST catalog 测试 fixture Docker 镜像，用于本地开发和测试。

原问题：该 fixture 默认使用 slf4j-simple 的 INFO 级别日志，在开发和测试过程中会产生大量 INFO 级别日志（"log flooding"），干扰用户关注的重要信息。用户无法通过环境变量调整日志级别，只能接受默认的 INFO 级别。

修复后，用户可以通过两种方式控制日志：
1. **环境变量 `LOG_LEVEL`**：设置 slf4j-simple 的默认日志级别（如 `WARN`、`ERROR`、`OFF`），简单快捷。
2. **挂载配置目录 `LOG_CONFIG_DIR`**：挂载包含 `simplelogger.properties` 的目录，支持完整的 slf4j-simple 配置。若同时设置 `LOG_LEVEL` 和 `LOG_CONFIG_DIR`，`LOG_LEVEL` 会覆盖配置文件中的默认日志级别，但配置文件中的其他设置仍然生效。

实现方式是将 Docker 镜像的启动命令从直接 `java -jar` 改为通过 `entrypoint.sh` 脚本启动，脚本根据环境变量动态构建 Java 启动参数。

## 如何达成设计目的

1. 新增 `entrypoint.sh` 启动脚本，根据 `LOG_LEVEL` 和 `LOG_CONFIG_DIR` 环境变量构建 Java 启动命令。
2. 修改 `Dockerfile`，将启动命令从 `CMD ["java", "-jar", "iceberg-rest-adapter.jar"]` 改为 `CMD ["sh", "/usr/lib/iceberg-rest/entrypoint.sh"]`，并 COPY 脚本到镜像中。
3. 更新 `README.md`，文档说明日志配置的使用方法。

## 修改详情

### `docker/iceberg-rest-fixture/Dockerfile` (+3/-2 lines)

**修改目的**：使用 entrypoint 脚本替代直接 java -jar 启动。

**工作逻辑**：
- 新增 `COPY` 指令将 `entrypoint.sh` 复制到镜像中。
- 将 `CMD` 从 `["java", "-jar", "iceberg-rest-adapter.jar"]` 改为 `["sh", "/usr/lib/iceberg-rest/entrypoint.sh"]`。

### `docker/iceberg-rest-fixture/entrypoint.sh` (+35 lines, 新文件)

**修改目的**：动态构建 Java 启动命令，支持日志级别配置。

**工作逻辑**：
```sh
set -eu
CLASSPATH="iceberg-rest-adapter.jar"
if [ -n "${LOG_CONFIG_DIR:-}" ]; then
  CLASSPATH="${LOG_CONFIG_DIR}:${CLASSPATH}"
fi
set -- java
if [ -n "${LOG_LEVEL:-}" ]; then
  set -- "$@" "-Dorg.slf4j.simpleLogger.defaultLogLevel=${LOG_LEVEL}"
fi
exec "$@" -cp "$CLASSPATH" org.apache.iceberg.rest.RESTCatalogServer
```
- 如果设置了 `LOG_CONFIG_DIR`，将其加入 classpath（使 `simplelogger.properties` 可被加载）。
- 如果设置了 `LOG_LEVEL`，添加 `-Dorg.slf4j.simpleLogger.defaultLogLevel` 系统属性。
- 使用 `exec` 启动 Java 进程，确保信号正确传递。

### `docker/iceberg-rest-fixture/README.md` (+24/-2 lines)

**修改目的**：文档说明日志配置方法。

**工作逻辑**：新增 "Logging" 章节，说明：
- 通过 `LOG_LEVEL` 环境变量设置日志级别（WARN/ERROR/OFF），附示例。
- 通过挂载 `LOG_CONFIG_DIR` 目录使用完整的 `simplelogger.properties` 配置，附示例。
- 两者同时设置时 `LOG_LEVEL` 覆盖配置文件中的默认级别。

## 总结

这次提交为 iceberg-rest-fixture Docker 镜像添加了灵活的日志级别配置能力，通过 `LOG_LEVEL` 环境变量和 `LOG_CONFIG_DIR` 挂载目录两种方式，解决了 fixture 默认 INFO 级别日志过多的问题。这改善了开发和测试体验，使用户能够根据需要调整日志输出级别。
