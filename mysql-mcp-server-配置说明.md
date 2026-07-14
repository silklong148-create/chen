# MySQL MCP Server 配置说明

## 已完成的配置

Codex 已注册名为 `mysql` 的本地 MCP 服务。它使用 STDIO 通信方式启动，命令为：

```text
C:\Users\g'g'g\AppData\Local\Microsoft\WinGet\Packages\astral-sh.uv_Microsoft.Winget.Source_8wekyb3d8bbwe\uvx.exe --from mysql-mcp-server mysql_mcp_server
```

配置文件地址：

```text
C:\Users\g'g'g\.codex\config.toml
```

MCP 服务配置段：

```toml
[mcp_servers.mysql]
command = '''C:\Users\g'g'g\AppData\Local\Microsoft\WinGet\Packages\astral-sh.uv_Microsoft.Winget.Source_8wekyb3d8bbwe\uvx.exe'''
args = ["--from", "mysql-mcp-server", "mysql_mcp_server"]
env_vars = ["MYSQL_HOST", "MYSQL_PORT", "MYSQL_USER", "MYSQL_PASSWORD"]
startup_timeout_sec = 30
tool_timeout_sec = 60
default_tools_approval_mode = "writes"
```

`default_tools_approval_mode = "writes"` 表示查询、读取结构等操作可以直接执行；写入数据、建表、删表、改表等操作需要确认。

## 还需要完成：设置数据库连接信息

连接已固定为本机 MySQL 的 `root` 账号：

```text
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_USER=root
```

为保护密码，密码没有写进 `config.toml` 或项目文件。请打开 Windows 的“编辑账户的环境变量”，在“用户变量”中仅添加：

```text
MYSQL_PASSWORD=替换为 root 的实际密码
```

不要设置 `MYSQL_DATABASE`，即可使用多数据库模式；执行 SQL 时以 `数据库名.表名` 的形式指定目标表。

设置完成后，完全退出并重新启动 Codex 桌面版。

## 验证与使用

1. 在 Codex 新建一个任务，输入 `/mcp`。
2. 确认列表中的 `mysql` 显示为已连接。
3. 先进行只读测试：

```text
读取当前 MySQL 实例中的数据库列表。不要修改任何内容。
```

4. 常用开发指令示例：

```text
读取 web_db_tool_dev 库中所有表的结构，并给出表关系分析。不要执行修改。
```

```text
为本项目设计数据库连接配置表和操作审计表；先输出建表 SQL，不要执行。
```

```text
执行下面这一条 SQL；如果报错，说明原因并给出修正方案。
```

## 当前授权范围

当前配置使用 MySQL `root` 账号。查询、读取表结构等操作可直接进行；会修改数据库的数据定义或数据内容的操作需要由你确认后才会执行。

这只适用于你确认可完全删除或修改的本机开发数据库。不要将 `MYSQL_PASSWORD` 提交到 Git，也不要复制到本说明文件。

## 相关地址

- Codex MCP 配置：<https://learn.chatgpt.com/docs/extend/mcp.md>
- mysql_mcp_server 项目：<https://github.com/designcomputer/mysql_mcp_server>
- uv 安装说明：<https://docs.astral.sh/uv/getting-started/installation/>
