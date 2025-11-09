# Shoulder ROM — Web Application

A fully client-side web application for measuring shoulder range of motion using MediaPipe Pose estimation. No server required, runs entirely in your browser.

## ✨ Features

- **Live Webcam Tracking**: Real-time pose estimation with skeleton overlay
- **Video Processing**: Upload videos and export with angle overlays (WebM/MP4)
- **3 ROM Modes**: Abduction (ABD), Flexion (FLEX), Extension (EXT)
- **Side Selection**: Left/Right shoulder measurement
- **Quality Gates**: Automatic validation for accurate measurements
- **Privacy First**: 100% on-device processing, no data upload
- **Cross-Platform**: Works on desktop and mobile browsers

---

## 🚀 Quick Start (Local Development)

### Option 1: Python (Recommended)
```bash
python3 -m http.server 8000
# Open http://localhost:8000/
```

### Option 2: Node.js
```bash
npx serve .
# Open http://localhost:3000/
```

### Option 3: PHP
```bash
php -S localhost:8000
# Open http://localhost:8000/
```

**Note**: Module imports require a server — you cannot open `index.html` directly via `file://`

---

## 🌍 Deploy to Production

Ready to share your app with the world? Choose a deployment platform:

### 🏆 Recommended: Netlify (< 5 minutes)

**Easiest and fastest deployment option**

