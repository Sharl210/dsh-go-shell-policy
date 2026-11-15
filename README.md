# DSH Go Shell 确认策略模块

独立的 LSPosed 模块 App，目标包名为 `io.github.ccch1mneyyy.dsh_app`。不替换目标 APK，不改目标 App 的 Flutter 页面、设置文件或 DSH 核心文件。

## 策略

在模块 App 内选择：

- **每次都询问**：保留 DSH Go 的原始命令确认。
- **始终允许**：自动允许 Android Shell 命令，不显示该命令的确认卡。
- **仅危险命令询问**：递归删除、分区/刷写、重启、应用数据清理、权限授予、关键路径写入等命令保留原确认；普通查询直接通过。脚本执行、动态展开和无法明确解析的间接执行也会询问。

新安装的默认值是“每次都询问”。策略保存到模块私有数据，通过只读 ContentProvider 供目标 App 查询。Provider 只接受模块自身及目标包 UID 的读取，拒绝外部写入。切换策略后下一次命令读取新值；已经显示的确认卡不被追认或自动点击。

危险判定是有明确定义的本地规则集合，不是完整 shell 解释器，也不声称识别所有脚本或工具可能造成的副作用。

## 构建

要求 JDK 17、Android SDK Platform 33、可运行的 Gradle/Linux 构建环境和网络连接。Android/Termux 宿主不是本项目指定的编译环境，应在已就绪的 Linux 工具中构建。

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。Debug 由 Android Gradle Plugin 使用本地调试密钥签名。正式发布应自行配置签名，不要把个人签名密钥提交到工程中。

工程包含 Gradle Wrapper；Xposed API 82、DexKit 2.2.0 及其依赖由 Gradle 从官方 Maven 仓库下载。Xposed API 为 `compileOnly`，不随 APK 打包。

## GitHub Actions

仓库：https://github.com/Sharl210/dsh-go-shell-policy

每次推送 `main` 或手动触发 `Build Android module` 都会在 Ubuntu runner 上安装 SDK、运行单元测试、生成签名 Debug APK并验证签名。成功后在该次运行的 Artifacts 中下载 `DSHGoShellPolicy-debug`，内含 APK、SHA-256 和签名信息。

自动生成的是调试签名；不同全新 runner 的调试证书可能不同。后续正式更新应配置固定签名密钥，不应靠卸载目标 DSH Go 来处理模块签名差异。

## 使用

1. 安装构建出的模块 APK。
2. 在 LSPosed 中启用模块，作用域仅选择 **DSH Go**；此步骤由用户操作，不修改 LSPosed 数据库。
3. 用户结束并重新打开 DSH Go 进程，使新模块加载。无需重启手机。
4. 打开模块自己的 App，选择策略。
5. 在 DSH Go 原有 Android Shell 后端已由用户连接的前提下验证命令确认。模块不会替用户启动或授权 Shizuku。

停用时在模块 App 选择“每次都询问”。完全移除 hook 时由用户在 LSPosed 禁用模块，再重新打开 DSH Go。

## 匹配设计

DexKit 每次从当前 APK 解析，不保存混淆类名：

1. 找同时使用 `confirmationDenied`、`confirmationUnavailable` 的结果判断方法。
2. 检查其自身及直接调用者，筛选五参数、`long/String/long/回调/回调`、Map 返回形状。
3. 验证 `start/poll/jobId/command` 的 Shell 作业协议锚点，必须唯一。
4. 从关联结果判断方法的实际枚举参数解析 Decision；内联情况下辅助追踪枚举调用及实际消费的 Kotlin 映射初始化器。
5. 校验七个决策常量，并绑定当前方法第五参数的真实接口和唯一二参回调 Method。
6. 仅包装本次确认回调，不全局修改确认结果判断。原来的命令长度、超时、任务取消、启用状态、通道探测及后端 UID 检查保留。

0 个候选、多个无法排除的候选、未知回调形状或枚举关联不足时，记录原因并保留原确认，不随机选方法。

当前定位按一个 base APK 及至多一层直接调用关系设计。未来拆分 APK、多跳 helper、协议变化或枚举消除可能需要适配；不能把该设计称为所有版本已经实测通过。

## 验证

`DangerCommandsTest` 使用命令字符串测试普通查询、危险命令、引用/转义、连接命令及动态脚本；不执行测试字符串中的命令。`PolicyLogicTest` 覆盖三档策略、未知模式和后端 UID 白名单。

LSPosed 日志标签：`DSHGoShellPolicy`。

- `shell_candidates=1`：解析结果唯一，不代表功能已验证。
- `installed`：hook 安装成功，不代表命令已执行。
- `callback_wrapped`：确认代理已装入本次调用。
- `decision_auto_allow`：代理在认可的后端/UID 下返回了允许决定。
- 最终是否执行以 DSH Go 返回的 `started/exitCode/errorCode` 为准。

实际设备验收至少需要：默认逐条询问；始终允许下连续两条查询不询问；危险档查询不询问、危险命令出现原确认并拒绝后不执行；切回每次询问立即恢复；取消/断开连接仍遵循原行为。危险命令验证只检查出现确认并拒绝，不实际执行破坏性操作。

## 验证状态

源码经过独立只读审查，实际编译及单元测试结果以仓库 Actions 对应提交的运行记录为准。构建成功不等于目标 App 内的 hook 效果已经验证；真实三档行为和跨版本兼容性需要单独验证。
