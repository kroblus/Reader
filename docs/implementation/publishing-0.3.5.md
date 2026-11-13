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
