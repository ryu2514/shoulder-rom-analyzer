#!/bin/bash
# Run unit tests for the Android app

set -e

echo "Running ShoulderROM unit tests..."
echo ""

cd "$(dirname "$0")/.."

./gradlew :app:test --info

echo ""
echo "Tests completed successfully!"
