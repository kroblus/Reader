# 0.3.5 发布验证记录

用户在完整实施交付后明确授权上传 GitHub。版本更新为0.3.5/19；源码分支为`codex/reader-complete-improvements`，发布标签为`v0.3.5`。只上传本次提交，不混入原有未跟踪资料。

## 首次远端门禁

运行[43440883750](https://github.com/kroblus/Reader/actions/runs/43440883750)，源码`7bf8b0f80585d67110ad567b5810cb97b2dbb46f`。单元/Lint/构建与比较器检查通过，模拟器阶段失败，发布job被阻止，未生成Release。

- 收到65/108项测试结果，其中备份UI有1项断言失败，剩余测试因模拟器进程退出未完成；不将未完成项计为通过。
- 备份失败发生在`UiDevice.pressBack()`返回值。Logcat显示两次DocumentsUI均已关闭并恢复Reader，第二次UI Automator等待辅助事件1000ms超时。这是输入辅助事件回执与真实导航结果不一致，不能据此删除真实取消与数据完整性验收。
- 测试保留原UiDevice Back操作；其辅助事件回执返回false时，必须实际观察到Reader已经接管窗口才接受。随后仍验证Activity恢复/焦点、备份页面可见、原书正文和进度不变。保持原超时和两次系统选择器取消路径，不扩大skip或删掉功能断言。
- 本地曾尝试直接同步注入Back down/up以分离回执；该改动改变按键间的等待时序，复测发现额外返回导致备份页不可见，已撤回这一尝试。失败证据保留在`20261113T143513Z-daee97ed-7bf8b0f80585-b8f63a401942`，没有将失败写成通过。
- 远端默认环境为Ubuntu26.04、Emulator37.2.13；日志持续出现ColorBuffer丢失和GL context错误，随后adb设备消失。日志尚不足以证明全部退出原因。固定CI为Ubuntu24.04、Emulator36.6.11/build15507667（与本地已验证版本一致），使用官方当前支持的`-gpu swiftshader`，替换已弃用的`swiftshader_indirect`，再重跑完整门禁。

输入配置依据：[android-emulator-runner inputs](https://github.com/ReactiveCircus/android-emulator-runner/blob/main/action.yml)；图形后端依据：[Android官方模拟器加速说明](https://developer.android.com/studio/run/emulator-acceleration)。

原运行日志、JUnit及测试Logcat保存在`build/reports/github-release-v0.3.5/failed-ci/`，运行失败记录不删除。后续只在确认Release尚不存在时，以旧tag SHA为lease更新本次未发布标签，确保新一轮质量检查、签名与发布仍针对同一提交。

修正后的API36本地备份UI复测1/1通过，0失败/跳过：`build/reports/reader-user-tests/20261113T143836Z-44515233-7bf8b0f80585-00fa3c6c160a`，构建与测试43秒。CI配置YAML检查通过，固定Linux模拟器归档HEAD请求返回200。API35仍由下一次完整远端门禁验证，不以本地结果代替。

## 发布校验

发布流程使用仓库既有签名配置，并下载已发布的0.3.4 APK，先校验GitHub记录的SHA-256，再校验两版公开签名证书一致。发布正文使用已提交的`docs/releases/v0.3.5.md`，保留UI性能15%门槛未通过的限制。

最终远端运行、tag SHA、Release URL及资产信息保存于`build/reports/github-release-v0.3.5/`的发布收据；本文中的首次失败属于保留的历史证据。

## 第二轮远端门禁与修复

运行[43444999006](https://github.com/kroblus/Reader/actions/runs/43444999006)，源码`7fb1e70ed3c2d9e0af09e5521aab1cb0cc3bf151`。模拟器未再退出，108项全部结束，105通过、3失败、0跳过；宿主阶段与发布job没有执行。

- 搜索返回原位置失败是产品缺陷：Activity重建后视口从1080×2400变为1080×2146，再随工具隐藏变为1080×2272。原实现每次将精确字符锚点取整为新页首并保存，会漂移。ReaderViewModel现在独立保留带书籍/章节身份的恢复锚点，重排、读取当前位置和串行最终保存沿用它；真正翻页或显式跳转才释放。立即重开也从保存偏移重建锚点。
- 新增实际分页与Room回归，明确证明非零原锚点落在新页内部，再验证第二次重排、最终保存、退出重开及真实翻页/显式跳转释放。原搜索精确偏移断言和10秒时限保留，并增加失败分量。
- 两个UI观察失败分别为初始Activity焦点等待与DocumentsUI包名观察；后者日志已显示DocumentsUI成功打开。窗口探针改为观察新鲜的、可见且拥有输入焦点的TYPE_APPLICATION窗口，同时仍检查Activity生命周期、hasWindowFocus及真实返回页面。原5秒时限保留，失败附焦点归属；没有将后台窗口或任意包名当作成功。
- 本地安装了与CI相同的API35 default/x86_64镜像，新增隔离AVD `ReaderReleaseApi35` / `emulator-5556`，1080×2400、420dpi、Emulator36.6.11、SwiftShader。SDK下载使用现有本机代理，未关闭证书校验，也未更改设备安全设置。
- `build/reader-v035-anchor-build.log`：72项应用单元测试0失败/错误/跳过，app lint及QA/test构建通过。
- `scripts/run-reader-user-tests.ps1 -Serial emulator-5556 -TestClass 'com.lightreader.app.BackupUiInstrumentedTest,com.lightreader.app.ReaderUiInstrumentedTest#appSettingsSwitchesLanguageImmediately,com.lightreader.app.SearchLifecycleInstrumentedTest,com.lightreader.app.ReaderLifecycleInstrumentedTest'`：**11/11通过**，0失败/跳过，3m12s。报告`build/reports/reader-user-tests/20261113T153307Z-b237b468-7fb1e70ed3c2-5bfb552d790a`。本地结果不能替代最终tag的完整CI。

第二轮原始日志和JUnit位于`failed-ci-second/`，继续保留。发布前须在修复后的同一tag SHA上重新通过完整门禁。

## 第三轮完整回归与签名报告兼容

运行[43454507594](https://github.com/kroblus/Reader/actions/runs/43454507594)，源码`451ace602dbc7e01d84df7ac132952b7e95ecc91`。单元/Lint/构建、比较器及API35完整设备回归全部通过：109项普通测试、2项新进程恢复、2项全新安装恢复，共113项，0失败/错误/跳过。正式APK构建成功，上传前的证书报告解析失败，未生成Release。

失败脚本只识别旧版`Signer #1 certificate SHA-256 digest:`；仓库已发布v0.3.4的成功日志已经使用新版`V3.0 Signer: certificate SHA-256 digest:`，旧解析返回空值而无法继续核验。增加严格解析器与8项正反用例，兼容两种格式，仍要求APK验证成功、唯一签名身份、完整SHA-256，并拒绝未知格式和冲突证书。新APK与配置证书、已发布v0.3.4的证书一致性检查均保留，并输出公开指纹及具体失败环节；不打印私钥或口令。

本轮报告与签名失败日志保留于`build/reports/github-release-v0.3.5/final-ci/`、`ci-failure-third.log`。修正仅涉及发布验证脚本、CI调用和本文；最终标签仍重新执行完整门禁。
