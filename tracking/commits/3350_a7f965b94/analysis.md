# 提交 3350：Build: Bump docker/build-push-action from 6 to 7 (#15532)

## 提交信息

- **序号**：3350 / 4088
- **哈希**：a7f965b941e05853a273d3d5ec3da2e447960dc4
- **短哈希**：a7f965b94
- **日期**：2026-03-07 23:14:45 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump docker/build-push-action from 6 to 7 (#15532)
- **PR/Issue**：#15532

## 总体目的

`docker/build-push-action` 是 Docker 官方维护的 GitHub Action，用于在 CI 中构建并推送 Docker 镜像。Iceberg 在 `.github/workflows/publish-iceberg-rest-fixture-docker.yml` 工作流中用它把 `iceberg-rest-fixture`（OpenAPI REST Catalog 的参考实现 fixture 镜像）构建并发布到镜像仓库，供下游测试与集成使用。

本次 dependabot 把该 action 从大版本 `v6` 升级到 `v7`（语义版本 semver-major 升级）。大版本升级通常意味着 action 可能引入了不兼容变更或新默认行为，因此需要主动跟进以获取上游修复与新特性、避免使用已 EOL 的旧版本。由于 action 的输入接口在工作流中只用到 `context`、`file` 等基础参数，升级风险较低。

## 如何达成设计目的

仅需把工作流中 `uses:` 引用的版本标签从 `@v6` 改为 `@v7`，无需改动任何业务逻辑。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：把 build-push-action 升级到 v7。

**工作逻辑**：`Build and Push` 步骤由 `docker/build-push-action@v6` 改为 `@v7`，`with` 块的 `context` / `file` 等参数保持不变。由于本次升级发生在 `Set up Docker Buildx`（仍是 v3，随后由 3351 提交升到 v4）之后，buildx 已就绪，v7 的 build-push-action 可正常工作。

## 总结

本提交把发布 iceberg-rest-fixture 镜像所用到的 docker/build-push-action 从 v6 升到 v7（semver-major），让 CI 镜像发布流水线跟上上游主版本，获取修复与新特性。改动仅一行版本标签，业务参数不变。
