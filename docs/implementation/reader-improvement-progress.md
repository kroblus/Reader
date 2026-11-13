# Reader 完整改进实施检查点

本检查点记录上传前的完整实施阶段。用户随后授权上传GitHub，版本更新为0.3.5/19；发布工作由同一tag SHA的质量门禁与签名流程完成，见[版本说明](../releases/v0.3.5.md)。以下原始授权与未发布状态保留为历史记录。

## 现场与授权
- 开始日期：2026-11-13（Asia/Shanghai）。实际起点：`700916f41a46af9a00399749b06f0e59769dad33`，原分支 `main`。
- 工作分支：`codex/reader-complete-improvements`。
- 起始版本：0.3.4 / 18，Room schema 8（README 的 7 已过时）；最终版本号不变，Room升级到9。未发现适用 AGENTS.md。
- 初始仅有用户未跟踪内容：docs 下架构/状态/路线图/PDF/screenshots/ui-optimization/v2，output/，tmp/。保留且不纳入提交。
- 允许源码、测试、文档、工作流、本地提交；禁止 push、tag、Release、分发及正式数据清理。不读取真实 Key/签名。
- JDK 环境变量指向本机 Adoptium 21；Android SDK已配置，已在隔离API36 AVD完成本轮验证。

## 冻结的规则
- 阅读位置是 chapterId + UTF-16 code-unit 偏移；保留串行/最终保存和重开等待。
- 同书一个活动追更任务；有效 URL 决定章节身份，保留 path/query 大小写与参数。
- 完成后入库；暂停可恢复、取消停止、删除仅清理任务所有且未引用的数据。
- 快捷设置拖动预览、松手保存、关闭提交；预设不覆盖自定义快照。
- 搜索完整性显式建模；返回结果与回到查找前是两个动作，恢复原锚点按正常规则保存。
- 恢复先预览、重复默认跳过、不覆盖进度；不包含 Key/IV/Keystore/日志/搜索缓存/未完成下载。
- 界面皮肤和阅读背景作用域独立。所有设备清理仅隔离 QA 包。

## 工作包状态
| 包 | 内容 | 状态 | 证据/待办 |
|---|---|---|---|
| 01 | 阅读缓存与异步一致性 | 已验证完成 | 控制器单元、生命周期6项、新进程恢复2阶段及最终整套QA通过 |
| 02 | 网页预览与创建 | 已验证完成 | 预览JVM5项、持久化事务及最终整套QA通过 |
| 03 | 追更身份与文件提交 | 已验证完成 | 身份JVM、下载持久化8项、Worker入库及最终整套QA通过 |
| 04 | Worker 与任务状态 | 已验证完成 | Worker6项含200章25批次、通知暂停及取消删除、最终整套QA通过 |
| 05 | 抓取与 AI | 已验证完成 | 受控HTTP/robots/AI单元、HTTPS及最终整套QA通过 |
| 06 | 自定义与设置提交 | 已验证完成 | reducer/偏好兼容、草稿2项及最终整套QA通过 |
| 07 | 字体度量适配 | 已验证完成 | 12组字体尺寸/完整性及实际font2短横屏48dp点击通过 |
| 08 | 风格与交互 | 已验证完成 | 颜色合成审计、gallery、夜间搜索/大字截图目视检查及导航通过 |
| 09 | 搜索完整性与返回 | 已验证完成 | 搜索分页/UTF-16/返回原列表原锚点与Activity重建、最终整套QA通过 |
| 10 | 手动备份恢复 | 已验证完成 | 数据/取消/损坏归档、SAF UI、真实全新安装TXT/EPUB/WEB恢复通过 |
| 11 | 兼容与真实验证 | 已实现；性能验收未通过，参考环境外部阻塞 | 迁移/完整导入/113项QA/引擎比较/宏基准通过；ABBA UI的15%门槛两组失败，需受控参考环境继续归因 |
| 12 | 脚本和发布门禁 | 已验证完成（本地授权范围） | runner全量实跑、比较器16例、工作流静态验证通过；按授权未触发远端CI/签名/发布 |

