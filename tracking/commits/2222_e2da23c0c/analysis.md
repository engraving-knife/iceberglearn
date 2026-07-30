# 提交 2222：Docs: use latest minio client command configuration parameters (#13221)

## 提交信息

- **序号**：2222 / 4088
- **哈希**：e2da23c0cd53a46e28a59149f6199c235668315f
- **短哈希**：e2da23c0c
- **日期**：2025-06-07 09:31:12 +1000
- **作者**：Jerry Chen
- **提交说明**：Docs: use latest minio client command configuration parameters (#13221)
- **PR/Issue**：#13221

## 总体目的

这个提交是文档修复，将 Spark 快速入门文档中 docker-compose.yml 示例里的 MinIO 客户端（mc）命令从旧版的 `mc config host add` 更新为新版的 `mc alias set`。MinIO 客户端在新版本中废弃了 `config host add` 子命令，改用 `alias set` 来配置主机别名。使用旧命令在新版 mc 中会报错或产生废弃警告，导致快速入门指南中的 docker-compose 配置无法正常工作。此修复确保文档与最新版 MinIO 客户端兼容。

## 如何达成设计目的

- 在 `site/docs/spark-quickstart.md` 的 docker-compose.yml 代码块中，将 `mc config host add minio http://minio:9000 admin password` 替换为 `mc alias set minio http://minio:9000 admin password`。

## 修改详情

### `site/docs/spark-quickstart.md` (修改, +1/-1 line)

**修改目的**：更新 MinIO 客户端命令为新版语法。

**工作逻辑**：将 MinIO 初始化 entrypoint 脚本中的 `mc config host add` 命令替换为 `mc alias set`。`mc alias set` 是 MinIO 客户端当前推荐的配置主机别名的方式，功能等价但语法更新。其余命令（`mc rm`、`mc mb`、`mc policy set`）保持不变。

## 总结

该提交是单行文档修复，将 Spark 快速入门文档中 MinIO 客户端的 `mc config host add` 命令更新为 `mc alias set`，使文档与最新版 MinIO 客户端兼容。改动简单但解决了实际使用中的兼容性问题。