1. Sign in to [Netlify](https://www.netlify.com/)
2. Click "Add new site" → "Import an existing project"
3. Connect to GitHub and select your repository
4. **Base directory**: `web`
5. **Build command**: (leave empty)
6. **Publish directory**: `.`
7. Click "Deploy site"

✅ Your app is now live with HTTPS!

**See full guide**: [DEPLOYMENT.md](DEPLOYMENT.md#option-1-netlify-recommended---easiest)

### Other Options

- **Vercel**: [Deployment Guide](DEPLOYMENT.md#option-2-vercel)
- **GitHub Pages**: [Deployment Guide](DEPLOYMENT.md#option-3-github-pages)
- **Firebase Hosting**: [Deployment Guide](DEPLOYMENT.md#option-4-firebase-hosting)

📖 **Complete deployment documentation**: [DEPLOYMENT.md](DEPLOYMENT.md)

---

## 📱 Usage

### Live Webcam Mode

1. **Grant Camera Permission**: Click "Allow" when prompted
2. **Select Mode**: Choose ABD, FLEX, or EXT
3. **Select Side**: Choose Left or Right shoulder
4. **Click "Start Webcam"**
5. **Position yourself**:
   - **ABD**: Face the camera (front view)
   - **FLEX**: Stand sideways (side view)
   - **EXT**: Stand sideways (side view, arm behind body)
6. **See real-time angle** displayed on screen
7. **Stop** when done

### Video Processing Mode

1. **Select a video file** using the file input
2. **Choose Mode and Side** before processing
3. **Click "Overlay & Export"**
4. Wait for processing (progress shown)
5. **Download** the processed video with overlays

**Output formats**:
- WebM (most browsers)
- MP4 (if browser supports H.264 MediaRecorder)
- Option to force MP4 conversion using FFmpeg.wasm

---

## 🎯 Quality Gates

The app validates pose quality to ensure accurate measurements:

| Mode | Requirement | Reason |
|------|-------------|--------|
| ABD | Front view (shoulders aligned in Z-axis) | Frontal plane measurement |
| FLEX | Side view (shoulders separated in Z-axis) | Sagittal plane measurement |
| EXT | Side view (shoulders separated in Z-axis) | Sagittal plane measurement |

If quality gates fail, angle displays as `--.-°` until position is corrected.

---

## 🛠 Technical Details

### Dependencies (All CDN-based)

- **MediaPipe Tasks Vision** (`@mediapipe/tasks-vision@0.10.14`)
  - Pose Landmarker model
  - WASM runtime
- **FFmpeg.wasm** (`@ffmpeg/ffmpeg@0.11.6`) — Optional, for WebM→MP4 conversion

### Browser Compatibility

| Browser | Webcam | Video Upload | Video Export (WebM) | Video Export (MP4) |
|---------|--------|--------------|---------------------|-------------------|
| Chrome 90+ | ✅ | ✅ | ✅ | ✅ |
| Edge 90+ | ✅ | ✅ | ✅ | ✅ |
| Firefox 88+ | ✅ | ✅ | ✅ | ⚠️ Via FFmpeg |
| Safari 14+ | ✅ | ✅ | ⚠️ Via FFmpeg | ⚠️ Via FFmpeg |

**Recommendation**: Chrome or Edge for best experience

### Security Headers

All deployment configs include:
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `X-XSS-Protection: 1; mode=block`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: camera=*` (required for camera access)

### HTTPS Requirement

Camera access requires secure context:
- ✅ `https://` (production)
- ✅ `http://localhost` (development)
- ✅ `http://127.0.0.1` (development)
- ❌ `http://` over network (blocked)

All deployment platforms provide automatic HTTPS.

---

## 📐 Angle Calculation

### Abduction (ABD)
```
Angle = angle(elbow, shoulder, hip-midpoint) in XY plane
Range: 0-180°
```

### Flexion (FLEX)
```
Angle = angle(elbow, shoulder, hip-midpoint) in YZ plane
Range: 0-180°
```

### Extension (EXT)
```
Angle = max(0, angle(hip-midpoint, shoulder, elbow) in YZ - 180°)
Range: 0-50° (Japanese Orthopedic Association standard)
```

**Clinical Standard**: The 0-50° range follows the reference ROM defined by the Japanese Orthopedic Association (日本整形外科学会) and Japanese Association of Rehabilitation Medicine (日本リハビリテーション医学会). Standard clinical measurement uses prone position with fulcrum at acromion (肩峰).

**Smoothing**: Exponential Moving Average (α = 0.08) applied for stable display

---

## 🎨 UI Components

- **Video Element**: Displays webcam or uploaded video
- **Canvas Overlay**: Renders skeleton and angle text
- **Mode/Side Selectors**: Dropdown menus for measurement configuration
- **Control Buttons**: Start/Stop webcam, process video
- **Progress Indicator**: Shows video processing status
- **ROM Bar**: Visual progress bar showing angle relative to normal range
- **Download Link**: Appears after video processing

---

## 📦 File Structure

```
web/
├── index.html          # Main HTML page
├── app.js              # Core logic (ES6 module)
├── styles.css          # Minimal styling
├── package.json        # NPM metadata
├── netlify.toml        # Netlify deployment config
├── vercel.json         # Vercel deployment config
├── _headers            # Netlify security headers
├── DEPLOYMENT.md       # Comprehensive deployment guide
└── README.md           # This file
```

---

## 🔧 Development

### Code Structure

```javascript
// app.js organization
├── Imports (MediaPipe, DrawingUtils)
├── DOM references
├── Angle calculation utilities
│   ├── Mode and Side enums
│   ├── Landmark indexing
│   ├── 3D angle calculation
│   └── Quality gate logic
├── Overlay rendering
│   ├── Skeleton drawing
│   ├── Angle text display
│   └── ROM bar visualization
├── Pose landmarker setup
├── Live webcam handling
└── Video processing & export
```

### Customization

#### Change ROM thresholds:
```javascript
// In computeAngle() function
if (mode === Mode.ABDUCTION && shouldersZDiff > 0.12) return null;
//                                               ^^^^
// Adjust threshold (currently 0.12)
```

#### Modify overlay colors:
```javascript
// In drawOverlay() function
ctx.strokeStyle = '#fff';  // Change skeleton color
ctx.fillStyle = 'rgba(0,0,0,.6)';  // Change background color
```

#### Add custom modes:
```javascript
// 1. Add to Mode enum
const Mode = {
  ABDUCTION: "ABDUCTION",
  FLEXION: "FLEXION",
  CUSTOM: "CUSTOM"  // New mode
};

// 2. Add calculation logic in computeAngle()
// 3. Update UI dropdown in index.html
```

---

## 🐛 Troubleshooting

### Camera not working
- **Check HTTPS**: Camera API requires secure context
- **Check permissions**: Browser may have blocked camera
- **Try another browser**: Use Chrome/Edge for best support

### MediaPipe models not loading
- **Check internet connection**: Models load from CDN
- **Check browser console**: Look for CORS or network errors
- **Try VPN/proxy**: Some regions may block googleapis.com

### Video export fails
- **Check browser compatibility**: Chrome/Edge recommended
- **Try WebM format**: Uncheck "Force MP4"
- **Check disk space**: Large videos need storage
- **Use shorter videos**: Browser may timeout on long videos

### Slow performance
- **Close other tabs**: Free up memory
- **Use desktop**: Mobile devices may struggle
- **Lower video resolution**: Downscale before upload
- **Use modern browser**: Update to latest version

---

## 🧪 Testing Checklist

Before deploying:

- [ ] Webcam starts successfully
- [ ] Skeleton overlay renders correctly
- [ ] Angle measurements update in real-time
- [ ] Mode switching works (ABD/FLEX/EXT)
- [ ] Side switching works (L/R)
- [ ] Quality gates suppress invalid measurements
- [ ] Video file selection works
- [ ] Video processing completes
- [ ] Video download works
- [ ] ROM bar visualizes correctly
- [ ] Works on mobile (iOS/Android)
- [ ] HTTPS enforced in production
- [ ] No console errors

---

## 📊 Performance

### Webcam Mode
- **FPS**: ~30 FPS on modern desktop
- **Latency**: < 50ms pose detection
- **CPU**: ~30-50% on one core

### Video Processing
- **Speed**: ~20 FPS encoding
- **Time**: 1 minute video = ~3-4 minutes processing
- **Memory**: ~500 MB for HD video

---

## 🔒 Privacy & Security

- ✅ **No server communication**: All processing happens in browser
- ✅ **No data storage**: Nothing saved to disk (except user-initiated downloads)
- ✅ **No analytics**: No tracking, cookies, or telemetry
- ✅ **No external APIs**: MediaPipe models cached by browser
- ✅ **Camera stays local**: Video never leaves your device

---

## 📄 License

Apache 2.0 — See [../README.md](../README.md) for details

---

## 🔗 Links

- **Main Project README**: [../README.md](../README.md)
- **Deployment Guide**: [DEPLOYMENT.md](DEPLOYMENT.md)
- **Android App**: [../app/](../app/)
- **MediaPipe Documentation**: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker

---

## 🚀 Deploy Now

**Start in < 5 minutes**:

1. Push to GitHub
2. Go to [Netlify](https://app.netlify.com/)
3. Import repository → Set base to `web`
4. Deploy!

**Full guide**: [DEPLOYMENT.md](DEPLOYMENT.md)

---

**Questions?** See [DEPLOYMENT.md](DEPLOYMENT.md) for troubleshooting or check the main [README](../README.md).
