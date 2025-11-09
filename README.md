# ShoulderROM — Shoulder Range of Motion Measurement

A web and mobile application for measuring shoulder range of motion (ROM) using computer vision and MediaPipe Pose estimation. Runs entirely on-device with no server required.

## 🌐 Web App (Recommended)

**Try it now**: The web version works directly in your browser with no installation required!

### Features
- ✅ Live webcam pose tracking with real-time angle measurement
- ✅ Video upload with overlay rendering and export (WebM/MP4)
- ✅ Supports 3 ROM modes: Abduction (ABD), Flexion (FLEX), Extension (EXT)
- ✅ Left/Right side selection
- ✅ Skeleton overlay with angle display and ROM progress bar
- ✅ 100% client-side processing — no data upload, complete privacy
- ✅ Works on desktop and mobile browsers

### Quick Start (Local)

```bash
cd web
python3 -m http.server 8000
# Open http://localhost:8000/ in your browser
```

**Requirements**: Modern browser with camera support (Chrome, Edge, Safari)

### Deploy to Production

Multiple deployment options available — **all FREE**:

| Platform | Deploy Time | Difficulty | Recommended For |
|----------|-------------|------------|-----------------|
| **[Netlify](web/DEPLOYMENT.md#option-1-netlify-recommended---easiest)** | < 5 min | ⭐ Easy | Everyone |
| **[Vercel](web/DEPLOYMENT.md#option-2-vercel)** | < 5 min | ⭐ Easy | Developers |
| **[GitHub Pages](web/DEPLOYMENT.md#option-3-github-pages)** | < 10 min | ⭐⭐ Medium | Open Source |
| **[Firebase](web/DEPLOYMENT.md#option-4-firebase-hosting)** | < 10 min | ⭐⭐ Medium | Google Cloud users |

**📖 Full deployment guide**: [web/DEPLOYMENT.md](web/DEPLOYMENT.md)

#### Fastest Deploy (Netlify)

1. Push this repo to GitHub
2. Go to [netlify.com](https://www.netlify.com/) and sign in
3. "Add new site" → "Import from Git" → Select your repo
4. Set base directory: `web`
5. Deploy! ✨

Your app will be live with HTTPS at `https://yourname.netlify.app`

---

## 📱 Android App (Optional)

Native Android app with the same features plus PNG export and CSV data logging.

### Features
- Live camera feed with pose overlay
- Save snapshots as PNG with angle overlay
- Export session data as CSV (timestamp, mode, side, angle)
- Process videos and burn-in skeleton + angle overlay
- Background processing with progress indicator

### Setup

1. **Prerequisites**
   - Android Studio (Jellyfish/Koala or newer)
   - Java 17
   - Android SDK 34

2. **Download MediaPipe Model**
   - Download: [pose_landmarker_lite.task](https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task)
   - Place in: `app/src/main/assets/pose_landmarker_lite.task`

3. **Build**
   ```bash
   ./gradlew :app:assembleDebug
   ```

4. **Install**
   ```bash
   ./gradlew :app:installDebug
   # Or open in Android Studio and click Run
   ```

### Usage

#### Live Mode
1. Grant camera permission
2. Select Mode (ABD/FLEX/EXT) and Side (L/R)
3. See live angle and peak measurement
4. **Reset Peak**: Clear session
5. **Save PNG**: Save current frame with overlay → `Android/data/com.example.shoulderrom/files/Pictures/`
6. **Export CSV**: Export session data → `Android/data/com.example.shoulderrom/files/Documents/`

#### Video Overlay
1. Tap "Overlay Video"
2. Select a video file
3. Wait for processing (progress bar shows status)
4. Output MP4 → `Android/data/com.example.shoulderrom/files/Movies/`
5. Video plays automatically in built-in player

---

## 📐 ROM Measurement Details

### Supported Modes

| Mode | Full Name | Measurement Plane | Normal Range | Quality Gate |
|------|-----------|-------------------|--------------|--------------|
| **ABD** | Abduction | XY (frontal) | 0-180° | Front view required |
| **FLEX** | Flexion | YZ (sagittal) | 0-180° | Side view required |
| **EXT** | Extension | YZ (sagittal) | 0-50° | Side view required |

**Note**: Extension range follows Japanese Orthopedic Association (日本整形外科学会) and Japanese Association of Rehabilitation Medicine (日本リハビリテーション医学会) standards.

### Quality Gates

The app applies validation rules to ensure accurate measurements:

- **ABD (Abduction)**: Requires front view (shoulders Z-difference < 0.12)
- **FLEX (Flexion)**: Requires side view (shoulders Z-difference > 0.10)
- **EXT (Extension)**: Requires side view (shoulders Z-difference > 0.10)

If quality gates fail, measurement is suppressed (displays `--.-°`).

### Angle Calculation

- **ABD**: angle(elbow, shoulder, hip-midpoint) in XY plane
- **FLEX**: angle(elbow, shoulder, hip-midpoint) in YZ plane
- **EXT**: max(0, angle(hip-midpoint, shoulder, elbow) in YZ - 180°)

All angles are smoothed using Exponential Moving Average (α = 0.08) for stable display.

### Clinical Measurement Standards

#### Abduction (外転)
**Standard Protocol**:
- **Measurement Position**: 立位または座位 (standing or sitting)
- **Fulcrum (支点)**: 肩峰 (acromion)
- **Base Axis (基本軸)**: 体幹正中線に平行な垂直線 (vertical line parallel to body midline)
- **Moving Axis (移動軸)**: 上腕骨 (humerus)
- **Reference ROM (参考可動域)**: 0°〜180°

#### Extension (伸展)
**Standard Protocol** (Japanese Orthopedic Association):
- **Measurement Position**: 腹臥位 (prone/face-down position)
- **Fulcrum (支点)**: 肩峰 (acromion)
- **Base Axis (基本軸)**: Perpendicular line through acromion to floor
- **Moving Axis (移動軸)**: 上腕骨 (humerus, targeting lateral epicondyle)
- **Reference ROM (参考可動域)**: 0°〜50°

**Note**: This app uses standing/sitting position for practical measurement via camera. Results should be interpreted as functional ROM estimates rather than clinical goniometric measurements.

---

## 🛠 Project Structure

```
kata-rom/
├── web/                          # Web application (PRIORITY)
│   ├── index.html                # Main HTML
│   ├── app.js                    # MediaPipe pose logic
│   ├── styles.css                # Minimal styling
│   ├── package.json              # NPM metadata
│   ├── netlify.toml              # Netlify config
│   ├── vercel.json               # Vercel config
│   ├── _headers                  # Security headers
│   ├── DEPLOYMENT.md             # Deployment guide
│   └── README.md                 # Web-specific docs
├── app/                          # Android application
│   ├── src/main/
│   │   ├── java/com/example/shoulderrom/
│   │   │   ├── MainActivity.kt
│   │   │   ├── PlayerActivity.kt
│   │   │   ├── ui/PoseOverlayView.kt
│   │   │   ├── video/VideoOverlay.kt
│   │   │   └── model/Types.kt
│   │   ├── res/layout/
│   │   ├── assets/               # Place pose model here
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── .github/workflows/
│   └── deploy-pages.yml          # GitHub Pages auto-deploy
├── scripts/
│   ├── setup.sh                  # Project setup
│   ├── test.sh                   # Run tests
│   └── serve-web.sh              # Local web server
├── build.gradle                  # Root Gradle config
├── settings.gradle
├── gradle.properties
├── .gitignore
├── AGENTS.md                     # Development guidelines
└── README.md                     # This file
```

---

## 🧪 Testing

### Web App

```bash
cd web
python3 -m http.server 8000
# Open http://localhost:8000/

# Test checklist:
# ✓ Camera permission prompt appears
# ✓ Skeleton overlay renders on webcam
# ✓ Angle measurements update in real-time
# ✓ Mode/Side switching works
# ✓ Video file selection works
# ✓ Video export downloads
```

### Android App

```bash
./scripts/test.sh
# Or: ./gradlew :app:test

# Unit tests:
# ✓ Angle calculation logic
# ✓ Quality gate validation
# ✓ ABD/FLEX view direction gating
```

---

## 🔒 Privacy & Security

- ✅ **100% on-device processing** — No video or images uploaded to servers
- ✅ **No analytics** — No tracking, cookies, or user data collection
- ✅ **No backend** — Static files only, no server-side code
- ✅ **Camera access** — Only used locally, never transmitted
- ✅ **Security headers** — X-Frame-Options, CSP, etc. configured
- ✅ **HTTPS required** — Camera API only works on secure origins

---

## 🌟 Use Cases

- **Physical Therapy**: Track patient ROM progress over time
- **Sports Medicine**: Assess athlete shoulder mobility
- **Telemedicine**: Remote ROM assessment without specialized equipment
- **Research**: Collect ROM data for clinical studies
- **Self-Assessment**: Monitor your own flexibility and recovery

---

## ⚙️ Technical Details

### Web Stack
- Vanilla JavaScript (ES6 modules)
- MediaPipe Tasks Vision (Pose Landmarker) via CDN
- Canvas API for overlay rendering
- MediaRecorder API for video export
- FFmpeg.wasm for WebM→MP4 conversion (optional)

### Android Stack
- Kotlin
- CameraX for camera access
- MediaPipe Tasks Vision (Pose Landmarker)
- MediaCodec for video encoding
- Material Design 3

### Dependencies

#### Web (CDN, no build step)
- `@mediapipe/tasks-vision@0.10.14` (pose estimation)
- `@ffmpeg/ffmpeg@0.11.6` (optional, for MP4 conversion)

#### Android (Gradle)
- `androidx.camera:camera-*:1.3.4` (CameraX)
- `com.google.mediapipe:tasks-vision:0.10.14` (pose estimation)
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1`

---

## 📋 Known Limitations

- **No audio in exported videos** — Intentional for MVP, focuses on visual overlay
- **Video processing speed** — Depends on device performance (~20 FPS re-encode)
- **Browser compatibility** — MediaRecorder H.264 support varies (Chrome/Edge best)
- **Mobile performance** — Older devices may struggle with video processing
- **Thresholds** — Quality gate values may need tuning for different camera distances

---

## 🚀 Roadmap

- [ ] Add more ROM measurements (shoulder adduction, scaption)
- [ ] Multi-session comparison charts
- [ ] Export reports (PDF with charts)
- [ ] Calibration mode for personalized thresholds
- [ ] Audio feedback for target ROM achievement
- [ ] Progressive Web App (PWA) for offline use
- [ ] Elbow and knee ROM measurements

---

## 📄 License

Apache 2.0 (to match MediaPipe licensing)

---

## 🤝 Contributing

Contributions welcome! Please:
1. Read [AGENTS.md](AGENTS.md) for coding guidelines
2. Test locally before submitting PR
3. Update documentation for new features
4. Add unit tests for calculation logic

---

## 📞 Support

- **Deployment issues**: See [web/DEPLOYMENT.md](web/DEPLOYMENT.md)
- **Technical issues**: Check browser console for errors
- **Camera not working**: Ensure HTTPS or localhost
- **MediaPipe errors**: Check CDN connectivity

---

## 🎯 Quick Links

- **Live Web Demo**: Deploy your own in < 5 minutes → [Deployment Guide](web/DEPLOYMENT.md)
- **Development Guide**: [AGENTS.md](AGENTS.md)
- **Web App README**: [web/README.md](web/README.md)
- **MediaPipe Pose**: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker

---

**Start measuring ROM in seconds — no installation required!**

Deploy the web app or run locally: `cd web && python3 -m http.server 8000`
