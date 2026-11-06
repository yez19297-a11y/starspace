# 参与贡献

感谢你愿意改进星屿。提交 PR 前请先开 Issue 说明要解决的问题，避免重复劳动。

## 开发环境

- JDK 17
- Android SDK 35（Build Tools 35.0.0）
- Python 3.11+

## 提交前自检

```bash
cd server && pytest
cd android && ./gradlew assembleDebug
```

## 代码约定

- 客户端不硬编码任何 API Key、服务器地址或第三方密钥
- 新增界面请同时适配浅色与深色主题，颜色统一走 `AppTheme.colors`
- 新增后端接口请补 `server/tests/test_app.py` 中的测试
- 不要在仓库里提交 `local.properties`、`.env`、数据库文件或构建产物

## 提交信息

使用简短的祈使句，例如：

```
feat: 空间支持评论 @ 角色
fix: 修复暗色模式下输入框文字不可见
docs: 补充 Termux 部署说明
```

## 许可

提交的代码默认按 MIT 许可发布。
