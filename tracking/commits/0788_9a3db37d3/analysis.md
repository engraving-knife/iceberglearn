# 提交 0788：Build: Bump io.netty:netty-buffer from 4.1.109.Final to 4.1.110.Final (#10384)

## 提交信息

- **序号**：0788 / 4088
- **哈希**：9a3db37d393b4eb7a270ffef14f2a6a5ed6c71da
- **短哈希**：9a3db37d3
- **日期**：2024-05-27 12:30:43 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.109.Final to 4.1.110.Final (#10384)
- **PR/Issue**：#10384
- **提交正文摘要**：Dependabot 自动生成，`dependency-type: direct:production`，`update-type: version-update:semver-patch`，链接了 netty/netty 仓库 `netty-4.1.109.Final...netty-4.1.110.Final` 的 compare 页面。

## 总体目的

由 Dependabot 自动发起的依赖版本升级，将 Netty 的 `netty-buffer` 模块从 `4.1.109.Final` 升至 `4.1.110.Final`。`netty-buffer` 提供 `ByteBuf` 及其池化实现，是 Iceberg 在网络/IO 相关路径（如 S3 客户端底层传输、ORC/Parquet 读写缓冲等场景间接使用）使用的底层缓冲区库。本次为同一 `4.1.x` 修订线内的补丁级递增（patch +1），主要获取缺陷修复与小幅稳定性改进。

注意本次提交同时升级了两个相关版本变量：`netty-buffer` 与 `netty-buffer-compat`，二者保持版本一致（均为 `4.1.110.Final`），以避免 compat 兼容模块与主模块版本错配。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml`，将 `[versions]` 节中的 `netty-buffer` 与 `netty-buffer-compat` 两个 key 的版本字面量同步从 `"4.1.109.Final"` 改为 `"4.1.110.Final"`。版本目录的集中式管理让两处保持对齐，下游模块引用 `${libs.versions.netty-buffer}` / `${libs.versions.netty-buffer-compat}` 时自动获得新版本，无需逐模块修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `netty-buffer` 与 `netty-buffer-compat` 版本号从 `4.1.109.Final` 升级到 `4.1.110.Final`。

**工作逻辑**：在 `[versions]` 节中，原两行
```toml
netty-buffer = "4.1.109.Final"
netty-buffer-compat = "4.1.109.Final"
```
修改为
```toml
netty-buffer = "4.1.110.Final"
netty-buffer-compat = "4.1.110.Final"
```
两行紧邻，分别对应主缓冲模块与兼容模块（compat 用于向旧 `ByteBuf` API 的过渡兼容）。二者必须保持版本一致，否则可能出现 API 不匹配。本次同步升级，符合该约束。

统计：1 file changed, 2 insertions(+), 2 deletions(-)。

## 小结

- **成效**：将 `netty-buffer` 与 `netty-buffer-compat` 同步升级至 `4.1.110.Final`，获取补丁级修复，保持两模块版本对齐避免兼容性问题。Dependabot 标注为生产直接依赖、补丁级升级，风险可控。
- **影响范围**：仅依赖版本配置改动，无源码、API 改动。影响所有引用 `netty-buffer` / `netty-buffer-compat` 的模块的构建产物依赖版本。Netty 4.1.x 保持二进制兼容，运行时行为预期无破坏性变化。
- **回迁注意事项**：回迁到 1.4.x 分支无障碍，仅需修改同样的两行版本号。需注意 1.4.x 分支上 `netty-buffer-compat` 行是否仍存在（若 1.4.x 分支已移除 compat 模块，则只改 `netty-buffer` 一行）。Netty 4.1.110.Final 对 JDK 8+ 兼容，与 1.4.x 的 JDK 基线无冲突。建议回迁后执行一次构建确认 netty 依赖能正常解析。
