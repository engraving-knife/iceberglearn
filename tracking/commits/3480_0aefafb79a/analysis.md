# 提交 3480：add tag as inline comment (#15805)

## 提交信息

- **序号**：3480 / 4088
- **哈希**：0aefafb79a7396ab64125b6ed78384734046e40e
- **短哈希**：0aefafb79a
- **日期**：2026-03-27 22:59:32 -0700
- **作者**：Kevin Liu
- **提交说明**：add tag as inline comment (#15805)
- **PR/Issue**：#15805

## 总体目的

为 GitHub Actions 工作流中使用的若干第三方 action 添加版本标签注释（inline comment）。这些 action 此前仅以 commit SHA 引用（这是安全最佳实践，确保不可变引用），但没有标注对应的版本号，导致可读性差。添加 `# vX.Y.Z` 注释可以同时保持 SHA 引用的安全性和版本号的可读性。

这与 zizmor 安全扫描（#15793）相关，zizmor 会检查 action 引用方式，添加版本注释有助于审计和追踪。

## 如何达成设计目的

在两个工作流文件中，为使用 commit SHA 引用的第三方 action 添加 `# vX.Y.Z` 版本注释。

## 修改详情

### `.github/workflows/open-api.yml` (+1/-1 lines)

**修改目的**：为 `astral-sh/setup-uv` action 添加版本注释。

**工作逻辑**：
```yaml
-        uses: astral-sh/setup-uv@37802adc94f370d6bfd71619e3f0bf239e1f3b78
+        uses: astral-sh/setup-uv@37802adc94f370d6bfd71619e3f0bf239e1f3b78 # v7.6.0
```

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+3/-3 lines)

**修改目的**：为三个 docker 相关 action 添加版本注释。

**工作逻辑**：
- `docker/setup-qemu-action@ce36039...` 添加 `# v4.0.0`
- `docker/setup-buildx-action@4d04d5d...` 添加 `# v4.0.0`
- `docker/build-push-action@d08e5c3...` 添加 `# v7.0.0`

## 总结

这是一个 CI 配置可读性改进提交，为使用 commit SHA 引用的第三方 GitHub Actions 添加版本号注释，既保持了 SHA 引用的安全性（不可变、抗供应链攻击），又提升了可读性和可审计性。这与新增的 zizmor 安全扫描工作流配合使用。
