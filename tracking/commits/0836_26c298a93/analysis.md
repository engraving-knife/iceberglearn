# 提交 0836：Build: Bump io.netty:netty-buffer from 4.1.110.Final to 4.1.111.Final (#10504)

## 提交信息
- **序号**：0836 / 4088
- **哈希**：26c298a934899cb7caf0af69f38f1da6563b9b01
- **短哈希**：26c298a93
- **日期**：2024-06-16
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.110.Final to 4.1.111.Final (#10504)
- **PR/Issue**：#10504

## 总体目的

本提交由 Dependabot 自动生成，目的是将 Iceberg 项目中使用的 `io.netty:netty-buffer` 依赖从 4.1.110.Final 升级到 4.1.111.Final。Netty 是一个高性能、异步事件驱动的网络应用框架，其 `netty-buffer` 模块提供了 ByteBuf 等缓冲区相关的基础能力，被广泛用于各类网络和数据传输场景。

Iceberg 在运行时依赖 `netty-buffer` 来支撑其底层 I/O 与缓冲区处理（例如在 S3、GCS 等存储客户端以及 ORC/Parquet 等读写链路中通过传递依赖被引入）。该升级属于补丁版本（patch）升级，根据 SemVer 语义化版本规范，4.1.110 到 4.1.111 之间的变更仅包含向后兼容的缺陷修复或内部改进，不会引入破坏性 API 变更。

Dependabot 通过监控 Maven Central 上依赖的最新发布版本，自动生成 Pull Request 并附带更新日志链接，由维护者评审合并。这类提交是项目持续保持依赖新鲜度、获取上游安全修复与稳定性改进的常规机制。

## 如何达成设计目的

提交通过修改 Gradle 的版本目录文件 `gradle/libs.versions.toml` 来统一升级 netty-buffer 的版本。Iceberg 使用 Gradle 的 Version Catalog 机制集中管理依赖版本，所有子模块通过引用版本目录中定义的别名（alias）来获取依赖坐标，这样只需在一处修改即可让整个构建树中所有引用 `netty-buffer` 的模块同步升级，避免多处硬编码版本导致的不一致。

具体改动同时更新了两个版本变量：
- `netty-buffer`：常规依赖使用的版本
- `netty-buffer-compat`：兼容性变体使用的版本

两者都同步从 4.1.110.Final 提升到 4.1.111.Final，保持主依赖与 compat 版本一致，这是 Iceberg 维护 netty-buffer 时的既定模式（compat 版本用于在某些与旧版 netty 4.0.x 不兼容的环境下提供过渡性兼容）。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：将 netty-buffer 与 netty-buffer-compat 的版本号从 4.1.110.Final 升级到 4.1.111.Final。

**工作逻辑**：版本目录中的版本定义条目修改如下：

```toml
netty-buffer = "4.1.110.Final"          # 旧
netty-buffer-compat = "4.1.110.Final"   # 旧

netty-buffer = "4.1.111.Final"          # 新
netty-buffer-compat = "4.1.111.Final"   # 新
```

下方的依赖坐标声明（`netty-buffer = { module = "io.netty:netty-buffer", version.ref = "netty-buffer" }` 和 `netty-buffer-compat = { module = "io.netty:netty-buffer", version.ref = "netty-buffer-compat" }`）通过 `version.ref` 引用上述版本变量，因此无需修改即可自动指向新版本。所有通过 `libs.netty.buffer` / `libs.netty.buffer.compat` 别名引入该依赖的子模块在重新构建时即可拉取到 4.1.111.Final 版本。

## 小结
- **成效**：将 netty-buffer 升级到 4.1.111.Final，获取上游在 4.1.110 至 4.1.111 之间的缺陷修复和稳定性改进，保持依赖新鲜度。
- **影响范围**：仅影响构建依赖版本，不修改任何源代码；所有传递或直接使用 netty-buffer 的模块都会在下次构建时拉取新版本，无 API 层面的破坏性变更。
- **回迁注意事项**：回迁到 1.4.x 时直接将 `gradle/libs.versions.toml` 中 `netty-buffer` 和 `netty-buffer-compat` 改为 `4.1.111.Final` 即可。若 1.4.x 分支当前使用的是更早的版本（例如 4.1.97.Final 等），建议先核对是否存在其他模块对 netty 4.1.110 才存在的 API 依赖，避免一次性跨多个版本回迁引入意外兼容问题；正常情况下补丁版本升级可直接回迁，无需额外代码改动。
