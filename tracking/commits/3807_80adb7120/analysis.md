# 提交 3807：Build: Bump docker/build-push-action from 7.1.0 to 7.2.0 (#16631)

## 提交信息

- **序号**：3807 / 4088
- **哈希**：80adb7120909543b7ed03ea3e29fbbf0b2d10b5c
- **短哈希**：80adb7120
- **日期**：2026-05-31 08:41:59 -0700
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump docker/build-push-action from 7.1.0 to 7.2.0 (#16631)
- **PR/Issue**：#16631

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 仓库 `publish-iceberg-rest-fixture-docker.yml` 工作流中使用的 `docker/build-push-action` 从 `7.1.0` 升级到 `7.2.0`。`docker/build-push-action` 是 Docker 官方维护的 GitHub Action，用于构建并推送 Docker 镜像。Iceberg 用它来发布 REST catalog 的 fixture Docker 镜像。这是一个 minor 级升级（7.1.0 → 7.2.0），可能引入新的构建选项或修复，属于 CI/CD 基础设施的常规维护。

## 如何达成设计目的

Dependabot 检测到工作流中 `uses: docker/build-push-action@<pin>` 的版本注释有新发布，自动创建 PR 升级 commit pin 和版本注释。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：升级 Docker build-push Action 版本。

**工作逻辑**：
将 Action 引用从旧 commit pin（`bcafcacb16a39f128d818304e6c9c0c18556b85f # v7.1.0`）更新为新 commit pin（`f9f3042f7e2789586610d6e8b85c8f03e5195baf # v7.2.0`）：
```yaml
-      uses: docker/build-push-action@bcafcacb16a39f128d818304e6c9c0c18556b85f # v7.1.0
+      uses: docker/build-push-action@f9f3042f7e2789586610d6e8b85c8f03e5195baf # v7.2.0
```

## 总结

这是一次 CI/CD 基础设施的常规 minor 级升级，由 Dependabot 自动完成，风险低。升级后 Iceberg REST fixture Docker 镜像的构建与推送流程将基于 `docker/build-push-action` 7.2.0。
