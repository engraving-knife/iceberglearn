# 提交 3821：ORC: Remove ORC tests for partition statistics (#16676)

## 提交信息

- **序号**：3821 / 4088
- **哈希**：c91025e13d6c5bdcf052957c892574929674cc79
- **短哈希**：c91025e13
- **日期**：2026-06-03 13:48:25 -0700
- **作者**：gaborkaszab <gaborkaszab@gmail.com>
- **提交说明**：ORC: Remove ORC tests for partition statistics (#16676)
- **PR/Issue**：#16676

## 总体目的

本提交删除 Iceberg ORC 模块中两个针对分区统计（partition statistics）的测试类，因为 ORC 文件格式目前并不支持分区统计的读写。这两个测试类（`TestOrcPartitionStatisticsScan` 与 `TestOrcPartitionStatsHandler`）继承自通用的测试基类，但由于 ORC 未注册为内部数据格式，几乎所有测试方法都被重写为"期望抛出 `UnsupportedOperationException: Cannot write using unregistered internal data format: ORC`"。

这种"全部重写为期望失败"的测试类没有提供任何额外的覆盖率价值——它们只是反复验证同一件事：ORC 不支持分区统计。维护这些测试类反而增加噪音，且每次基类新增测试方法时，ORC 子类都要再加一个"期望失败"的重写，造成持续维护负担。本提交将它们移除，让测试套件更聚焦于真正有意义的覆盖。

## 如何达成设计目的

直接删除两个测试文件，不保留任何替代。因为 ORC 不支持分区统计这一事实已经由其它更通用的机制（运行时抛出 `UnsupportedOperationException`）保证，无需专门的测试类反复断言。这是测试套件的"减负"清理。

## 修改详情

### `orc/src/test/java/org/apache/iceberg/orc/TestOrcPartitionStatisticsScan.java` (-73/-0 lines, deleted)

**修改目的**：移除 ORC 分区统计扫描测试类。

**工作逻辑**：
该类继承 `PartitionStatisticsScanTestBase`，重写了 `format()` 返回 `FileFormat.ORC`，并把基类的多个测试方法（`testScanPartitionStatsForCurrentSnapshot`、`testScanPartitionStatsForOlderSnapshot`、`testReadingStatsWithInvalidSchema`、`testV2toV3SchemaEvolution`、`testProjectStatFields`、`testProjectIgnoresUnknownField` 等）全部重写为期望抛出 `UnsupportedOperationException("Cannot write using unregistered internal data format: ORC")`。这些重写无实际覆盖率价值，予以删除。

### `orc/src/test/java/org/apache/iceberg/orc/TestOrcPartitionStatsHandler.java` (-73/-0 lines, deleted)

**修改目的**：移除 ORC 分区统计 handler 测试类。

**工作逻辑**：
与扫描测试类类似，该类继承 `PartitionStatsHandlerTestBase`，把基类测试方法全部重写为期望 ORC 不支持的异常，无额外覆盖价值，予以删除。

## 总结

本提交是一次测试套件的精简清理，删除了两个因 ORC 不支持分区统计而全部"期望失败"的测试类。这些测试不提供额外覆盖率，反而增加维护成本。移除后测试套件更聚焦，也减少了未来基类新增测试时需要在 ORC 子类同步重写的负担。这是测试质量治理的体现——测试应当提供价值，而非仅为存在而存在。
