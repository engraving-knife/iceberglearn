# 提交 1572 dc47b854a 分析

## 提交信息
- 哈希：dc47b854ac491b955be633b16d8e3498acf2a317
- 日期：2025-01-13（Mon Jan 13 05:42:26 2025 -0600）
- 作者：Neodon <82944+neodon@users.noreply.github.com>
- 消息：docs: Use the YAML multi-line indicator (#11552)

## 总体目的

本提交修复 Spark 快速上手指南（`site/docs/spark-quickstart.md`）中 docker-compose 示例的 YAML 多行字符串语法错误。在原示例中，`entrypoint` 字段使用了一个多行脚本来初始化 MinIO 服务（创建 warehouse bucket、设置访问策略等），但该字符串被错误地以 YAML 的"折叠样式"（folded style）指示符 `>` 开头，而非"字面样式"（literal style）指示符 `|`。

按照 YAML 1.2.2 规范，折叠样式 `>` 会将连续的非空行合并为单行（用空格连接），这会导致脚本被压缩成一行而无法正确执行；而字面样式 `|` 则保留每行的换行符，保持脚本原始格式。该问题虽然不影响文档的可读性，但会让读者直接复制粘贴示例时得到一个行为异常的 compose 文件，影响首次使用体验。

作者 Neodon 在提交说明中引用了 YAML 规范的两个章节：折叠样式（§8.1.3）和字面样式（§8.1.2），让维护者清楚理解变更依据。这是一个典型的小范围文档质量修复，提升官方示例的可执行性。

## 如何达成设计目的

通过修改 `site/docs/spark-quickstart.md` 中 docker-compose 示例的 `entrypoint` 行，把多行字符串的指示符从 `>` 改为 `|`。这样 YAML 解析器在加载该文件时，会把整个 `/bin/sh -c "..."` 脚本作为字面量多行字符串保留，每条 shell 命令仍各占一行，符合 entrypoint 脚本本来的运行时语义。

### 修改详情

#### `site/docs/spark-quickstart.md`

**修改目的**：修复 docker-compose 示例中 entrypoint 多行脚本的 YAML 标量类型。

**工作逻辑**：
```yaml
-    entrypoint: >
+    entrypoint: |
       /bin/sh -c "
       until (/usr/bin/mc config host add minio http://minio:9000 admin password) do echo '...waiting...' && sleep 1; done;
       /usr/bin/mc rm -r --force minio/warehouse;
       ...
```

把 `>` 改为 `|` 之后，YAML 解析器会按"字面样式"处理后续缩进的行，保留所有换行符。`/bin/sh -c "..."` 命令会作为一段完整的多行 shell 脚本传给容器的 entrypoint，循环等待、`mc` 命令等仍逐行执行；否则在折叠样式下整段脚本会被压成一行，shell 解析会失败或行为异常。

## 小结

- **成效**：Spark 快速上手指南的 docker-compose 示例恢复为正确的 YAML 字面多行字符串，读者复制粘贴即可正确启动 MinIO 容器并初始化 warehouse bucket。
- **影响范围**：仅文档 `site/docs/spark-quickstart.md` 一行，单字符级修改，无代码或构建逻辑变更。
- **回迁到 1.4.x 的注意事项**：纯文档修复，与运行时无关。1.4.x 作为维护分支，文档通常由 main 统一维护。如 1.4.x 也保留该 quickstart 文档（且包含同样问题），可以一并修；否则**无需回迁**，对发布产物无影响。
