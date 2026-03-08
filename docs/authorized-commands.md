# 高权限命令白名单（免逐条询问）

> 目的：新会话中提前授权常用高权限命令前缀，避免执行时重复询问。

## 建议预授权前缀

- `docker compose up -d --build`
- `docker compose down --remove-orphans`
- `docker compose ps`
- `docker compose logs`
- `docker compose exec -T mysql env MYSQL_PWD=edc mysql`
- `./scripts/verify.sh`
- `mvn -q -DskipTests package`
- `npm run build`
- `ps -ef`
- `pkill -f`

## 推荐执行顺序

1. 先授权上面的命令前缀。
2. 再执行 `./scripts/verify.sh` 做全链路验收。
3. 故障时执行 `docker compose logs --tail=200 <service>` 定位问题。
