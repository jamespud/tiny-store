#!/bin/bash
set -e

# Create all schemas for TinyStore microservices
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Create schemas for each domain service
    CREATE SCHEMA IF NOT EXISTS tinystore_account;
    CREATE SCHEMA IF NOT EXISTS tinystore_auth;
    CREATE SCHEMA IF NOT EXISTS tinystore_inventory;
    CREATE SCHEMA IF NOT EXISTS tinystore_order;
    CREATE SCHEMA IF NOT EXISTS tinystore_payment;
    CREATE SCHEMA IF NOT EXISTS tinystore_product;
    CREATE SCHEMA IF NOT EXISTS tinystore_promotion;
    
    -- Grant privileges
    GRANT ALL PRIVILEGES ON SCHEMA tinystore_account TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON SCHEMA tinystore_auth TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON SCHEMA tinystore_inventory TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON SCHEMA tinystore_order TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON SCHEMA tinystore_payment TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON SCHEMA tinystore_product TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON SCHEMA tinystore_promotion TO $POSTGRES_USER;
EOSQL

echo "All TinyStore schemas created successfully"
