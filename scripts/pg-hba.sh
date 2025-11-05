#!/usr/bin/env bash
set -e
echo "host replication postgres 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"
