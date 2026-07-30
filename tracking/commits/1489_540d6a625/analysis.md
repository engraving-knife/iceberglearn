# 提交 1489：GCS: Suppress JavaUtilDate in OAuth2RefreshCredentialsHandler (#11773)

## 提交信息

- **序号**：1489 / 4088
- **哈希**：540d6a6251e31b232fe6ed2413680621454d107a
- **短哈希**：540d6a625
- **日期**：2024-12-13（Fri Dec 13 17:05:11 2024 +0900）
- **作者**：Yuya Ebihara <ebyhry@gmail.com>
- **提交说明**：GCS: Suppress JavaUtilDate in OAuth2RefreshCredentialsHandler (#11773)
- **PR/Issue**：#11773

## 总体目的

Iceberg 项目在构建中启用了 Error Prone 静态分析，其中包含 `JavaUtilDate` 检查规则——该规则会标记所有使用 `java.util.Date` 的位置，因为 `java.util.Date` 是设计上存在缺陷的遗留 API（可变、时区处理易错、大多方法已废弃），现代代码应优先使用 `java.time` 包下的 `Instant` / `LocalDateTime` 等。

但 Iceberg GCP 模块的 `OAuth2RefreshCredentialsHandler.refreshAccessToken()` 方法在结尾构造返回值时必须创建 `com.google.auth.oauth2.AccessToken`，而 GCP 官方 API `AccessToken(String token, Date expirationTime)` 的第二个参数类型正是 `java.util.Date`——这是 GCP SDK 强制要求的，Iceberg 无法改用 `java.time` 类型。

此前同模块的 `GCPProperties` 构造函数已用 `@SuppressWarnings("JavaUtilDate") // GCP API uses java.util.Date` 标注抑制该警告，但 `OAuth2RefreshCredentialsHandler.refreshAccessToken` 漏标，导致构建时持续产生 `JavaUtilDate` 警告，污染构建日志、干扰真正的告警排查。

本提交为 `refreshAccessToken` 方法补上同样的 `@SuppressWarnings("JavaUtilDate")` 注解与说明注释，消除该警告。

## 如何达成设计目的

在 `OAuth2RefreshCredentialsHandler.refreshAccessToken()` 方法上添加注解：

```java
@SuppressWarnings("JavaUtilDate") // GCP API uses java.util.Date
@Override
public AccessToken refreshAccessToken() {
  ...
  return new AccessToken(token, new Date(Long.parseLong(expiresAt)));
}
```

注解放在 `@Override` 之前（这是 Java 注解目标为方法时的常见写法）。注释 `// GCP API uses java.util.Date` 与 `GCPProperties` 中已有的写法完全一致，说明抑制原因是 GCP SDK API 强制要求 `java.util.Date` 类型，非 Iceberg 主动选择。

## 修改详情

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/OAuth2RefreshCredentialsHandler.java`

**修改目的**：抑制 Error Prone 的 `JavaUtilDate` 警告。

**工作逻辑**：仅在 `refreshAccessToken` 方法声明前新增一行注解 + 注释：

```java
@SuppressWarnings("JavaUtilDate") // GCP API uses java.util.Date
@Override
public AccessToken refreshAccessToken() {
```

该方法体内结尾处 `return new AccessToken(token, new Date(Long.parseLong(expiresAt)));` 是触发 `JavaUtilDate` 警告的位置——`new Date(...)` 创建了一个 `java.util.Date` 实例，作为 `AccessToken` 构造函数的过期时间参数。GCP 的 `com.google.auth.oauth2.AccessToken` API 不接受 `java.time.Instant` 等替代类型，故只能用 `java.util.Date` 并抑制警告。

> 注：该文件已 `import java.util.Date;`，本提交不改变任何 import、不改变方法体逻辑、不改变行为，仅新增一行注解。

## 小结

- **成效**：`OAuth2RefreshCredentialsHandler.refreshAccessToken` 不再触发 `JavaUtilDate` 警告，构建日志更干净，与同模块 `GCPProperties` 的处理方式保持一致。
- **影响范围**：1 个文件、1 行新增，零行为变更、零逻辑变更。纯构建卫生（build hygiene）改进。
- **回迁到 1.4.x 的注意事项**：
  - 这是纯注解补充，**可回迁可不回迁**：
    - 若 1.4.x 构建也启用了 Error Prone 且 `JavaUtilDate` 规则开启，回迁能消除警告，**建议回迁**以保持构建干净。
    - 若 1.4.x 构建未启用该规则或未将 `JavaUtilDate` 视为错误，回迁与否不影响构建结果。
  - cherry-pick 风险为零：仅新增一行注解，不与任何已有代码冲突。
  - 前提是 1.4.x 的 `OAuth2RefreshCredentialsHandler` 类已存在（该类是 GCS OAuth2 凭据刷新功能的一部分，若 1.4.x 已有此功能则可回迁；若 1.4.x 尚无此类，本提交无意义）。
  - 若 1.4.x 的 `refreshAccessToken` 方法签名/位置与 main 不同，需手工定位注解插入位置。
