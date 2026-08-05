#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE userdb;
    CREATE DATABASE categorydb;
    CREATE DATABASE eventdb;
    CREATE DATABASE requestdb;
    CREATE DATABASE compilationdb;
    CREATE DATABASE commentdb;
EOSQL