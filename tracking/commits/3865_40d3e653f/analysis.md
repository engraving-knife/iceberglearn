# 提交 3865：CI: Retry Trivy scanner image pull to absorb transient Docker Hub timeouts (#16660)

## 提交信息

- **序号**：3865 / 4088
- **哈希**：40d3e653f18a0460ffe6f35c69d6e641cdfeecd5
- **短哈希**：40d3e653f
- **日期**：2026-06-11 23:02:14 -0700
- **作者**：Vova Kolmakov
- **提交说明**：CI: Retry Trivy scanner image pull to absorb transient Docker Hub timeouts (#16660)
- **PR/Issue**：#16660

## 总体目的

本提交修复了 CVE 扫描 CI 工作流中 Trivy 扫描器镜像拉取因 Docker Hub 超时而失败的问题。Iceberg 的 CVE 扫描工作流使用 Trivy 安全扫描器对构建产物进行漏洞扫描，Trivy 以 Docker 容器方式运行，需要从镜像仓库拉取。

在 GitHub-hosted runner 上，从 Docker Hub 拉取镜像经常遇到瞬时超时（exit code 125），导致 CI 不稳定。本提交通过两个措施解决：

1. **改用 GHCR 镜像源**：将 Trivy 镜像源从 Docker Hub 改为 GHCR（ghcr.io），GHCR 在 GitHub-hosted runner 上更可靠。镜像通过 digest 固定（`@sha256:...`）确保不可变。

2. **预拉取 + 重试**：在运行 Trivy action 之前，先手动 `docker pull` 镜像，带 5 次重试和递增退避（10s、20s、30s、40s），吸收瞬时超时。预拉取后 action 的 `docker run` 能直接使用本地镜像，不再访问仓库。

## 如何达成设计目的

在 `cve-scan.yml` 工作流中：
1. 新增 `TRIVY_IMAGE` 环境变量，指定 GHCR 镜像地址（含 digest）。
2. 新增 "Pull Trivy image (with retry)" 步骤，循环重试 `docker pull`。
3. 将 `trivy-image` 参数传给 Trivy action，覆盖其默认镜像。

## 修改详情

### `.github/workflows/cve-scan.yml` (+22/-0 lines)

**修改目的**：预拉取 Trivy 镜像并重试。

**工作逻辑**：

1. 新增 `TRIVY_IMAGE` 环境变量：
```yaml
env:
  TRIVY_IMAGE: ghcr.io/aquasecurity/trivy:0.69.3@sha256:bcc376de8d77cfe086a917230e818dc9f8528e3c852f7b1aff648949b6258d1c
```

2. 新增 "Pull Trivy image (with retry)" 步骤：
```bash
for attempt in 1 2 3 4 5; do
  if docker pull "${TRIVY_IMAGE}"; then
    exit 0
  fi
  if [ "${attempt}" = "5" ]; then
    break
  fi
  echo "docker pull failed (attempt ${attempt}/5); retrying in $((attempt * 10))s..." >&2
  sleep "$((attempt * 10))"
done
echo "Failed to pull ${TRIVY_IMAGE} after 5 attempts" >&2
exit 1
```
5 次重试，退避时间递增（10s、20s、30s、40s）。

3. Trivy action 新增 `trivy-image` 参数：
```yaml
trivy-image: ${{ env.TRIVY_IMAGE }}
```
使 action 使用预拉取的镜像而非默认从 Docker Hub 拉取。

## 总结

本提交通过改用 GHCR 镜像源和预拉取重试机制，解决了 CVE 扫描 CI 因 Docker Hub 超时而不稳定的问题。重试机制使用 5 次递增退避（最长 40s），能有效吸收瞬时网络问题。预拉取确保 action 运行时镜像已在本地，避免运行时拉取失败。这是 CI 稳定性提升的重要改进。
