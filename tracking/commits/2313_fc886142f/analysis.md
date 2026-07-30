# 提交 2313：Docs: Describe testcontainer failure workaround (#13454)

## 提交信息

- **序号**：2313 / 4088
- **哈希**：fc886142f8236346e32259ef64acd56977de8aee
- **短哈希**：fc886142f
- **日期**：2025-07-03 11:50:36 +0200
- **作者**：Claude Warren
- **提交说明**：Docs: Describe testcontainer failure workaround (#13454)
- **PR/Issue**：#13454

## 总体目的

这是一个文档改进提交，旨在帮助开发者在遇到 testcontainer 初始化失败时找到解决方案。

testcontainer 是 Iceberg 项目集成测试中使用的关键组件，它通过 Docker 容器来模拟各种外部服务（如数据库、消息队列等）。在某些环境中（特别是启用了 SELinux 的 Linux 系统），testcontainer 可能会因为 SELinux 的强制访问控制策略而无法正常初始化容器，抛出非法状态异常（illegal state exception）。

在此提交之前，README 中仅描述了 macOS 上的 Docker socket 问题及解决方案，但没有涵盖 SELinux 导致的 testcontainer 初始化失败问题。这导致在受影响环境中的开发者可能会花费大量时间排查问题。

## 如何达成设计目的

通过在 README.md 中现有的 Docker/测试设置说明之后，添加一段关于 SELinux 导致 testcontainer 失败的描述和解决方案。解决方案是暂时将 SELinux 设为 permissive 模式以允许容器运行，测试完成后再恢复为 enforcing 模式。

## 修改详情

### `README.md` (+9/-0 lines)

**修改目的**：添加 SELinux 导致 testcontainer 失败的说明和解决方案。

**工作逻辑**：在 macOS Docker socket 说明之后，新增一段文字描述 testcontainer 因 SELinux 初始化失败的问题，并给出三步操作：
1. `sudo setenforce Permissive` - 暂时关闭 SELinux 强制模式
2. `./gradlew ...` - 运行测试
3. `sudo setenforce Enforcing` - 恢复 SELinux 强制模式

## 总结

这是一个纯文档提交，为受 SELinux 策略影响的开发者提供了 testcontainer 失败的解决方案。变更简洁明了，仅添加 9 行说明文字，不影响任何代码逻辑。
