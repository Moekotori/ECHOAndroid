# 模块化语言系统

ECHO 使用 Android 原生资源回退，不维护一份全局文案 Map，也不让 feature 读取 app 的 R。

## 职责

| 模块 | 职责 |
| --- | --- |
| `core:model` | `EchoLanguage`、`EchoAppLanguage.supported`：语言 ID、BCP 47 tag、原生名称与兼容 ID 归一化 |
| `core:i18n` | Android 语言选择、系统语言读取、旧版本 Context 包装、Android 13 平台语言迁移 |
| `core:data` | 设置持久化与启动快照；Android 13+ 读取平台选择作为实际语言 |
| 各 feature / `core:design` | 自己的 `res/values*/strings*.xml`，自己消费自己的 R |
| `app` | 启动与语言切换接线、系统 `localeConfig` 声明 |

语言注册表是设置页选项的数据来源。`system` 是选择模式，不是一份翻译。旧 ID `zh / en / ja / system` 继续有效；平台 tag 如 `zh-CN`、`en-US` 会归一化到已注册 ID。不支持的设置 ID 回到跟随系统。

## 扩展一种语言

1. 在 `EchoAppLanguage.supported` 添加 `EchoLanguage("fr", "fr", "Français")`。无需修改设置页的 when 或选项列表。
2. 执行 `./gradlew generateEchoLocales --no-configuration-cache`，同步生成入库的 `app/src/main/res/xml/locales_config.xml`。
3. 在需要翻译的模块添加 `src/main/res/values-fr/strings.xml` 或 `strings_feature.xml`，沿用英文默认资源的 key。普通语言用 `values-fr`，地区用 `values-pt-rBR`，脚本等完整 BCP 47 形式用 `values-b+zh+Hant`。
4. 执行 `./gradlew checkLocalization checkModules --no-configuration-cache`，查看覆盖率及格式参数错误；构建一次 APK 后切换语言检查主要页面。

新语言不需要新 Gradle module。模块划分按功能职责，语言版本由资源 qualifier 划分。允许逐模块补翻译，缺失的条目由 Android 回退到默认英文 `values/`；不要把空字符串当作“尚未翻译”，空值不会触发回退。注册语言前应决定是否接受部分页面英文。

`strings_feature.xml` 中已经迁出的 key 是稳定标识，后面的短后缀只用于避免碰撞；修改文案时保留 key，不要重新生成或按文案改名。已有中英日译文均保留。

## 编写文案

Compose 直接使用本模块的资源：

```kotlin
Text(stringResource(R.string.library_empty_title))
Text(stringResource(R.string.library_loading_count, count))
```

格式化用带序号的占位符，翻译可重排参数，但不能改变参数类型：

```xml
<string name="library_loading_count">Loading %1$d tracks</string>
```

既有 Kotlin 插值迁移时用 `%1$s` 保留其原始字符串语义；新数量文案优先用 Android `<plurals>` 与 `pluralStringResource`，不要自行按 `count == 1` 推断所有语言的复数规则。复数资源必须有 `other`。同样支持原生 string-array、RTL qualifier 和格式化资源，不引入另一个资源渲染器。

涉及 Context 的非 UI 代码应从所属模块资源读取 `context.getString(...)`，或由调用方注入已解析的文字/资源访问接口；不要长期缓存 Activity Context。Android 12 及以下的 Application/Service Context 需要先用 `wrapEchoAppLocale(selectedLanguage)` 创建语言 Context，不能假定它和 Activity 的资源配置一致。业务模型优先携带状态/错误码，由 UI 翻译，避免把语言固定在长生命周期状态里。

## 回退与切换

- Android 13+：`LocaleManager.applicationLocales` 是实际选择；启动只做一次旧偏好迁移，之后不会拿过期 DataStore 值覆盖用户在系统设置中的选择。
- Android 8–12：沿用已保存偏好与 Activity Context 包装，切换后由 app 重建 Activity。跟随系统从系统 Resources 读取，不从已被应用修改的 `Locale.getDefault()` 反推系统语言。
- UI 使用 `stringResource`，随 Context 配置变化刷新。语言资源由 Android 负责匹配与默认回退。
- 兼容期保留 `echoString` 和非 UI 的 `echoText`。前者已没有业务 UI 调用，新增调用会被检查拒绝；后者仍是中英日旧接口，后台异常/扫描状态等文字尚未全部迁移，不代表这些旧调用已支持新增语言。新代码不再使用三语参数接口。

## 检查与边界

`checkLocalization` 无需 Python 或设备，会检查注册表与系统声明一致性、模块内 string key 重复、译文是否有本模块默认值、string 格式参数，以及是否重新引入 UI 内嵌三语调用；输出各已有资源目录的 string 覆盖率。缺译允许回退，不强制伪造翻译。复数/数组由 Android 资源编译校验，当前覆盖率只统计 string。

CI 在原模块检查步骤一起运行该任务，不增加仪器测试。语言切换布局、专业译文质量、RTL 和 Android 12 以下设备仍需按发布范围验收。

平台依据：[Android 应用语言设置](https://developer.android.com/guide/topics/resources/app-languages)。
