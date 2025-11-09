#!/bin/bash
# Simple HTTP server for the web demo

set -e

cd "$(dirname "$0")/../web"

PORT="${1:-8000}"

echo "Starting HTTP server for Shoulder ROM Web Demo..."
echo "Open http://localhost:${PORT}/ in your browser"
echo ""
echo "Note: Use HTTPS or localhost for camera/video access."
echo "Press Ctrl+C to stop the server."
echo ""

if command -v python3 &> /dev/null; then
    python3 -m http.server "$PORT"
elif command -v python &> /dev/null; then
    python -m http.server "$PORT"
else
    echo "Error: Python 3 is required to run the web server."
    echo "Please install Python 3 or use an alternative static server:"
    echo "  - npx serve web"
    echo "  - php -S localhost:8000 -t web"
    exit 1
fi
