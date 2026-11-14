# 打开应用设置时输入法闪现

日期：2026-11-14，发布版本 0.3.6 / versionCode 20。此版本一并包含此前已在手机体验的字体、留白、排版设置和过渡改进。

## 问题与修复

书架的“更多 → 应用设置”切换过程中，输入法会短暂出现后收回。设置页最终没有键盘，因此只截图最终状态或只检查导航完成后的状态无法发现问题。

在 MEIZU 20 Pro / Android 16、系统动画倍率 1.0 上，旧 QA APK 的逐帧回归捕获到 IME 可见且高度为 1042px 的单帧，发生在菜单窗口关闭、焦点交回应用之后。未点击过搜索框的场景同样复现。仅清理搜索焦点、隐藏键盘和限制退场搜索框焦点，不能消除这个窗口切换瞬态。

正式修复限定在没有输入控件的“更多”菜单：通过 `PopupProperties.flags` 保留 `FLAG_WATCH_OUTSIDE_TOUCH` 并添加 `FLAG_ALT_FOCUSABLE_IM`，使它仍可接收键盘焦点、返回键和外部点击关闭，但不作为输入法目标窗口。依据 Android 的 [FLAG_ALT_FOCUSABLE_IM 定义](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_ALT_FOCUSABLE_IM) 和 [PopupProperties 配置接口](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/PopupProperties)。

同时，打开菜单前结束旧搜索输入会话，切换页面时清理旧焦点；菜单展开或书架退场期间，搜索框不接受新焦点。没有使用延时隐藏、关闭过渡动画、禁用菜单焦点，或修改整个 Activity 的软键盘模式。用户返回书架主动点击搜索时仍能调出键盘并输入。

## 回归方法与结果

新增 `AppSettingsImeInstrumentedTest`，使用真实 `ActivityScenarioRule`、UiAutomator 点击和 Choreographer 时钟。逐帧记录 IME 可见性、高度、路由、窗口焦点及输入连接状态；从打开菜单前开始，到设置页连续稳定 800ms 为止。没有替换生产 Insets listener、使用人工 Compose 时钟、延迟忽略首帧或放宽断言。

| 场景 | 操作 | 修复版真机结果 |
| --- | --- | --- |
| 未使用搜索 | 菜单进入设置并返回，3 次 | 通过，870 个过渡采样帧中 0 个异常 IME 帧 |
| 搜索后按 Back 收起键盘 | 仍允许保留旧焦点，再进入设置并返回，3 次 | 通过，875 个过渡采样帧中 0 个异常 IME 帧 |
| 键盘显示时打开菜单 | 允许初始收起，但禁止隐藏后再次出现 | 通过，312 个过渡采样帧中 0 个重新出现的 IME 帧 |

三项末尾均重新点击搜索、实际显示键盘并注入 Q/A 键，验证能够输入。为覆盖模拟器硬件键盘，测试临时开启 `show_ime_with_hard_keyboard`，在 finally 中恢复原值并读回确认。

旧 APK 对照：`build/ime-regression/ime-baseline-45507801-20261113T191645Z`，3 项中 2 项失败；未使用搜索场景捕获 2 次单帧闪现，Back 收起场景捕获 1 次，证明此回归能够检出原问题。

最终修复版真机：`build/ime-regression/ime-popup-ime-phone-20261113T192702Z`，3/3 通过，全部观察共 3766 帧，其中 2057 帧属于进入设置的观察窗口。原始 JSON 在各报告的 `evidence/interaction/`，失败候选报告亦保留在本地。

本地 115 项 JVM 测试通过，QA 应用与测试 APK 构建通过。最终 Lint 为 0 errors、63 warnings、1 hint；日志 `build/ime-regression/final-lint.log`。

- 最终真机正常动画下两项阅读过渡回归通过：`build/ime-regression/ime-final-phone-motion-20261113T192820Z`。
- 最终 API 36 模拟器正常动画下三项 IME 回归和两项阅读过渡回归全部通过：`build/ime-regression/ime-final-api36-20261113T192820Z`。
- 在手机上实际验证菜单可由返回键和外部点击关闭，随后能正常进入应用设置；`build/ime-regression/menu-smoke.json`，最终截图 `ime-final-settings.png`。
- 手机系统动画倍速恢复为 1.0，临时硬件键盘 IME 配置按测试前值恢复。

## 交付核对

首轮发布检查 `43493157257` 的 JVM、Lint 和构建通过，设备回归为 118/120。两项失败均有对应测试修正：搜索返回原位置的样本首章原为 1795 字符，新默认排版不足三页，现为该用例提供更长首章，保留 10 秒超时与第三页起点；SAF 导入已完成，但等待焦点时没有继续驱动 Compose 测试时钟，现同步界面以释放已结束的忙碌弹窗，保留 Activity 和真实输入窗口的严格焦点检查。

上述修正与 IME 回归在 Android 15 / API 35 模拟器定向 8/8 通过（`build/ime-regression/ci-repair-api35.log`）；按用户要求在 MEIZU 20 Pro 上追加 IME、搜索、系统导入、阅读过渡和内置字体验证，13/13 通过（`build/ime-regression/ime-ci-repair-phone-20261114T051906Z`）。随后追加的书架尺寸与全应用风格调整另见 `app-visual-consistency.md`。

输入法专项修复阶段的 QA APK SHA-256：`d02742129c95f8af223a7d6306b1a410f573fb8a5b24ea4b1772ba5fece6359c`。后续外观统一版本的验证另见 `app-visual-consistency.md`。正式发布仍由仓库同 SHA 质量检查及既有签名工作流生成，正式 APK 与 QA APK 的包名和签名不同。

手机清理范围限定为独立 QA 包、QA 测试包、已核对的 Reader 测试证据目录、历史 Reader UI 截图/XML 和已核对散列的旧测试安装包。删除前把文件拉取至本地 `build/ime-regression/phone-test-archive` 并逐一比对 SHA-256；不清理正式应用数据或个人备份目录。

已核对并清理 232 个文件，共 98,612,852 字节，以及 `com.lightreader.app.qa` 和 `com.lightreader.app.qa.test`。清理后手机仅保留正式 `com.lightreader.app`；文件清单和结果分别存于上述归档目录的 `manifest.json`、`cleanup-result.json`。

用户要求追加真机和外观验证后，再次归档并清理 104 个文件、38,413,002 字节，卸载本轮 QA 应用及测试包；本轮清单位于 `build/ime-regression/phone-final-archive`。归档以短文件名映射原始路径，绕开 Windows 长路径限制，每个文件仍逐一核对 SHA-256。两轮合计 336 个文件、137,025,854 字节，正式书库保留。
