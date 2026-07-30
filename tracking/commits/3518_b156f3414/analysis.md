# 提交 3518：pass dockerhub token the safely (#15940)

## 提交信息

- **序号**：3518 / 4088
- **哈希**：b156f3414e402aa4c0aea5eaa397f34da23e4a05
- **短哈希**：b156f3414
- **日期**：2026-04-11 20:06:13 -0700
- **作者**：Dhruv Arya
- **提交说明**：pass dockerhub token the safely (#15940)
- **PR/Issue**：#15940

## 总体目的

在发布 Iceberg REST Fixture Docker 镜像的 GitHub Actions 工作流中，原先使用 `docker login -u "$DOCKERHUB_USER" -p "$DOCKERHUB_TOKEN"` 的方式登录 Docker Hub。通过命令行参数 `-p` 传递密码/令牌是不安全的做法：在进程列表（`ps`）、shell 历史以及日志中可能泄露凭据。

Docker 官方推荐使用 `--password-stdin` 通过标准输入传递密码，这样可以避免凭据出现在命令行参数中，是更安全的登录方式。本提交就是将登录命令改为这种安全写法。

## 如何达成设计目的

将 `docker login` 命令改为通过管道把 token 注入 stdin：
```bash
echo "$DOCKERHUB_TOKEN" | docker login --username "$DOCKERHUB_USER" --password-stdin
```
这样 token 只通过 stdin 传入 docker 进程，不会出现在命令行参数中，也不会被 `ps` 等工具捕获。同时使用 `--username`/`--password-stdin` 的长选项形式，可读性更好。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：用 `--password-stdin` 的方式安全传递 Docker Hub token。

**工作逻辑**：
```yaml
-        docker login -u "$DOCKERHUB_USER" -p "$DOCKERHUB_TOKEN"
+        echo "$DOCKERHUB_TOKEN" | docker login --username "$DOCKERHUB_USER" --password-stdin
```
环境变量定义（`DOCKERHUB_USER`、`DOCKERHUB_TOKEN`）保持不变，仅修改登录命令本身。

## 总结

这是一个安全相关的 CI 改进提交，将 Docker Hub 登录方式从命令行参数传递 token 改为通过 stdin 传递，避免凭据泄露到进程列表或日志中。改动极小但符合 Docker 官方的安全最佳实践。