## 文件所有权
- reader：feature-reader/core-reader（含 Models）、阅读 VM/Screen/Canvas/SettingsPanel/Theme、LibraryScreen、设置 Repository/VM及专用测试。
- downloads：feature-download、现有 core-data DAO/BookRepository/DownloadRepository/Database/实体、Worker/Scheduler/通知、相关测试；完成后交还 DAO/BookRepository 给主代理。
- web_validation：网页解析抓取/AI（Worker除外）、API 设置、脚本/工作流/benchmark、app Gradle 与性能测试。
- 主代理：ReaderApp/AppContainer/导航、搜索、备份新增文件、共享资源与最终验证。新增领域资源用独立 XML 防冲突。

## 问题与已排除假设
- 当前源码就是历史基线，未重置历史提交。
- “删除已完成追更会删除原书正文”属于已排除历史假设，只保留回归，不预先认定缺陷。
- 缓存正文错配触发路径已静态确认，已添加控制器确定性回归与真实 ReaderViewModel QA 用例；尚未声称真机复现。
- 已确认搜索候选与精确扫描双重100限制、robots text/plain吞错、URL path/query整串小写、进度整行回写、预览缺取消/版本门禁等问题，均已修改实现。

## 实际验证记录
- `git status --short --branch`、`git log -1`：已核对，受跟踪源码初始干净。
- 设备：emulator-5554，ShengliFlowApi36，API36 x86_64。只使用 com.lightreader.app.qa；正式包未清理/卸载。
- 首轮单元命令遇本机 Gradle 8.13 kotlin-dsl/accessors、dependencies-accessors 和 transforms 缺失 metadata.bin；停止 daemon 后将这些缓存目录改名保留并重建，恢复编译。
- `build/reader-unit-cache-repair2.log`：进入源码检查，发现迭代中的缺失import/测试helper遮蔽；已修。
- `build/reader-integrated-check1.log`：Windows classes.jar占用失败；停止daemon改用in-process编译。
- `build/reader-integrated-check2.log`：PowerShell未加引号的-P属性被拆开，命令失败；check3修正引号。
- `build/reader-integrated-check3.log`：核心单元任务已运行，app编译因Worker/DAO迭代中接口不同步失败；正在完成整合，不能视作整套通过。
- `build/reader-integrated-check4.log`：app72单元中1失败，503+Retry-After:0触发OkHttp内部隐式重试。修复为统一外层有界重试，未放宽断言/增加假响应掩盖。
- `build/reader-integrated-check5.log`：115单元全通过（core-reader18/core-formats8/feature-reader12/feature-download5/app72，0跳过）；QA/QA AndroidTest APK构建成功。之后又补本代调度对账、暂停恢复串行与搜索导航仪器测试，需追加受影响验证。
- `powershell -NoProfile -File scripts/test-performance-comparison.ps1`：主代理复跑13个正/负向用例全部通过（也修复了5.1不支持-AsHashtable的测试脚本问题）。工作流仅本地静态验证，未触发发布。
- 定向QA已开始：`scripts/run-reader-user-tests.ps1 -Serial emulator-5554 -TestClass '...SearchBackupInstrumentedTest,...ReaderSchemaEightMigrationInstrumentedTest,...ReaderLifecycleInstrumentedTest,...ReaderSettingsDraftInstrumentedTest,...ReaderFontMetricsInstrumentedTest,...SearchLifecycleInstrumentedTest,...DownloadPersistenceInstrumentedTest'`。本轮报告 `build/reports/reader-user-tests/20261113T061448Z-966e32b6-700916f41a46-e100214bc570`，输出`build/reader-targeted-qa1.log`，结果待完成。
- 基线性能准备：隔离detached worktree `D:\Desktop\Codex\Reader-baseline-700916f`，仅复制同一受控测量harness；尚未构建/测量。

## 当前与下一步
12个工作包实现已落地；最终完整QA2为109常规+4宿主阶段，共113/113通过、0失败/错误/跳过。115项单元、lint（0错误/63警告/1提示）、2项实际macro和16个比较器用例通过。最终安装启动与截图已核验，全部测试代码身份已比对。
UI性能验收仍未通过：有效ABBA原15%比较两组失败，分段诊断仍失败；无法把当前主机负载波动下的结果归因完成，也不据此免除失败。需要固定参考设备/受控主机和系统trace继续定位，尚无已证明可安全删除的多余生产操作。没有遗漏尚未实施的搜索、备份或门禁功能，但不能宣称全部验收通过。
本地交付按阅读、下载网页、搜索备份、验证门禁四组提交；跨模块接线统一以最终整合版本的验证为准，不将中间提交声称为分别通过整套测试。最终源码验证收据与本地提交身份位于本次QA报告`source-verification.json`；后续受控性能检查步骤见`design/performance-testing.md`。未push/tag/release/清理正式数据，用户初始untracked文件保留且不纳入提交。

