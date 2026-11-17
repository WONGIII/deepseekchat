# Chat Android 客户端

原生 Jetpack Compose 客户端，界面和交互对齐网页版，默认本地优先。

## 构建

要求 JDK 17+、Android SDK 35。

```bat
gradlew assembleDebug
gradlew assembleRelease
```

Release 产物：

`app/build/outputs/apk/release/app-release.apk`

仓库不提交 APK、签名文件和 `local.properties`。

## 目录

```text
app/src/main/java/com/wongiii/chat/
  data/local/      本地会话、偏好、加密存储
  data/remote/     LLM、搜索、语音、账号与会话接口
  data/repo/       本地/云端会话仓库
  domain/chat/     SSE 流、思考/搜索/分支
  domain/files/    PDF/Office 文本提取、图片压缩
  domain/speech/   录音、TTS 流式播放
  domain/archive/  v3 ZIP 导入导出
  design/          主题、排版、图标和通用组件
  feature/         聊天页、抽屉、设置、登录、语音界面
app/src/test/       Markdown 表格和解析测试
```

## 配置

首次启动无需服务器。模型、搜索和语音地址在 App 的
“设置 → 网络设置”中独立填写；API Key 使用 Android Keystore 加密保存。

语音服务需要可访问的 8000 适配器地址。公网部署请使用 HTTPS；本地局域网可直接使用 HTTP。

## 许可证

原创代码使用仓库根目录的 MIT License；第三方依赖和上游项目说明见
`../THIRD_PARTY_NOTICES.md`，App 内也可在“设置 → 开源许可”查看摘要。
