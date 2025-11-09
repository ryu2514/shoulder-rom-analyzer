#!/bin/bash
# Setup script for ShoulderROM project

set -e

echo "╔═══════════════════════════════════════════╗"
echo "║     ShoulderROM Setup & Quick Start       ║"
echo "╚═══════════════════════════════════════════╝"
echo ""

# Determine what to set up
if [ "$1" == "android" ]; then
    SETUP_MODE="android"
elif [ "$1" == "web" ]; then
    SETUP_MODE="web"
else
    SETUP_MODE="web"  # Default to web
fi

# ========================================
# Web App Setup (Default & Recommended)
# ========================================

if [ "$SETUP_MODE" == "web" ]; then
    echo "🌐 Setting up Web Application (Recommended)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    # Check Python
    if command -v python3 &> /dev/null; then
        PYTHON_CMD="python3"
        echo "✅ Python 3: $(python3 --version)"
    elif command -v python &> /dev/null; then
        PYTHON_CMD="python"
        echo "✅ Python: $(python --version)"
    else
        echo "⚠️  Python not found (optional)"
        echo "   Install Python or use: npx serve web"
        PYTHON_CMD=""
    fi
    echo ""

    # Check if web directory exists
    if [ ! -d "web" ]; then
        echo "❌ Error: web/ directory not found"
        exit 1
    fi

    echo "✅ Web app files: OK"
    echo ""

    # Make scripts executable
    if [ -f "scripts/serve-web.sh" ]; then
        chmod +x scripts/serve-web.sh
        echo "✅ Scripts: Executable"
    fi
    echo ""

    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🎉 Web App Setup Complete!"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "📋 Quick Start Options:"
    echo ""
    echo "Option 1: Run Locally (Development)"
    echo "  → ./scripts/serve-web.sh"
    echo "  → Open http://localhost:8000/"
    echo ""
    if [ -n "$PYTHON_CMD" ]; then
        echo "Option 2: Direct Python Server"
        echo "  → cd web && $PYTHON_CMD -m http.server 8000"
        echo ""
    fi
    echo "Option 3: Deploy to Production (< 5 min)"
    echo "  → Push to GitHub"
    echo "  → Go to https://app.netlify.com/"
    echo "  → Import repository → Set base to 'web'"
    echo "  → Deploy! (Free HTTPS included)"
    echo ""
    echo "📖 Full deployment guide: web/DEPLOYMENT.md"
    echo "📖 Quick start guide: QUICKSTART.md"
    echo ""

    # Auto-start option
    read -p "🚀 Start local server now? (y/n) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        if [ -n "$PYTHON_CMD" ]; then
            echo ""
            echo "Starting server at http://localhost:8000/"
            echo "Press Ctrl+C to stop"
            echo ""
            cd web && $PYTHON_CMD -m http.server 8000
        else
            echo "Please install Python or run: npx serve web"
        fi
    fi

    exit 0
fi

# ========================================
# Android App Setup (Optional)
# ========================================

if [ "$SETUP_MODE" == "android" ]; then
    echo "📱 Setting up Android Application"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    # Check Java
    if ! command -v java &> /dev/null; then
        echo "❌ Java is not installed"
        echo "   Please install Java 17 or higher"
        echo "   Download: https://adoptium.net/"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
        echo "⚠️  Java 17+ required. Current: $JAVA_VERSION"
        echo "   Download: https://adoptium.net/"
    else
        echo "✅ Java: $(java -version 2>&1 | head -n 1)"
    fi
    echo ""

    # Check Android SDK
    if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
        echo "⚠️  Android SDK not configured"
        echo "   Set ANDROID_HOME or ANDROID_SDK_ROOT"
        echo "   Or create local.properties with sdk.dir=/path/to/sdk"
        echo ""
    else
        echo "✅ Android SDK: ${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
        echo ""
    fi

    # Check MediaPipe model
    MODEL_FILE="app/src/main/assets/pose_landmarker_lite.task"
    if [ ! -f "$MODEL_FILE" ]; then
        echo "⚠️  MediaPipe model not found"
        echo ""
        echo "📥 Download Required:"
        echo "   URL: https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
        echo ""
        echo "   Place in: app/src/main/assets/pose_landmarker_lite.task"
        echo ""
        echo "   Quick download:"
        echo "   curl -o \"$MODEL_FILE\" \"https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task\""
        echo ""
    else
        MODEL_SIZE=$(du -h "$MODEL_FILE" | cut -f1)
        echo "✅ MediaPipe model: $MODEL_SIZE"
        echo ""
    fi

    # Make gradlew executable
    if [ -f gradlew ]; then
        chmod +x gradlew
        echo "✅ Gradle wrapper: Executable"
    fi
    echo ""

    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🎉 Android Setup Complete!"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "📋 Next Steps:"
    echo ""
    echo "1. Download model (if needed):"
    echo "   curl -o \"$MODEL_FILE\" \\"
    echo "     \"https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task\""
    echo ""
    echo "2. Build:"
    echo "   ./gradlew :app:assembleDebug"
    echo ""
    echo "3. Install on device:"
    echo "   ./gradlew :app:installDebug"
    echo ""
    echo "4. Run tests:"
    echo "   ./scripts/test.sh"
    echo ""
    echo "📖 Full Android guide: README.md#android-app-optional"
    echo ""

    exit 0
fi
