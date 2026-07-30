# 提交 0573：在 TestRESTCatalog 中补回缺失的 @Test 注解

## 提交信息

- **序号**：0573 / 4088
- **哈希**：b261edd0c6d820fa0bc9e215aad84bf90bd273cf
- **短哈希**：b261edd0c
- **日期**：2024-03-09 02:29:35 +0100
- **作者**：Alexandre Dutra <adutra@users.noreply.github.com>
- **提交说明**：Core: Add missing `@Test` annotation in TestRESTCatalog (#9899)
- **PR/Issue**：#9899

## 总体目的

修复 `TestRESTCatalog` 中一个名为 `testCatalogCredentialNoOauth2ServerUri` 的测试方法因缺失 `@Test` 注解而无法被 JUnit 实际执行的问题。这是一个纯粹的测试质量修复：测试代码已经存在且逻辑完备，但因为注解遗漏，它从来没被真正跑过——属于典型的「假绿」测试，给开发者带来虚假的安全感。

背景动机：
- 该测试方法验证的场景比较关键：当 Catalog 配置了 `credential`（client credentials）但没有配置 OAuth2 server URI 时，REST Catalog 的鉴权与请求头处理是否正确。这是一个容易出 bug 的边界场景。
- 由于方法以 `test` 开头且看起来像一个常规 JUnit 用例，开发者很容易误以为 CI 已经覆盖它。但实际上缺少 `@Test` 注解，JUnit 4 / 5 都不会把它作为测试方法运行，CI 即便它内部逻辑失败也不会报警。
- 这类问题在代码审查时容易遗漏，往往要等到有人手动审视测试类时才被发现，本提交正是这种审视后的修复结果。

## 如何达成设计目的

提交只做一件事：在 `testCatalogCredentialNoOauth2ServerUri` 方法签名上方加一行 `@Test` 注解。补上注解后，该方法会被 JUnit 视为测试用例并执行，验证其中三段 `Mockito.verify(adapter).execute(...)` 断言：

1. 不带 token / credential 调用 `POST v1/oauth/tokens` 进行 catalog token 交换；
2. 不带 token / credential 调用 `GET v1/config` 获取配置；
3. 使用 catalog token 调用 `GET v1/namespaces/ns/tables/table`。

修复思路非常直接——既然测试已经写好但没被识别为测试，那就补回注解让其生效。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：补回 `@Test` 注解，让测试方法真正进入 JUnit 测试执行流程。

**工作逻辑**：
- 在 `testCatalogCredentialNoOauth2ServerUri` 方法之上加一行 `@Test`（`org.junit.Test`，从该类既有上下文看是 JUnit 4 风格）。
- 该方法本身的逻辑不变：构造一个带 `credential=catalog:secret` 配置的 `RESTCatalog`，调用 `catalog.tableExists(TableIdentifier.of("ns", "table"))`，并用 Mockito 校验底层的 `RESTCatalogAdapter` 收到了正确的请求序列与请求头：
  - OAuth token 交换（`POST v1/oauth/tokens`）使用空请求头 `emptyHeaders`；
  - config 拉取（`GET v1/config`）使用空请求头 `emptyHeaders`；
  - 表存在性检查（`GET v1/namespaces/ns/tables/table`）使用包含 `Authorization: Bearer client-credentials-token:sub=catalog` 的 `catalogHeaders`。
- 加上注解后，这套断言才会真正运行，从而覆盖「无 OAuth2 server URI 时如何用 catalog credential 换 token 并复用到后续表操作」这一鉴权链路。

## 小结

- **成效**：让一个本应覆盖关键鉴权边界场景的测试方法真正生效，把隐藏的「假绿」测试变成实跑用例，补回了 REST Catalog 鉴权链路的测试覆盖盲区。
- **影响范围**：仅限测试代码，对运行时行为无影响。CI 流水线在合并后会多跑一个测试方法，可能暴露此前未发现的潜在 bug（但既然 PR 已经合入，说明该测试在新代码上是绿过的）。
- **回迁到 1.4.x 注意事项**：
  1. 改动是单行注解追加，无 API / 行为变更，回迁零风险。
  2. 回迁前需确认 1.4.x 分支上 `TestRESTCatalog` 与 `testCatalogCredentialNoOauth2ServerUri` 方法已存在（即该测试方法已通过其它提交进入该分支），否则注解无对象可加。
  3. 回迁后建议跑一遍该测试方法，确认 1.4.x 上同样能通过；若不通过，说明 1.4.x 上的鉴权链路实现与 main 分支已有偏差，需要排查 REST Catalog 在「无 OAuth2 server URI」场景下是否真的符合预期行为。
  4. 这类「补 @Test」型修复建议在所有追踪分支上做一次系统性扫描，用类似 `grep -rn "public void test" --include="*Test.java" | grep -v "@Test"` 的方式找出其它潜在的「假绿」测试，避免同类遗漏。
