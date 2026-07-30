# 提交 1170：Build: Bump org.xerial.snappy:snappy-java from 1.1.10.6 to 1.1.10.7 (#11140)

## 提交信息

- **序号**：1170 / 4088
- **哈希**：d4af40c9bc2b0d5c6271056f8f10b3c85fbe694e
- **短哈希**：d4af40c9b
- **日期**：2024-09-20（Fri Sep 20 11:03:33 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.xerial.snappy:snappy-java from 1.1.10.6 to 1.1.10.7 (#11140)
- **PR/Issue**：#11140
- **提交类型**：dependabot 自动依赖升级（semver-patch）

## 总体目的

`org.xerial.snappy:snappy-java` 是 Snappy 压缩算法的 Java 绑定，广泛用于 Hadoop/Avro/Parquet 生态做数据压缩。Iceberg 的 `kafka-connect` 模块在打包 `iceberg-kafka-connect-runtime` 时，通过 `resolutionStrategy.force` 强制把传递依赖中的 `snappy-java` 锁定到特定版本，以修复已知漏洞（CVE）或兼容性问题。

此次 dependabot 把锁定的 `snappy-java` 版本从 `1.1.10.6` 升级到 `1.1.10.7`，属于 patch 级别升级，主要目的是跟进上游修复。根据 dependabot 元数据：

- **dependency-type**: `direct:production`
- **update-type**: `version-update:semver-patch`

即生产环境直接依赖的 semver patch 升级，通常包含 bug 修复与安全补丁，无 API 破坏。

## 如何达成设计目的

通过 1 处修改完成：在 `kafka-connect/build.gradle` 的 `iceberg-kafka-connect-runtime` 子项目配置中，把 `resolutionStrategy.force` 中的 `snappy-java` 版本字符串从 `1.1.10.6` 改为 `1.1.10.7`。这是纯粹的版本字符串修改，无逻辑变化。

## 修改详情

### `kafka-connect/build.gradle`

**修改目的**：升级 `iceberg-kafka-connect-runtime` 强制锁定的 `snappy-java` 版本。

**工作逻辑**：

修改前（在 `project(':iceberg-kafka-connect:iceberg-kafka-connect-runtime')` 块内）：

```groovy
resolutionStrategy {
  force 'org.codehaus.jettison:jettison:1.5.4'
  force 'org.xerial.snappy:snappy-java:1.1.10.6'
  force 'org.apache.commons:commons-compress:1.27.1'
  force 'org.apache.hadoop.thirdparty:hadoop-shaded-guava:1.2.0'
}
```

修改后：

```groovy
resolutionStrategy {
  force 'org.codehaus.jettison:jettison:1.5.4'
  force 'org.xerial.snappy:snappy-java:1.1.10.7'
  force 'org.apache.commons:commons-compress:1.27.1'
  force 'org.apache.hadoop.thirdparty:hadoop-shaded-guava:1.2.0'
}
```

- `resolutionStrategy.force` 是 Gradle 的依赖解析策略，强制把所有传递依赖中指定 group:name 的版本锁定为给定版本。
- 这里把 `snappy-java` 锁定到 1.1.10.7，意味着 `iceberg-kafka-connect-runtime` 打包时，无论 Kafka Connect 客户端、Hadoop、Avro 等传递依赖声明的是哪个 `snappy-java` 版本，最终都会解析为 1.1.10.7。
- 该机制常用于修复已知 CVE：snappy-java 历史版本曾有 DoS 漏洞（如 CVE-2023-34453、CVE-2023-43642 等），强制升级到修复版本可避免运行时被攻击。
- 注意此 force 仅作用于 `iceberg-kafka-connect-runtime` 子项目，不影响 Iceberg 其他模块（如 `core`、`spark`、`flink`）的 snappy-java 版本。

## 小结

- **成效**：`iceberg-kafka-connect-runtime` 打包时使用的 `snappy-java` 从 1.1.10.6 升级到 1.1.10.7，获得上游 patch 修复（含潜在安全补丁）；其他模块不受影响。
- **影响范围**：仅 `kafka-connect/build.gradle` 1 个文件、1 行版本字符串修改，无 Java 源码或 API 变更。
- **回迁到 1.4.x 的注意事项**：
  - 这是 dependabot 例行 patch 升级，**对运行时兼容**，原则上可以回迁到 1.4.x。
  - 回迁价值有限：1.4.x 通常不单独追平 dependabot 升级；但若 1.1.10.6 存在已知 CVE 且 1.4.x 仍发布 `iceberg-kafka-connect-runtime`，可考虑回迁以修复漏洞。
  - 注意 1.4.x 的 `kafka-connect/build.gradle` 可能不存在（取决于 1.4.x 是否已包含 kafka-connect 模块）。若不存在则**无需也无法回迁**；若存在但 `resolutionStrategy.force` 列表与 main 不同（如缺少 snappy-java 行），cherry-pick 时需手工调整。
  - 由于 snappy-java 在 1.1.10.x 系列内 API 完全兼容，回迁不会引入编译或运行时问题。
  - 建议默认不回迁，除非有明确安全需求。
