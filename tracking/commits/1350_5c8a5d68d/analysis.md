# 提交 1350：Docs: Fix verifying release candidate with Spark and Flink (#11461)

## 提交信息

- **序号**：1350 / 4088
- **哈希**：5c8a5d68d43bc9d32fe5cb272be7ea01b95a7251
- **短哈希**：5c8a5d68d
- **日期**：2024-11-08（Fri Nov 8 00:50:10 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Fix verifying release candidate with Spark and Flink (#11461)
- **PR/Issue**：#11461

## 总体目的

`site/docs/how-to-release.md` 是 Iceberg 发布流程文档，其中"Verifying with Spark"和"Verifying with Flink"两节给出了发布候选（RC）验证时启动 Spark shell 与 Flink SQL Client 的示例命令。社区成员按这些命令拉取对应版本的 Iceberg jar 并启动引擎验证功能。

随着 Iceberg 在多 Spark/Flink 版本并行支持上的演进，发布产物的 artifactId 命名规则发生了变化：
- **Spark**：早期统一发布为 `iceberg-spark3-runtime`，后来改为按 Spark 版本和 Scala 版本拆分，如 `iceberg-spark-runtime-3.5_2.12`（Spark 3.5 + Scala 2.12）。
- **Flink**：早期为 `iceberg-flink-runtime`，后来按 Flink 版本拆分，如 `iceberg-flink-runtime-1.20`（Flink 1.20）。

文档中的示例命令仍引用旧 artifactId，且使用了未在上下文定义的占位变量（如 `${LOCAL_WAREHOUSE_PATH}`、`${FLINK_CONNECTOR_PACKAGE}`、`${HIVE_VERSION}`、`${SCALA_VERSION}`、`${FLINK_VERSION}`），导致验证者按文档执行时无法找到 jar 或变量未定义。

本提交把示例命令更新为：
1. 使用具体的 Spark 3.5 / Scala 2.12 artifactId `iceberg-spark-runtime-3.5_2.12`；
2. 把 `${LOCAL_WAREHOUSE_PATH}` 替换为具体的 `$PWD/warehouse`；
3. 把 Flink artifactId 改为 `iceberg-flink-runtime-1.20`；
4. 把 Flink Hive connector 占位变量替换为具体的 `flink-connector-hive_2.12-1.20.jar`。

## 如何达成设计目的

直接修改 `site/docs/how-to-release.md` 中两段 bash 代码块，把过时的 artifactId 与未定义变量替换为与当前发布产物一致的、可直接复制运行的命令。改动不引入新文档结构，仅修正命令字符串。

## 修改详情

### `site/docs/how-to-release.md`

**修改目的**：修正"Verifying with Spark"与"Verifying with Flink"两节示例命令。

**工作逻辑**：

Spark 部分原命令：
```bash
spark-shell \
    --conf spark.jars.repositories=${MAVEN_URL} \
    --packages org.apache.iceberg:iceberg-spark3-runtime:{{ icebergVersion }} \
    --conf spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions \
    --conf spark.sql.catalog.local=org.apache.iceberg.spark.SparkCatalog \
    --conf spark.sql.catalog.local.type=hadoop \
    --conf spark.sql.catalog.local.warehouse=${LOCAL_WAREHOUSE_PATH} \
    --conf spark.sql.catalog.local.default-namespace=default \
    --conf spark.sql.defaultCatalog=local
```

改为：
```bash
spark-shell \
    --conf spark.jars.repositories=${MAVEN_URL} \
    --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:{{ icebergVersion }} \
    --conf spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions \
    --conf spark.sql.catalog.local=org.apache.iceberg.spark.SparkCatalog \
    --conf spark.sql.catalog.local.type=hadoop \
    --conf spark.sql.catalog.local.warehouse=$PWD/warehouse \
    --conf spark.sql.catalog.local.default-namespace=default \
    --conf spark.sql.defaultCatalog=local
```

变化点：
- `iceberg-spark3-runtime` → `iceberg-spark-runtime-3.5_2.12`（新 artifactId 命名）。
- `${LOCAL_WAREHOUSE_PATH}` → `$PWD/warehouse`（具体路径，避免未定义变量）。

Flink 部分原命令：
```bash
wget ${MAVEN_URL}/iceberg-flink-runtime/{{ icebergVersion }}/iceberg-flink-runtime-{{ icebergVersion }}.jar

sql-client.sh embedded \
    -j iceberg-flink-runtime-{{ icebergVersion }}.jar \
    -j ${FLINK_CONNECTOR_PACKAGE}-${HIVE_VERSION}_${SCALA_VERSION}-${FLINK_VERSION}.jar \
    shell
```

改为：
```bash
wget ${MAVEN_URL}/iceberg-flink-runtime-1.20/{{ icebergVersion }}/iceberg-flink-runtime-1.20-{{ icebergVersion }}.jar

sql-client.sh embedded \
    -j iceberg-flink-runtime-1.20-{{ icebergVersion }}.jar \
    -j flink-connector-hive_2.12-1.20.jar \
    shell

```

变化点：
- `iceberg-flink-runtime` → `iceberg-flink-runtime-1.20`（新 artifactId，含 Flink 版本）。
- `${FLINK_CONNECTOR_PACKAGE}-${HIVE_VERSION}_${SCALA_VERSION}-${FLINK_VERSION}.jar` → `flink-connector-hive_2.12-1.20.jar`（具体 jar 名）。

**注意**：本提交在 Flink 代码块末尾把原本的闭合 ```` ``` ```` 误删为一个空行，导致该 bash 代码块在 markdown 渲染时未正确闭合，后续的 `## Voting` 标题会被某些渲染器视为仍在代码块内。这是一个文档格式小回归，建议后续提交修复。

## 小结

- **成效**：发布候选验证文档现使用与当前发布产物一致的 Spark/Flink artifactId 与具体路径/文件名，验证者可直接复制命令运行，不再因 artifactId 不存在或变量未定义而失败。
- **影响范围**：仅 `site/docs/how-to-release.md` 一个文档文件，6 行字符串替换，无代码、构建或测试变更。
- **回迁到 1.4.x 的注意事项**：
  - 这是文档修复，与产品版本功能无关，对 1.4.x 运行时无影响。
  - 1.4.x 作为维护分支，其发布流程文档由 main 分支统一维护并作用于整个项目站点，**无需单独回迁**。即使 1.4.x 分支的该文件与 main 不同，发布文档以 main 为准。
  - 若 1.4.x 自行发布 RC 且文档与 main 不同步，可考虑同步本修复，但需注意 1.4.x 对应的 Spark/Flink 版本可能与 main 的 3.5/1.20 不同，应按 1.4.x 实际支持的版本替换 artifactId。
  - 注意本提交引入的代码块未闭合小问题，回迁时可一并修复（补回闭合 ```` ``` ````）。