## 2026-11-13 第二轮验证增量
- 定向 QA1：26 项中 21 通过、5 失败。实际 schema8→9 完整打开、下载持久化8项、Reader生命周期5项、字体1项通过。失败分别为 Android16 非法ZIP先抛ZipException、QA缺Compose宿主Activity、搜索导航测试。
- 定向 QA2：11 项中10通过、1失败。ZIP异常统一并保留取消传播；补QA专用Compose测试manifest；搜索结果点击前滚动到实际可见位置。备份/搜索数据7项、设置草稿2项、新分页搜索返回路径1项全部通过。
- 定向 QA3：SearchLifecycle 两项中1通过、1失败。旧用例等待自动隐藏工具栏中的搜索图标超时；reader代理正在改为先等待正文就绪再实际点击正文唤起工具，并补状态/UI树诊断，未放宽超时或删除断言。
- 备份VM增加取消中防重复点击保护，替换预览前清除已失效暂存引用，需最终回归。
- 主代理重新运行 `powershell -NoProfile -File scripts/test-performance-comparison.ps1`，16个有效/无效指标与环境对比用例全部通过。
- 历史baseline现由web_validation独占Gradle与AVD运行相同engine/UI测量harness；当前源码仍在完善对比度/大字短屏与真实新安装恢复验证，不作最终性能结论。
- downloads代理新增全新QA卸载重装备份恢复两phase验证，仅操作QA包；主代理整合完整脚本和CI。
## 2026-11-13 基线与最终验证准备
- 历史worktree相同harness性能测试2/2通过，日志与JSON复制到`build/reports/reader-performance/baseline-700916f-perf-20261113/`。UI每项10样本，模拟器p95：book_open810.54ms、cached_turn232.15ms、uncached_chapter266.87ms、layout_change165.85ms、tools_list367.23ms。含instrumentation开销，不作真机目标。
- 当前源码final-static1在配置阶段失败：macrobenchmark未设置release库fallback。已修，同时补独立self-instrumenting、targetSdk36与仅查询隔离benchmark包的manifest。final-static2仍运行，接线最后改动后须追加benchmark构建。
- 实际颜色合成审计与结果表在`readability-validation.md`；修复alpha造成的辅助文字不足4.5:1，不增加镜像测试。新增实际fontScale2短横屏QA。
- FreshInstall专用测试与Python宿主脚本已完成静态校验并整合到完整runner/CI，严格检查QA APK和instrumentation身份，真正卸载/重装QA，导出归档及证据由host暂存；尚未跑设备，不记通过。
- 默认connected套件排除两个宿主分阶段类，完整runner另跑prepare/force-stop/verify及导出/重装/恢复；CI也按阶段执行。完整runner加入Python脚本到源码hash清单。
- API高级配置草稿变化立即取消旧连接测试并清除旧成功状态，避免把已保存配置的结果显示成新草稿已验证。
## 2026-11-13 最终构建与QA4
- `build/reader-final-static3.log`：基础5模块单元115/115，0失败0跳过；app lint 0错误/63警告/1提示；QA、QA AndroidTest、benchmark app、macrobenchmark全部构建成功。static2的旧配置混淆检查失败由self-instrumenting修复，未关闭app混淆。
- benchmark APK只读核验：独立`com.lightreader.app.benchmark`非debuggable且profileable；driver `com.lightreader.app.macrobenchmark`的instrumentation target为自身；两个APK均debug同证书验证通过，未读取私钥。
- `build/reader-targeted-qa4.log`：13项定向测试运行中。旧SearchLifecycle真实状态为Reader已打开、book匹配，但loading=true/pages=0/无错误，已确认为需检查生产异步与viewport路径，不能继续视作单纯toolbar测试问题。font2短横屏测试实际暂停按钮可见高度46.476dp，小于48dp，正在修实现。
- 大字号测试新增实际控件48dp宽高验收（不是颜色镜像断言）。reader代理只修改受影响阅读实现；下载200章25批次继续，不打断。
## 2026-11-13 QA5修复验证
- QA4最终13项中11通过；6项Worker全部通过，200章25正式批次用时94.513s（含生产每章400ms限速），不是挂起；4项受控HTTPS/AI用例通过。
- 修复真实首次布局竞态：书籍/章节元数据已就绪时，0×0到有效viewport触发当前章节请求重启；屏幕挂载不重复提交持久偏好分页。
- QA5 `build/reports/reader-user-tests/20261113T064702Z-63a7efd3-700916f41a46-6062da6f2679/`：SearchLifecycle2、ReaderLifecycle5、SettingsDraft2、font2短横屏1，10/10通过、0跳过。
- 主代理已查看该次025/026真实PNG；工具为不透明夜间面、可滚动，暂停48dp可见可点，正文中文英文emoji清晰。发现底部Contents被固定76dp裁切，已改为最小76dp让文字自然扩展，随后全量截图复验。
- static4补跑受影响app单元/lint与benchmark，运行中。下一步全量QA含大语料、两阶段新进程及全新QA安装恢复，然后复用同run性能数据比较，并执行独立macro冷/热测量。

