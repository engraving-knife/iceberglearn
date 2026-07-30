# 提交 3552：Docs: Add Apache Hive 4.2 to website (#15998)

## 提交信息

- **序号**：3552 / 4088
- **哈希**：53fde56a87d49517a9688fe73600eca10def1283
- **短哈希**：53fde56a8
- **日期**：2026-04-17 10:02:45 -0700
- **作者**：Shohei Okumiya
- **提交说明**：Docs: Add Apache Hive 4.2 to website (#15998)
- **PR/Issue**：#15998

## 总体目的

Apache Hive 4.2 已发布，它与 Hive 4.1.x 一样内置了 Iceberg 1.9.1 集成。Iceberg 官方网站的 Hive 文档和快速入门此前只覆盖到 Hive 4.1.x，未提及 4.2。本提交将文档更新为同时覆盖 Hive 4.1.x 和 4.2.x。

同时更新了 Hive 快速入门的 docker 示例：将默认版本从 4.0.0 改为 4.2.0，移除了针对 Apple Silicon (arm64) 的 `--platform linux/arm64` 平台参数说明（4.2.0 镜像应已支持多架构），并简化了相关说明文字。另外移除了硬编码的 Iceberg 版本号 "1.4.3"（改为泛指 "Iceberg"），并新增了一个指向 Hive 官方「使用 Hive Metastore 作为 Iceberg REST Catalog」文档的链接。

## 如何达成设计目的

1. 在 `docs/docs/hive.md` 中将 "Hive 4.1.x" 标题改为 "Hive 4.1.x, 4.2.x"，并更新描述说明两者都内置 Iceberg 1.9.1
2. 在 `site/docs/hive-quickstart.md` 中：
   - 将 `HIVE_VERSION=4.0.0` 改为 `4.2.0`
   - 简化 docker run 命令，移除 `--platform linux/arm64` 和相关说明
   - 移除硬编码 Iceberg 版本 "1.4.3"
   - 新增 Hive Metastore as REST Catalog 的文档链接

## 修改详情

### `docs/docs/hive.md` (+2/-2 lines)

**修改目的**：将 Hive 4.2 纳入文档覆盖范围。

**工作逻辑**：
```markdown
-### Hive 4.1.x
+### Hive 4.1.x, 4.2.x

-Hive 4.1.x comes with Iceberg 1.9.1 included.
+Hive 4.1.x and 4.2.x come with Iceberg 1.9.1 included.
```

### `site/docs/hive-quickstart.md` (+5/-6 lines)

**修改目的**：更新快速入门使用 Hive 4.2.0 并简化说明。

**工作逻辑**：
1. 版本变量：
```sh
-export HIVE_VERSION=4.0.0
+export HIVE_VERSION=4.2.0
```
2. docker run 命令简化（移除平台参数和相关说明文字）：
```sh
-docker run -d --platform linux/arm64 -p 10000:10000 -p 10002:10002 --env SERVICE_NAME=hiveserver2 --name hive4 apache/hive:${HIVE_VERSION}
+docker run -d -p 10000:10000 -p 10002:10002 --env SERVICE_NAME=hiveserver2 --name hive4 apache/hive:${HIVE_VERSION}
```
3. 移除硬编码 Iceberg 版本：
```markdown
-If you already have a Hive 4.0.0 or later environment, it comes with the Iceberg 1.4.3 included.
+If you already have a Hive 4.0.0 or later environment, it comes with the Iceberg included.
```
4. 新增 Hive Metastore as REST Catalog 文档链接：
```markdown
-...standalone metastore, HS2 and Postgres](...). Now that you're up and running...
+...standalone metastore, HS2 and Postgres](...) or [use Hive Metastore as Iceberg REST Catalog](https://hive.apache.org/docs/latest/admin/iceberg-rest-catalog/). Now that you're up and running...
```

## 总结

本提交将 Iceberg 官方网站的 Hive 文档更新为覆盖 Apache Hive 4.2（与 4.1.x 一样内置 Iceberg 1.9.1），并将快速入门的 docker 示例从 Hive 4.0.0 升级到 4.2.0，简化平台参数说明，移除硬编码 Iceberg 版本号，新增 Hive Metastore as REST Catalog 文档链接。属于文档维护，反映 Hive 4.2 的发布。
