#!/bin/bash
# ============================================================================
# 脚本名：pg_backup.sh
# 用途：给 studycode 题库数据库做一次全量备份（pg_dump 压缩），并自动清理过期备份。
#       数据库跑在 docker 容器 pgvector 里，库名 app。
#
# 用法：
#   bash pg_backup.sh          # 手动执行一次备份
#
# 部署位置：服务器 /home/ubuntu/pgbackup/backup.sh（本文件是本地副本）
# 定时任务：crontab 每天 03:00 自动跑，保留最近 7 天
#   0 3 * * * /home/ubuntu/pgbackup/backup.sh >> /home/ubuntu/pgbackup/backup.log 2>&1
#
# 输出：/home/ubuntu/pgbackup/app_YYYYmmdd_HHMMSS.sql.gz
# 恢复：gunzip -c 备份文件 | sudo docker exec -i pgvector psql -U postgres -d app
# ============================================================================

BACKUP_DIR=/home/ubuntu/pgbackup
KEEP_DAYS=7
CONTAINER=pgvector
DB=app
STAMP=$(date +%Y%m%d_%H%M%S)
OUT="$BACKUP_DIR/app_${STAMP}.sql.gz"

mkdir -p "$BACKUP_DIR"

# 用 custom format（-Fc），恢复时可用 pg_restore 选择性还原单表
if sudo docker exec "$CONTAINER" pg_dump -U postgres -Fc "$DB" | gzip > "$OUT"; then
    SIZE=$(du -h "$OUT" | cut -f1)
    echo "[$(date '+%F %T')] 备份成功: $OUT ($SIZE)"
else
    echo "[$(date '+%F %T')] 备份失败!"
    rm -f "$OUT"
    exit 1
fi

# 清理过期备份
find "$BACKUP_DIR" -name "app_*.sql.gz" -mtime +$KEEP_DAYS -delete

# 可选：同步一份到 /data 之外的地方（如对象存储）在这里加

echo "[$(date '+%F %T')] 当前备份份数: $(ls -1 "$BACKUP_DIR"/app_*.sql.gz 2>/dev/null | wc -l)"
