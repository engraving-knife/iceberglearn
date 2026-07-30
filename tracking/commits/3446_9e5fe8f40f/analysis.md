# 提交 3446：Infra: Pin versions in publish-iceberg-rest-fixture-docker.yml (#15730)

## 提交信息

- **序号**：3446 / 4088
- **哈希**：9e5fe8f40f6dde1b6e23b97dfac52ade37313f89
- **短哈希**：9e5fe8f40f
- **日期**：2026-03-23 11:26:34 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Infra: Pin versions in publish-iceberg-rest-fixture-docker.yml (#15730)
- **PR/Issue**：#15730

## 总体目的

将 Docker 发布工作流中使用的第三方 GitHub Actions 从版本标签（如 `@v4`）固定到特定的 commit SHA，以提高 CI/CD 安全性。这是 Apache 项目安全加固的一部分，防止供应链攻击通过修改 action 标签指向恶意代码。

## 如何达成设计目的

- 将三个 Docker 相关的 GitHub Actions 从版本标签改为固定 commit SHA：
  - `docker/setup-qemu-action`
  - `docker/setup-buildx-action`
  - `docker/build-push-action`

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+3/-3 lines)

**修改目的**：将 Docker 相关 GitHub Actions 固定到特定 commit SHA。

**工作逻辑**：
- `docker/setup-qemu-action@v4` → `docker/setup-qemu-action@ce360397dd3f832beb865e1373c09c0e9f86d70a`
- `docker/setup-buildx-action@v4` → `docker/setup-buildx-action@4d04d5d9486b7bd6fa91e7baf45bbb4f8b9deedd`
- `docker/build-push-action@v7` → `docker/build-push-action@d08e5c354a6adb9ed34480a06d141179aa583294`

使用 commit SHA 而非版本标签可以确保 action 内容不可被篡改，因为 SHA 是内容寻址的，而标签可以被重新指向。

## 总结

该提交将 REST fixture Docker 发布工作流中的三个第三方 GitHub Actions 从版本标签固定到特定 commit SHA，是 Apache 项目 CI/CD 安全加固的一部分，防止供应链攻击。
