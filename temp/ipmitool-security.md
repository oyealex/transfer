# ipmitool 密码安全最佳实践

## 问题

命令行参数指定密码 `ipmitool -P password` 会在 `ps aux` 中直接暴露，存在安全风险。

## 安全方案

### 1. 交互式输入（最安全）

不传 `-P`，ipmitool 自动提示输入（不回显）：

```bash
ipmitool -H 192.168.1.100 -U admin sensor
# Password:         ← 手动输入，不回显
```

### 2. `-f` 从文件读取（推荐）

```bash
echo "mypassword" > /tmp/ipmi_pass
chmod 600 /tmp/ipmi_pass

ipmitool -H 192.168.1.100 -U admin -f /tmp/ipmi_pass sensor
```

文件设置为 600 权限，只有属主可读。

### 3. 环境变量 `-E`

```bash
export IPMI_PASSWORD="***"
ipmitool -H 192.168.1.100 -U admin -E sensor
```

`-E` 从环境变量 `IPMI_PASSWORD` 取密码。进程环境变量在 `/proc/<pid>/environ` 中对同用户可见，但不会出现在 `ps aux` 中，比命令行参数安全。

### 4. freeipmi 配置文件

如果换用 freeipmi：

```bash
# ~/.freeipmi/freeipmi.conf
username admin
password mypassword
```

## 安全度对比

| 方式 | ps/top 暴露 | /proc 暴露 | 推荐度 |
|---|---|---|---|
| `-P password` | ❌ 直接可见 | ❌ 可见 | 别用 |
| 环境变量 `-E` | ✅ 不暴露 | ⚠️ 同用户可见 | 可接受 |
| `-f` 文件 | ✅ 可权限控制 | ✅ 可控 | 推荐 |
| 交互式输入 | ✅ | ✅ | 最安全 |

## 推荐做法

脚本中把密码存到 `chmod 600` 的独立文件，用 `-f` 读取；或者用 `-E` 从受保护的环境变量中取。
