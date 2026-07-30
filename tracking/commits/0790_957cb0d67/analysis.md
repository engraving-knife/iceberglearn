# 提交 0790：Build: Bump org.springframework:spring-web from 5.3.35 to 5.3.36 (#10382)

## 提交信息

- **序号**：0790 / 4088
- **哈希**：957cb0d67f077d18ebf9f0b1a6abea7f099ac899
- **短哈希**：957cb0d67
- **日期**：2024-05-27 12:31:27 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.springframework:spring-web from 5.3.35 to 5.3.36 (#10382)
- **PR/Issue**：#10382
- **提交正文摘要**：Dependabot 自动生成，`dependency-type: direct:production`，`update-type: version-update:semver-patch`，链接了 spring-projects/spring-framework 的 release notes 与 `v5.3.35...v5.3.36` 的 compare 页面。

## 总体目的

由 Dependabot 自动发起的依赖版本升级，将 Spring Framework 的 `spring-web` 模块从 `5.3.35` 升至 `5.3.36`。`spring-web` 提供 Web 层基础（如 `RestTemplate`、HTTP 客户端抽象、编解码基础设施等），Iceberg 在部分与 Spring/Spring Boot 集成的模块（如基于 Spring Boot 的运行时或 REST catalog 相关组件）中作为生产依赖引入。本次为同一 `5.3.x` 修订线内的补丁级递增（patch +1），主要获取缺陷修复，尤其可能包含安全相关补丁（Spring 5.3.x 维护线通常会持续合入 CVE 修复）。

Spring Framework 5.3.x 是当前仍在维护的 JDK 8 兼容线（5.3.x 系列支持 Java 8+），与 Iceberg 1.4.x 的 JDK 基线契合。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml`，将 `[versions]` 节中的 `spring-web` 版本字面量从 `"5.3.35"` 改为 `"5.3.36"`。下游模块通过 `${libs.versions.spring-web}` 引用，自动获取新版本，无需逐模块修改。注意相邻的 `spring-boot = "2.7.18"` 不在本次升级范围内（Spring Boot 2.7.18 对应 Spring Framework 5.3.x，二者兼容性矩阵保持稳定）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `spring-web` 版本号从 `5.3.35` 升级到 `5.3.36`。

**工作逻辑**：在 `[versions]` 节中（位于 `spark-hive35` 之后，`spring-boot` 之后，`sqlite-jdbc` 之前），原行
```toml
spring-web = "5.3.35"
```
修改为
```toml
spring-web = "5.3.36"
```
该 key 在版本目录中被各处 `${libs.versions.spring-web}` 引用，升级后所有引用处自动指向新版本。

统计：1 file changed, 1 insertion(+), 1 deletion(-)。

## 小结

- **成效**：将 `spring-web` 升级至 5.3.36，获取补丁级修复（Spring 5.3.x 维护线通常包含缺陷与潜在安全修复），保持 Spring 栈依赖最新。Dependabot 标注为生产直接依赖、补丁级升级，风险可控。
- **影响范围**：仅依赖版本配置改动，无源码、API 改动。影响所有引用 `spring-web` 的模块的构建产物依赖版本。Spring 5.3.x 在补丁版本内保持二进制兼容，运行时行为预期无破坏性变化。
- **回迁注意事项**：回迁到 1.4.x 分支无障碍，仅需修改同一行 `spring-web` 版本号。Spring Framework 5.3.x 支持 Java 8+，与 1.4.x 的 JDK 基线无冲突。需确认 1.4.x 分支上 `spring-boot` 版本是否仍为 `2.7.18`（Spring Boot 2.7.x 对应 Spring Framework 5.3.x，二者兼容性矩阵稳定，5.3.36 与 Spring Boot 2.7.18 配对无问题）。建议回迁后执行一次构建确认 Spring 相关模块依赖解析正常。
