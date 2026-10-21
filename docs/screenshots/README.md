# 截图规范

本目录存放 README「界面预览」使用的图片。图片就绪后，把根目录 `README.md` 中被注释的预览段落取消注释即可。

## 文件命名

| 文件名 | 画面 | 说明 |
| --- | --- | --- |
| `01-home.png` | 首页 | 顶栏 + 分区（列表 / 网格 / 横滑至少各露出一个） |
| `02-detail.png` | 详情与播放 | 播放器 + 简介/评论切换 |
| `03-search.png` | 搜索与筛选 | 标签 / 日期 / 时长筛选中展开一个 |
| `04-download.png` | 下载管理 | 有进行中与已完成任务各一条 |
| `05-customize.png` | 界面定制 | 实时预览卡片 + 预设方案 |
| `06-settings.png` | 设置 | 分组容器与各项设置 |
| `07-dark.png` | 深色模式 | 任选一页，建议首页或详情 |
| `08-tablet.png` | 平板布局 | 双列导航或栅格，可选 |

命名前缀即 README 中的排列顺序；少一两张不影响，但不要改动已有文件的编号。

## 规格

- 分辨率：宽 1080（长边不限），统一比例，建议 1080×2400
- 格式：PNG；单张控制在 500 KB 以内
- 内容：同一设备、同一语言（简体中文）、同一字号档位拍摄，避免横向对比时参差

## 拍摄

```bash
# 截图（设备已连接且授权）
adb exec-out screencap -p > 01-home.png

# 压缩到宽 1080（若原始分辨率更高）
ffmpeg -i raw.png -vf scale=1080:-1 -compression_level 100 01-home.png
```

拍摄前请检查：

- 状态栏的电量、时间、通知图标不含个人信息
- 账号相关页面不要露出昵称、邮箱、订阅列表
- 「官网网址」等站点配置项如不希望公开，用马赛克或纯色块遮挡
- 若画面含不宜公开的封面内容，请做模糊处理后再放入本目录

## 在 README 中的用法

`README.md` 的「界面预览」段落已按下列结构写好并被注释：

```html
<p align="center">
  <img src="docs/screenshots/01-home.png" width="24%" alt="首页">
  <img src="docs/screenshots/02-detail.png" width="24%" alt="详情与播放">
  <img src="docs/screenshots/03-search.png" width="24%" alt="搜索与筛选">
  <img src="docs/screenshots/04-download.png" width="24%" alt="下载管理">
</p>
```

四张一行、`width="24%"` 在 GitHub 的窄栏与宽栏下都不会溢出；两行之间留空行即可。
