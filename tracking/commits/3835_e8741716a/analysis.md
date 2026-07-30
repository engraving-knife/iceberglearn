# 提交 3835：Build: Bump nessie from 0.107.5 to 0.107.6 (#16703)

## 提交信息

- **序号**：3835 / 4088
- **哈希**：e8741716a39306c91b4580c640da61cbcc84dd54
- **短哈希**：e8741716a
- **日期**：2026-06-07 09:41:01 -0700
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.107.5 to 0.107.6 (#16703)
- **PR/Issue**：#16703

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目依赖的 Nessie 版本从 `0.107.5` 升级到 `0.107.6`。Nessie 是一个提供事务化目录服务（类似 Git 的数据版本控制）的 catalog 实现，Iceberg 的 `nessie` 模块（Nessie catalog 集成）依赖 Nessie 的客户端与测试扩展组件。本次升级覆盖四个 Nessie 组件：`nessie-client`（生产依赖）、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`（测试依赖）。这是一个 patch 级升级（0.107.5 → 0.107.6），通常包含 bug 修复与小改进，属于常规依赖维护，有助于及时获取 Nessie 上游修复。

## 如何达成设计目的

Dependabot 检测到 `gradle/libs.versions.toml` 中 `nessie` 版本有新发布，自动创建 PR 升级版本字符串。Iceberg 用一个 `nessie` 版本变量统一管理所有 Nessie 组件版本，因此修改一处即可让 `nessie-client` 与三个测试扩展组件同步升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Nessie 依赖版本。

**工作逻辑**：
将版本目录中的 Nessie 版本声明从 `0.107.5` 改为 `0.107.6`：
```toml
-nessie = "0.107.5"
+nessie = "0.107.6"
```
所有引用 `nessie` 版本的组件（`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`）会自动获得新版本。

## 总结

这是一次常规的 patch 级依赖升级，由 Dependabot 自动完成，改动仅一行。Nessie 是 Iceberg 支持的 catalog 实现之一，及时升级有助于获取上游 bug 修复与改进，保持 Nessie catalog 集成的健康。由于版本变量统一管理，影响范围可控。
