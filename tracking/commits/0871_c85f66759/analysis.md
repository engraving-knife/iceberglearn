# 提交 0871：Build: Bump Nessie to 0.91.2 (#10563)

## 提交信息

- **序号**：0871 / 4088
- **哈希**：c85f66759e112dd1a02d1636ffe0814f514e64c9
- **短哈希**：c85f66759
- **日期**：2024-06-25（Tue Jun 25 12:17:04 2024 +0200）
- **作者**：Alexandre Dutra <adutra@users.noreply.github.com>
- **提交说明**：Build: Bump Nessie to 0.91.2 (#10563)
- **PR/Issue**：#10563

## 总体目的

紧接上一个提交 #10551（提交 0867）把 Nessie 从 0.90.4 升到 0.91.1 之后，Nessie 项目又发布了 0.91.2 patch 版本。本提交由人工（非 dependabot）发起，目的是把 `gradle/libs.versions.toml` 中 `nessie` 变量从 0.91.1 进一步升级到 0.91.2，跟进上游 patch 修复。

与 dependabot 自动升级不同，本提交由作者 Alexandre Dutra 手动发起（推测该作者来自 Nessie 项目方），可能针对 0.91.1 引入的某些问题做了快速跟进。这次升级是 patch 级别（0.91.1 → 0.91.2），按 semver 应仅含 bug fix，向后兼容性更好。

## 如何达成设计目的

实现方式非常直接：修改 `gradle/libs.versions.toml` 中 `nessie` 变量的版本钉，从 `0.91.1` 改为 `0.91.2`。该变量被 version catalog 中所有 nessie 相关工件坐标引用，统一对齐（包括 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Nessie 相关 4 个工件的统一版本从 0.91.1 升级到 0.91.2。

**工作逻辑**：仅修改一行，diff 如下：

```diff
 microprofile-openapi-api = "3.1.1"
 mockito = "4.11.0"
 mockserver = "5.15.0"
-nessie = "0.91.1"
+nessie = "0.91.2"
 netty-buffer = "4.1.111.Final"
 netty-buffer-compat = "4.1.111.Final"
 object-client-bundle = "3.3.2"
```

被该变量影响的工件包括运行时客户端 `nessie-client`（被 `NessieCatalog` 使用）以及三个测试扩展（`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`）。

## 小结

- **成效**：把 Project Nessie 相关 4 个工件版本从 0.91.1 升级到 0.91.2，跟进上游 patch 修复。这是对 #10551（提交 0867）的跟进升级。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动。运行时影响：使用 `NessieCatalog` 对接 Nessie 服务的用户会切换到 Nessie 0.91.2 客户端；测试影响：所有 Nessie 相关测试扩展会切换版本。
- **回迁到 1.4.x 的注意事项**：本提交是 Nessie patch 版本升级，**适合回迁**到 1.4.x 分支，特别是当 1.4.x 用户依赖 Nessie 集成时。注意事项：(1) 与 0867 类似，需确认 Iceberg `nessie` 模块代码在 Nessie 0.91.2 客户端 API 下编译通过、测试通过；(2) 如果 1.4.x 回迁了 0867（升到 0.91.1），则本提交作为后续 patch 升级也应一并回迁，最终 1.4.x 上的 Nessie 版本为 0.91.2；(3) 如果 1.4.x 选择跳过 0867 直接回迁本提交，需注意从原版本（可能仍是 0.90.4）跨 minor 升级到 0.91.2 的兼容性评估更复杂；(4) 该升级不依赖其他提交，可独立 cherry-pick；与 awssdk-bom 升级（提交 0866）需注意 Nessie 传递依赖的 awssdk 版本会被 BOM 覆盖。
