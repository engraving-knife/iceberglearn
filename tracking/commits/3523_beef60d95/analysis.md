# 提交 3523：API: Fix TableIdentifier.toLowerCase to use Locale.ROOT for namespace levels (#15956) (#15958)

## 提交信息

- **序号**：3523 / 4088
- **哈希**：beef60d95e94902eaaf2fac031194f6b2e038c6c
- **短哈希**：beef60d95
- **日期**：2026-04-13 16:25:27 +0200
- **作者**：Govindarajan
- **提交说明**：API: Fix TableIdentifier.toLowerCase to use Locale.ROOT for namespace levels (#15956) (#15958)
- **PR/Issue**：#15958（修复 #15956，回移到 1.4.x 分支）

## 总体目的

`TableIdentifier.toLowerCase()` 方法此前对 namespace levels 调用的是 `String::toLowerCase`（无参版本），而无参的 `toLowerCase()` 会使用 JVM 默认 Locale。这在土耳其语（tr）等特殊 Locale 下会产生严重问题：在土耳其语 Locale 中，大写字母 'I' 会被转换为 'ı'（带下脚点的 dotless i），而不是英文的 'i'。

这意味着名为 `INFORMATION.DB.TBL` 的表在土耳其语 Locale 的机器上执行 `toLowerCase()` 后，会得到 `ınformation.db.tbl`（首字母变成 dotless i），与其他 Locale 下的结果 `information.db.tbl` 不一致，会导致表标识符匹配失败、表找不到等诡异 bug。

实际上同方法中对 `name()` 已经使用了 `toLowerCase(Locale.ROOT)`，但 namespace levels 的处理遗漏了。本提交补上这个一致性修复。

## 如何达成设计目的

将 namespace levels 的 `String::toLowerCase` 替换为 lambda 形式 `s -> s.toLowerCase(Locale.ROOT)`，与 `name()` 的处理方式保持一致，确保跨 Locale 行为确定。同时新增一个使用 junit-pioneer `@DefaultLocale(language = "tr")` 的测试，在土耳其语 Locale 下验证 `toLowerCase()` 的行为符合预期，防止回归。

由于测试用到了 junit-pioneer，还需要在 `iceberg-api` 模块的测试依赖中新增 `libs.junit.pioneer`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/catalog/TableIdentifier.java` (+3/-1 lines)

**修改目的**：让 namespace levels 的 `toLowerCase` 使用 `Locale.ROOT`，保证跨 Locale 一致。

**工作逻辑**：
```java
String[] newLevels =
    Arrays.stream(namespace().levels())
        .map(s -> s.toLowerCase(Locale.ROOT))
        .toArray(String[]::new);
String newName = name().toLowerCase(Locale.ROOT);
```
`Locale.ROOT` 表示与 Locale 无关的「根」Locale，其大小写转换规则与英文一致，不受运行机器 Locale 影响。

### `api/src/test/java/org/apache/iceberg/catalog/TestTableIdentifier.java` (+8/-0 lines)

**修改目的**：在土耳其语 Locale 下回归测试 `toLowerCase` 的正确性。

**工作逻辑**：
```java
@Test
@DefaultLocale(language = "tr")
public void testToLowerCaseIsLocaleIndependent() {
  assertThat(TableIdentifier.of("information", "db", "tbl"))
      .isEqualTo(TableIdentifier.of("INFORMATION", "DB", "TBL").toLowerCase());
}
```
`@DefaultLocale(language = "tr")` 来自 junit-pioneer，会在该测试执行期间将默认 Locale 设为土耳其语。如果代码仍用默认 Locale，`INFORMATION` 会变成 `ınformation`（dotless i），断言会失败。修复后通过。

### `build.gradle` (+1/-0 lines)

**修改目的**：为 `iceberg-api` 模块添加 junit-pioneer 测试依赖。

**工作逻辑**：
```groovy
testImplementation libs.junit.pioneer
```
junit-pioneer 提供 `@DefaultLocale` 等 JUnit 5 扩展，用于在测试中控制 JVM 默认 Locale 等环境因素。

## 总结

本提交修复了一个隐藏的国际化 bug：`TableIdentifier.toLowerCase()` 对 namespace levels 使用默认 Locale 进行大小写转换，导致在土耳其语等 Locale 下产生不一致结果。通过统一使用 `Locale.ROOT` 并新增土耳其语 Locale 的回归测试，保证标识符大小写转换的确定性与一致性。这是回移到 1.4.x 分支的修复（PR #15958 对应 main 上的 #15956）。
