# 提交 1036：Build: Add checkstyle rule to ban assert usage (#10886)

## 提交信息

- **序号**：1036 / 4088
- **哈希**：257b1d7b18f638b5925de32bcd9bbcbe5a4416c2
- **短哈希**：257b1d7b1
- **日期**：2024-08-06 18:19:12 +0200
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Build: Add checkstyle rule to ban assert usage (#10886)
- **PR/Issue**：#10886

## 总体目的

Java 的 `assert` 语句在生产环境中默认不启用（需要 `-ea` JVM 参数才会执行断言检查），因此用 `assert` 做参数校验或不变量检查是不可靠的——在未启用 `-ea` 的运行时（这是绝大多数生产部署的默认情况）下，`assert` 会被静默跳过，导致本应被拦截的非法输入或错误状态被放行，可能引发难以排查的后续问题。在测试代码中使用 `assert` 同样不理想，因为 JUnit 等测试框架不会捕获 `AssertionError`，失败信息不够友好。

Iceberg 项目此前已通过 checkstyle 规则禁止在测试中使用 `@Test(expected=...)`、`ExpectedException` 等 JUnit 机制，转而要求使用 AssertJ 的 `Assertions.assertThatThrownBy(...).isInstanceOf(...)`。本提交延续这一思路，新增一条 checkstyle 规则全面禁止 `assert` 关键字（`LITERAL_ASSERT` token）的使用，强制开发者改用 `Preconditions.checkArgument/checkState`（生产代码）或 AssertJ 断言（测试代码）等会在所有运行模式下都生效的校验手段。

这与同期的另一个提交 #10880（"Aliyun: Replace assert usage with assertThat"）配套——先清理已有 assert 用法，再加入 checkstyle 规则防止回潮。

## 如何达成设计目的

在 checkstyle 配置文件 `.baseline/checkstyle/checkstyle.xml` 中新增一个 `IllegalToken` 模块，将 `LITERAL_ASSERT` token 标记为非法。Checkstyle 的 `IllegalToken` 模块用于禁止源码中出现特定的 Java token，`LITERAL_ASSERT` 对应 `assert` 关键字。配置后，任何 Java 源文件中出现 `assert` 语句都会导致 checkstyle 检查失败，从而在 CI 阶段就阻断此类代码合入。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml`

**修改目的**：在 checkstyle 配置中新增禁止 `assert` 关键字的规则。

**工作逻辑**：在已有的 `RegexpSinglelineJava` 模块（禁止 `@Test(expected=...)`）之后，新增以下模块：

```xml
<module name="IllegalToken">
    <property name="tokens" value="LITERAL_ASSERT"/>
</module>
```

- `IllegalToken` 是 Checkstyle 内置模块，用于将指定 Java token 声明为非法。
- `tokens` 属性设为 `LITERAL_ASSERT`，表示只针对 `assert` 关键字触发违规。
- 该规则对所有应用此 checkstyle 配置的 Java 源文件生效（生产代码与测试代码均覆盖）。

## 小结

- **成效**：从静态检查层面彻底禁止 `assert` 关键字的使用，防止因 `assert` 在生产环境默认不生效而导致的校验失效问题。与 #10880 的 assert 清理配套，形成"清理 + 防回潮"的闭环。
- **影响范围**：仅 `.baseline/checkstyle/checkstyle.xml` 一个文件，新增 3 行配置。不改变任何源码逻辑，但会影响后续所有提交的 checkstyle 检查结果。
- **回迁到 1.4.x 的注意事项**：可以回迁，风险很低。该规则是纯增量配置，不依赖其他改动。但回迁前需先确认 1.4.x 分支的源码中不存在 `assert` 用法（或同步清理），否则回迁后 checkstyle 会立即失败。建议先在 1.4.x 上执行 `grep -rn "assert " --include="*.java"` 排查，必要时配合清理提交一起回迁。