## 2026-11-13 完整QA1与QA6
- static4成功；115单元完整通过记录在static3，static4追加受影响app单元/lint/benchmark成功。
- 完整QA1：`build/reports/reader-user-tests/20261113T065411Z-25a0bb63-700916f41a46-8d13fcd762d1`，108项/107通过/1失败/0跳过，用时14m24。失败是暂存章节顺序仍预期2/3；正式契约为暂存0/1、入库分配2/3，已补完整行为断言。因常规suite失败，宿主新进程及全新安装阶段未执行。
- 完整QA1引擎4/4通过历史基线15%比较；真实UI4/5通过，layout_change p95从165.8497ms到199.8385ms（+20.49%）失败。其余p95：打开762.4189ms、缓存翻页231.7789ms、未缓存跨章283.4189ms、工具列表366.5658ms。模拟器10样本/项，不是真机目标。
- 实际同章排版复用loadChapter导致重复8192字符preview。修复为已有同书同章pages时保留旧pages/字体快照直到完整替换；初开、空页/首次有效viewport和跨章仍保留preview。新增生命周期断言只提交一次新layoutVersion并保留UTF-16锚点0。
- QA6：`build/reports/reader-user-tests/20261113T070934Z-8b6fcfec-700916f41a46-63244b97dc23`，9项/8通过/1失败/0跳过。追更修正1、完整仓库导入4、搜索2、大字横屏1通过；gallery新夜间搜索点击时正文仍loading，已改为就绪+按钮enabled后点击。
- 大语料测量已扩展到BookRepository.import完整入库（含fingerprint、文件、Room提交），scope=library_import。QA6：100MiB46402ms/峰值PSS212311KiB/410章；约10MiB3915ms/212273KiB/857章；嵌套EPUB153ms/170002KiB/1章。50ms采样峰值，单样本，正文完整性和实际入库均校验。
- FullQA1的备份UI文件选择器两次取消、重复预览与恢复通过；全新卸载安装恢复尚待宿主阶段实跑。夜间Search新增027截图待QA7目视确认。

## 2026-11-13 QA7/8与验证接线
- QA7 `20261113T072017Z-716c7a9e-700916f41a46-6df29927338c`：10项中9通过，新增同章重排、原生命周期、搜索及UI性能测量本身通过；gallery夜间搜索仍失败。
- QA8只运行gallery并增加失败状态/语义树/PNG，实际观察到Reader已就绪、loading=false、13页，搜索按钮enabled，却未导航。语义坐标证明“返回搜索结果/回到查找前”面板从y189开始覆盖搜索按钮y128..254；根因是固定72dp顶部距离漏算状态栏。已改为状态栏insets+顶栏48dp+8dp，QA9验证真实点击及两项搜索导航，未删断言/放宽超时。
- QA7原15%性能门槛仍失败：p95打开911.8896ms(+12.50%)、缓存265.8852(+14.53%)、跨章316.3300(+18.53%)、重排199.7658(+20.45%)、工具433.4084(+18.02%)。各项中位也变慢，不能在无trace时归因于噪声。
- 冻结更充分配对方案：历史/当前UI harness统一30样本、同32章语料并按10个目标章节循环；A-B-B-A四轮，预先完成构建、独占模拟器、固定空闲和记录主机负载/设备条件，全样本保留、同一15%门槛，最终完整QA也使用30样本。原10样本失败保留，不能混为同协议通过。
- 宿主helper审查后修复fresh超时只限时force-stop QA并记证据、bash安装前aapt严格核验QA/QA.test/targetQA；Python AST、bash -n通过，实际阶段仍待运行。
- CI nightly/corpus补唯一runId及checkout SHA，process阶段改用复用workflow的实际checkout SHA，移除emulator-runner逐行shell不共享的跨行status语句；YAML/契约/语法检查通过，远端未触发。

