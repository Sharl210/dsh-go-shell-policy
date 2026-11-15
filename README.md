# DSH Go Shell 始终允许模块

独立的 LSPosed 模块 App，目标包名为 `io.github.ccch1mneyyy.dsh_app`。不替换目标 APK，不改目标 App 的 Flutter 页面、设置文件或 DSH 核心文件。

## 行为

模块没有设置入口，也没有跨进程配置读取。Hook 成功定位当前 DSH Go Shell 执行入口后，固定返回 `ALLOW_ONCE`，因此每条满足原始后端/UID 校验的 Android Shell 命令都会直接放行，不再显示确认卡。

模块不启动 Shizuku，不改变 DSH Go 的连接、超时、取消、启用状态或后端 UID 校验。DexKit 无法唯一定位时保留 DSH Go 原始确认路径。

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
4. 在 DSH Go 原有 Android Shell 后端已由用户连接的前提下验证命令确认。模块不会替用户启动或授权 Shizuku。

停用时由用户在 LSPosed 禁用模块，再重新打开 DSH Go。

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

`PolicyLogicTest` 覆盖 ADB/Shizuku 后端 UID 白名单。固定放行路径的设备行为仍需在 LSPosed 作用域已由用户配置的 DSH Go 进程中实测。

LSPosed 日志标签：`DSHGoShellPolicy`。

- `shell_candidates=1`：解析结果唯一，不代表功能已验证。
- `installed`：hook 安装成功，不代表命令已执行。
- `callback_wrapped`：确认代理已装入本次调用。
- `decision_auto_allow`：代理在认可的后端/UID 下返回了允许决定。
- 最终是否执行以 DSH Go 返回的 `started/exitCode/errorCode` 为准。

实际设备验收至少需要：普通 Android Shell 查询不再出现确认卡；命令仍受 DSH Go 原有连接、启用状态、后端 UID、超时和取消逻辑约束；取消/断开连接仍遵循原行为。

如果 LSPosed 日志出现 `couldn't find libdexkit.so`，新版本会先尝试类加载器路径，失败后从模块 APK 当前 ABI 的 `lib/<abi>/libdexkit.so` 解压到 DSH Go 的 `code_cache/dshgo-shellpol/`，再用绝对路径加载；不会修改 DSH Go APK 或系统库。日志中的 `native_loaded=absolute` 表示这条路径生效。
