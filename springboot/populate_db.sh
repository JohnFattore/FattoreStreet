#!/bin/bash

BASE_URL="http://localhost:8080"

echo "Scaling the wall... populating assets and listings..."
curl -s "$BASE_URL/admin/load" > /dev/null
echo "Assets and listings loaded."

echo "Collecting tribute... populating financial quarters (this may take a while)..."
curl -s "$BASE_URL/admin/quarters"
echo -e "\nPopulation complete."
