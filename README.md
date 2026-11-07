# 归言录原版存档提取器 0.2.1

用于提取自己已经登录的东方归言录玩家数据，再导入个人私服的存档位。APK 无联网权限，不自动上传文件，不需要输入 B站密码，也不读取登录令牌。不会修改原版游戏、原版存档或向官方服务器发请求。

## 下载

- [下载 APK](https://github.com/sanfenxiao-dev/lostword-save-extractor/releases/latest/download/lostword-save-extractor-0.2.1.apk)
- [完整工具包：APK + 自动发现模拟器的电脑脚本](https://github.com/sanfenxiao-dev/lostword-save-extractor/releases/latest/download/lostword-save-extractor-0.2.1.zip)

## 手机 / 有 su 的模拟器

1. 安装 `lostword-save-extractor-0.2.1.apk`。需要 Android 8.0 或以上、Root，以及已验证版本的 B服 6.6.0 ARM64 客户端。
2. 自己打开原版 LostWord 客户端，登录到主页并等数据同步完成；提取期间不要打关卡、领奖、抽卡或切换账号。
3. 返回「归言录存档提取」，点击「读取原版存档」，在 Root 管理器中允许授权。
4. 成功后核对玩家名、等级、角色和绘卷数量，点击「保存存档文件…」，选择本机保存位置。
5. 打开私服账号页面，登录自己的账号，选择空存档位，导入保存的 JSON 文件。已有存档位的替换仍需页面明确确认。

如果原版被系统后台清理，重新打开原版进入主页后再提取。工具只支持已核验程序版本；其他版本会停止读取，而不会猜测内存结构。没有 Root 的设备不能用这个独立 APK 直接提取别的应用数据。

## 电脑辅助：MuMu、雷电等模拟器

MuMu 的 Root 设置可能只让电脑端 ADB 获得权限，APK 内找不到 su。这时使用包内电脑辅助脚本：

1. 打开 MuMu 的 Root 权限，按模拟器要求重启；自己登录原版 B服到主页。
2. 把压缩包解压到同一个文件夹，运行 `Extract-With-PC.cmd`。
3. 脚本会安装独立提取器并调用其同一套读取代码，不启动或点击原版游戏。完成后，JSON 保存在脚本所在目录。
4. 将 JSON 上传到自己的私服存档位。

如果 ADB 不在默认位置，或连接了多个设备，用 PowerShell 指定：

```powershell
./Export-OriginalSave.ps1 -AdbPath '你的 adb.exe 完整路径' -Serial '127.0.0.1:16384'
```

脚本优先使用 ADB 已连接的设备；没有在线设备时，从 MuMu、雷电、夜神、逍遥、BlueStacks 等模拟器进程的本机监听端口尝试发现 ADB，每个连接最多等待 4 秒。不会扫描局域网或整个端口范围。不同模拟器安装方式可能仍需手动指定 ADB 路径或端口；不会自动登录或关闭游戏。ADB Root 不可用时会停止。

```powershell
# 自动寻找 ADB、设备和运行中的 LostWord
.\Export-OriginalSave.ps1
# 手动指定雷电 ADB 和小米服（无需固定模拟器端口）
.\Export-OriginalSave.ps1 -AdbPath 'E:\leidian\LDPlayer9\adb.exe' -GamePackage 'com.gg.lostword.mi'
```

多个设备在线时，使用提示里的编号传入 `-Serial`；多个游戏同时运行时，用 `-GamePackage` 选择，手机端请仅保留一个游戏运行。下载后使用完整的 0.2.1 工具包，旧 APK 仍固定 B服，不能只更新脚本。

## 渠道与版本兼容范围

自动发现包名中包含 `lostword` 的客户端（排除本提取器），包含小米服 `com.gg.lostword.mi`、B服及其他渠道。名称识别不是游戏身份或数据布局的证明：读取前必须通过核心库 SHA256 校验。

当前随附布局仅核验过 B服 6.6.0 ARM64。其他渠道只有在核心库与该布局完全一致时才进入读取；不一致时显示目标包名及核心库哈希并停止，不猜测偏移。**尚未实测小米服，也不能保证所有渠道、国际服、32 位或未来版本可导出。** 适配新核心库需要核对其字段结构后更新布局，不能删除校验强行读取。

## 数据范围

读取客户端当前内存中、已同步下来的角色、绘卷、道具、队伍、关卡等玩家字段。它不是官方提供的完整云端备份，好友历史、未同步邮件及其他只存在于服务器的数据可能不包含。

两次读取的核心数据不一致时会要求重试。正在进行的守矢秘境探索无法完整迁移，私服导入时会提示先结束原版探索再提取。

文件包含个人游戏进度，应由玩家自己保管。导出中的校验用于发现文件损坏，不代表官方认证。私服的资源和玩法实现仍影响导入后的实际显示与游玩。

## 0.1.0 历史验证

- APK 构建、v2/v3 签名检查及 MuMu 安装通过。
- Android 无界面合成内存布局测试通过：64 位整数、UTF16、列表、字典、空值、范围限制、敏感字段过滤。
- 原版未运行时，实际返回明确提示，未生成成功存档。
- 6 项新导入测试、2 项原存档转移测试、9 项账号测试通过。
- 之前已恢复的本人原版快照经新格式导入验证通过。
- MuMu 的电脑辅助方式已完成真实原版进程提取，文件校验、两次核心数据一致性、私服临时数据库导入导出往返通过。手机 su 授权和 APK 界面保存流程仍待手测。
- 已修复 Windows PowerShell 5.1 把 ADB 成功进度误判为错误的问题；回归测试保留非零退出码报错。


## 0.2.1 验证

参见 [更新记录](CHANGELOG.md)。脚本回归：`powershell -NoProfile -ExecutionPolicy Bypass -File tests/Test-Adb.ps1`。完整流程模拟测试：`powershell -NoProfile -ExecutionPolicy Bypass -File tests/Test-Flow.ps1`。APK 编译、签名及 Android 合成数据测试通过；新版各渠道和端口发现仍需对应真实环境验证。

## 从源码构建

需要 Python 3、JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。无需下载原版 APK、游戏资源或个人存档。`assets/layout.json` 只包含已核验版本的字段结构、偏移和程序校验值。

```powershell
python build.py --sdk "你的 Android SDK 目录" --jdk "你的 JDK 目录"
```

默认输出 `build/aligned-unsigned.apk`。如需签名，增加 `--keystore` 和 `--password-file` 参数，密钥和密码文件保留在仓库外。

导出格式为 `lostword-original-capture-v1`，其中 `user_json` 是 UTF-8 玩家 JSON 文本，`sha256` 是该文本字节的 SHA256。它需要支持此格式的私服导入端，不能当作原版游戏的官方导入文件。

## 实验读取不同核心库

电脑脚本提供手动开启的实验模式，仅跳过核心库哈希匹配，继续使用现有布局；仍保留读取范围、字段检查和两次快照一致性检查。默认关闭，手机 APK 界面不自动开启。

```powershell
.\Export-OriginalSave.ps1 -AdbPath 'E:\leidian\LDPlayer9\adb.exe' -GamePackage 'com.gg.lostword.mi' -AllowUnverifiedLibrary
```

实验输出标记 `source_library_verified: false` 和 `experimental_layout: true`。成功导出不证明字段布局正确，须核对玩家、角色、绘卷和道具等数据，并仅导入空存档位；失败请保留日志。不要仅凭两次一致就认定兼容。

### 不输入命令的实验方式

打开模拟器里的游戏并进入主页，双击工具包中的 `双击尝试提取.cmd`。此入口显式开启实验读取，自动保存 `extraction-log.txt`。成功后 JSON 也在同一文件夹；失败时只需发送日志文件。
