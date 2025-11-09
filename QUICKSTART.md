# ShoulderROM — Quick Start Guide

Get your ROM measurement app running in **under 5 minutes**!

## 🚀 Option 1: Deploy to Netlify (FASTEST)

**Time: < 5 minutes | No coding required**

1. **Push to GitHub**
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/yourusername/kata-rom.git
   git push -u origin main
   ```

2. **Deploy on Netlify**
   - Go to https://app.netlify.com/
   - Click "Add new site" → "Import an existing project"
   - Choose GitHub and select your repository
   - **Base directory**: `web`
   - **Build command**: (leave empty)
   - **Publish directory**: `.` (dot)
   - Click "Deploy site"

3. **Done!** 🎉
   - Your app is live at `https://yourname.netlify.app`
   - Camera works automatically (HTTPS enabled)
   - Free forever for personal projects

**Full guide**: [web/DEPLOYMENT.md](web/DEPLOYMENT.md)

---

## 💻 Option 2: Run Locally

**Time: < 1 minute**

### Python (Recommended)
```bash
cd web
python3 -m http.server 8000
# Open http://localhost:8000/
```

### Node.js
```bash
npx serve web
# Open http://localhost:3000/
```

### Or use the script
```bash
./scripts/serve-web.sh
# Open http://localhost:8000/
```

**Note**: Camera requires HTTPS in production, but works on `localhost` for development.

---

## 📱 Option 3: Android App

**Time: ~10 minutes | Requires Android Studio**

1. **Download MediaPipe Model**
   - Get: https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
   - Place in: `app/src/main/assets/pose_landmarker_lite.task`

2. **Build & Install**
   ```bash
   ./gradlew :app:assembleDebug
   ./gradlew :app:installDebug
   ```

3. **Run on Device**
   - Grant camera permission
   - Select mode (ABD/FLEX/EXT) and side (L/R)
   - Start measuring!

**Full guide**: [README.md](README.md#android-app-optional)

---

## ✅ What to Expect

### Web App Features
- ✅ Live webcam with real-time angle measurement
- ✅ Skeleton overlay showing pose landmarks
- ✅ 3 ROM modes: ABD, FLEX, EXT
- ✅ Video upload and export with overlays
- ✅ Works on desktop and mobile browsers
- ✅ 100% private (no data upload)

### First Use
1. Grant camera permission when prompted
2. Select a ROM mode:
   - **ABD** = Shoulder abduction (腕を横に上げる)
   - **FLEX** = Shoulder flexion (腕を前に上げる)
   - **EXT** = Shoulder extension (腕を後ろに引く)
3. Select side (Left or Right)
4. Click "Start Webcam"
5. Position yourself correctly:
   - **ABD**: Face camera directly
   - **FLEX**: Stand sideways to camera
   - **EXT**: Stand sideways to camera, arm behind body
6. See your ROM angle in real-time!

---

## 🎯 Recommended Choice

| Goal | Best Option | Why |
|------|-------------|-----|
| **Share with others** | Netlify | Free, fast, HTTPS automatic |
| **Quick test** | Python local server | One command |
| **Mobile app** | Android build | Offline, PNG/CSV export |
| **Developer workflow** | Vercel | Great CLI, GitHub integration |

**Most users**: Deploy to Netlify for instant, shareable access.

---

## 🐛 Common Issues

### "Camera not working"
- ✅ Production: Ensure deployed (Netlify/Vercel give HTTPS)
- ✅ Local: Use `localhost:8000` not your IP address
- ✅ Browser: Try Chrome or Edge

### "MediaPipe models not loading"
- ✅ Check internet connection (models load from CDN)
- ✅ Try different network (some block googleapis.com)
- ✅ Check browser console for errors

### "Cannot open index.html directly"
- ❌ `file:///path/to/index.html` won't work
- ✅ Must use HTTP server (Python, Node, etc.)
- ℹ️ ES6 modules require a server

---

## 📚 Next Steps

- **Deploy to production**: [web/DEPLOYMENT.md](web/DEPLOYMENT.md)
- **Understand ROM calculations**: [README.md](README.md#rom-measurement-details)
- **Customize the app**: [web/README.md](web/README.md#development)
- **Build Android app**: [README.md](README.md#android-app-optional)

---

## 🆘 Need Help?

- **Deployment issues**: See [web/DEPLOYMENT.md](web/DEPLOYMENT.md#troubleshooting)
- **Technical questions**: Check [README.md](README.md#support)
- **Browser errors**: Open DevTools console (F12) for details

---

**Ready to deploy?** Follow Option 1 above → **< 5 minutes to live app!** 🚀