## 2026-11-13 QA9及真实宿主阶段
- QA9 `20261113T072616Z-2706f567-700916f41a46-7f8f46acba03`：gallery+搜索两项，3/3通过；已目视027夜间搜索，输入框/结果面/状态栏正确，键盘遵循系统输入法外观。
- 真实新进程恢复：`C:\Program Files\Git\bin\bash.exe scripts/run-reader-process-recovery.sh emulator-5554 20261113T073000Z-process 700916f41a46-7f8f46acba03`，prepare/force-stop/verify两阶段2/2通过（4.63s/3.6s）；证据`app/build/reports/process-recovery/20261113T073000Z-process-700916f41a46-7f8f46acba03/`，含APK身份预检。
- fresh-check1准备导出通过、QA真实卸载重装确认通过，回传SHA失败：3438字节ZIP被Windows adb标准输入在首个0x1A处截成114字节。已改Base64 ASCII传输+QA私有目录解码，仍保留回读SHA校验，未放宽断言。
- fresh-check2回传校验通过，恢复前默认字号断言失败（预期17，实际18）。只读核对历史与当前ReaderSettingsRepository均定义空DataStore默认18sp，17sp仅是Models构造默认；测试改18sp以区分归档23sp，生产实现未改。
- `build/reader-fresh-test-build.log`成功重建QA test，fresh-check3正在验证；尚未声明全新安装恢复通过。最终完整QA还会重新执行两份宿主阶段。
- fresh-check3发现真实产品崩溃：恢复指定语言时原createConfigurationContext丢失Activity上下文链，书架的rememberLauncherForActivityResult找不到owner。AppLocalization改为保留base Activity的ContextThemeWrapper并覆盖locale configuration。
- QA10备份UI通过；扩展语言用例已成功返回书架并打开真实DocumentsUI，却还期待Compose导入sheet，纠正为断言DocumentsUI与取消回书架。QA11 `20261113T073649Z-763d7af2-700916f41a46-7cfcc6793c20` 1/1通过，未改变产品导入行为。
- fresh-check4 `build/reports/backup-fresh-install/20261113T073732Z_aa688d78c3`：2/2通过/0失败/0错误，真实卸载QA/QA.test且确认不存在、同APK重装、往返SHA一致；TXT/EPUB/WEB三本正文/书签/UTF-16进度/设置恢复，三本真实阅读页打开，重复恢复0新增，AI未配置。归档SHA256 `f0790fb1bc837d51e3c343ab6996da74fb0ae92e90180ea2841be923ee6a5c86`；开始/完成源码hash一致。正式包前后均未安装/未触碰。

