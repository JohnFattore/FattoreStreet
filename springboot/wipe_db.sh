#!/bin/bash

# Configuration from application.properties
DB_NAME="sec-api"
DB_USER="postgres"
# Check if PGPASSWORD is set
if [ -z "$PGPASSWORD" ]; then
    echo "Warning: PGPASSWORD is not set. Attempting with 'postgres'..."
    export PGPASSWORD="postgres"
fi

echo "Wiping database: $DB_NAME..."

# Drop tables and recreate them (handled by Hibernate update/create on restart)
psql -h localhost -U $DB_USER -d $DB_NAME -c "DROP TABLE IF EXISTS daily_prices, quarters, listings, assets CASCADE;"

echo "Database wiped successfully."
