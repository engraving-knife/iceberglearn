# 提交 2136：Core: Use baseTableLocation() instead of hardcoded table location in CatalogTests

## 提交信息

- **序号**：2136 / 4088
- **哈希**：cbbc8728e2bb5ee1f32d587dd99bb44384f8b799
- **短哈希**：cbbc8728e
- **日期**：2025-05-15 22:47:49 -0700
- **作者**：Talat UYARER
- **提交说明**：Core: Use baseTableLocation() instead of hardcoded table location in CatalogTests (#13071)
- **PR/Issue**：#13071

## 总体目的

这个提交将 CatalogTests 中硬编码的表位置字符串替换为使用 baseTableLocation() 方法返回的动态值。CatalogTests 是一个抽象测试基类，被多个 Catalog 实现的测试类继承。原来测试中使用了硬编码的 "file:/tmp/ns/table" 作为表位置，但不同的 Catalog 实现可能有不同的基础表位置（如基于临时目录的路径），硬编码的值会导致某些 Catalog 实现的测试失败。使用 baseTableLocation() 方法可以确保测试中使用的位置与实际 Catalog 实现的基础位置一致，提高测试的通用性和可移植性。

## 如何达成设计目的

1. 将创建表时 `.withLocation("file:/tmp/ns/table")` 替换为 `.withLocation(baseTableLocation(TABLE))`。
2. 将断言中 `.isEqualTo("file:/tmp/ns/table")` 替换为 `.isEqualTo(baseTableLocation(TABLE))`。
3. 利用已有的 baseTableLocation() 方法，该方法为各 Catalog 测试子类提供了正确的表位置基路径。

## 修改详情

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (修改, +2/-2 lines)

**修改目的**：消除硬编码的表位置字符串，改用动态方法获取。

**工作逻辑**：在测试方法的两个位置进行了替换。第一处是创建表事务时指定表位置，从硬编码的 `"file:/tmp/ns/table"` 改为 `baseTableLocation(TABLE)`。第二处是验证表位置是否匹配请求位置的断言，同样从硬编码值改为 `baseTableLocation(TABLE)`。这样测试会使用各 Catalog 实现实际的基础表位置，而非固定的 file 路径。

## 总结

这是一个小型的测试改进提交，通过消除硬编码的表位置字符串，使用 baseTableLocation() 方法替代，提高了 CatalogTests 抽象测试类的通用性，确保不同 Catalog 实现的测试都能正确运行。