## 2026-11-13 最终静态与配对测量证据修复
- `build/reader-final-static5.log`：3m13s构建成功，115单元0失败/0跳过，lint0错误/63警告/1提示，最新QA/test/benchmark/macro构建全过；主代理独立读取5模块JUnit确认计数。
- 首次30样本ABBA run `build/reports/reader-performance/abba-20261113T074215Z-99ab1525` 的A1/B1仪器执行成功，但未留下完整JSON；B2也已启动，保留其结果，A2不继续。此run按证据采集失败保留，不能构造缺失样本或判性能通过。
- 真实原因：手动Orchestrator安装的测试服务UID与旧输出目录owner不符，openOutputFile抛Operation not permitted；现有UiDevice复合shell fallback未真正写出却宣称wrote。没有修改目录权限、系统安全配置或清理服务数据来绕过。
- 正在修测试证据通道：同两份harness在全部计时结束后通过带run/source/hash的Base64 status stream及分块logcat保留完整JSON，宿主校验完整样本/身份/哈希；文件fallback必须回读SHA成功才报告wrote。两版同步构建后以新的run重新执行完整ABBA，旧轮不混用/不挑样本，门槛15%保持。测试通道改动不要求重复未变化的生产JVM/lint，最终完整QA仍必须重跑。
- 新证据通道已验证：Orchestrator过滤自定义stream时，从带run/source/hash/分块编号的完整Logcat重建；原150动作全部计时结束后才输出。文件fallback实际sh执行并SHA回读，与canonical JSON完全一致。两worktree同harness/helper已同步重建成功。
- 完整有效ABBA `build/reports/reader-performance/abba-20261113T075248Z-225614a5`：四轮600动作样本全部完整；两组原15%+MinimumSamples30比较均exit1。A1/B1 p95（ms）：打开515.039→566.050(+9.90%)，缓存197.406→232.810(+17.93%)，跨章199.818→216.785(+8.49%)，重排168.515→199.635(+18.47%)，工具319.782→367.373(+14.88%)。A2/B2：打开497.853→531.110(+6.68%)，缓存233.276→216.361(-7.25%)，跨章216.297→216.856(+0.26%)，重排167.082→200.682(+20.11%)，工具333.964→400.211(+19.84%)。不隐藏失败。
- layout中位A132.815/133.696 vsB133.526/133.781，差异主要在尾部；layout各版本两轮p95稳定。其他指标重复波动与主机负载需区分：固定空闲后CPU为16.47/23.85/50.39/44.14%，可用内存5.57/2.79/0.63/9.37GiB。没有把外部波动作为删除失败依据，也不把模拟器结果宣称物理设备产品目标。
- web_validation仍独占设备跑macro冷/热10各；reader代理只读定位排版尾延迟路径/样本，不改代码/运行设备。root等待交回，最终FullQA2仍未运行，未本地提交。
- macro现已完成并交回设备：`build/reports/reader-performance/macro-20261113T075929Z-a5ba6f48`，2/2通过，冷/热各10次，TTID中位948.1458/305.0984ms、p951116.9103/390.2930ms；20条真实Perfetto trace，最新APK哈希不变、同debug公开证书、三项动画均恢复1.0。主代理读取result.json确认。这是模拟器书架Activity启动，不是正文首帧，也没有虚构真机目标。
- reader只读检查原数组：B1重排最慢集中前两次，B2集中第1/2/4次；DataStore JSON只会从第2次开始首次encode，不能解释第1次全部差异。旧Canvas标题比例与引擎不一致，新版按引擎1.45正确绘制，这可能影响初始字体缓存但尚无trace证据，不能盲改字体/取消/快照规则。
- 已授权reader只修改两份相同UI harness增加action_end/ready_observed/idle_return/frame_commit分段原始数组，原总样本/ready/30/断言不变，全部计时结束后一次输出。web_validation先核查已有原Logcat能否提供engine metrics，再待root交接执行一对A/B诊断，定位约33ms尾部发生在哪一段，不覆盖ABBA失败。生产代码当前冻结，最终FullQA2仍待诊断结论。
- 分段诊断`build/reports/reader-performance/phase-diagnostic-20261113T081008Z-e3d55fa3`已完整取得两轮300动作；各操作四段30项、非负有限且逐项和等于原总值（1e-6ms容差）校验通过，实时collector保留同步engine日志，异步文件快照标注flush_guaranteed=false。比较仍exit1（打开+44.14%、缓存+21.37%、跨章+30.28%、重排+28.06%、工具+8.82%），仅用于定位，不替代原ABBA。已恢复环境并交回root；reader/web只读分析，FullQA2正在执行。
- 最终只读诊断结论：没有已证明、能安全删除的新增多余工作。两版454条已记录前台引擎事件=152 read+152 normalize+60 preview+90 paginate；预取内部paginate没有单独指标，不能据此声称全部后台计算量相同。最慢B为0-based iteration5（第6次），action184.3426/ready38.3381/idle238.6921/frame9.4706ms，已计时前台分页18.8539ms。action包含读取偏好、save及协程调度，idle包含测试同步，不能直接分别称作磁盘或Compose CPU耗时。诊断layout中位B略快、frame中位稳定，排除了“每次固定多1–2最终提交帧”的简单解释；JSON/JIT/调度/主机压力均未被充分归因。保留原性能门槛失败，不猜测式改变正确性/字体/取消规则。进一步放行需要固定参考设备/受控主机及系统trace，不能用当前波动模拟器伪造绝对性能目标。
