# Shoulder ROM Web App - Deployment Guide

This guide covers multiple deployment options for the Shoulder ROM web application.

## Prerequisites

- A GitHub account (for all deployment options)
- The web app is completely static and runs client-side
- No server-side code or database required
- Camera access requires HTTPS (all deployment platforms provide this)

---

## Option 1: Netlify (Recommended - Easiest)

### Why Netlify?
- Zero configuration deployment
- Automatic HTTPS
- Global CDN
- Free tier is generous
- Automatic deployments from Git

### Deployment Steps

1. **Sign up / Log in to Netlify**
   - Go to https://www.netlify.com/
   - Sign in with your GitHub account

2. **Deploy from GitHub**
   - Click "Add new site" → "Import an existing project"
   - Choose "GitHub" and authorize Netlify
   - Select your `kata-rom` repository
   - Configure build settings:
     - **Base directory**: `web`
     - **Build command**: (leave empty)
     - **Publish directory**: `.` (current directory)
   - Click "Deploy site"

3. **Done!**
   - Your site will be live at `https://random-name.netlify.app`
   - You can customize the subdomain in Site settings
   - Every push to `main` branch auto-deploys

### Custom Domain (Optional)
- Site settings → Domain management → Add custom domain
- Follow DNS configuration instructions

---

## Option 2: Vercel

### Why Vercel?
- Excellent performance
- Edge network
- Simple CLI deployment
- Free tier for personal projects

### Deployment Steps

#### Method A: GitHub Integration

1. **Sign up / Log in to Vercel**
   - Go to https://vercel.com/
   - Sign in with GitHub

2. **Import Project**
   - Click "Add New..." → "Project"
   - Import your `kata-rom` repository
   - Framework Preset: **Other**
   - Root Directory: `web`
   - Build Command: (leave empty)
   - Output Directory: (leave empty)
   - Click "Deploy"

#### Method B: Vercel CLI

```bash
# Install Vercel CLI
npm install -g vercel

# Navigate to web directory
cd web

# Deploy
vercel

# Follow prompts:
# - Login to Vercel
# - Confirm project settings
# - Deploy!

# For production deployment
vercel --prod
```

---

## Option 3: GitHub Pages

### Why GitHub Pages?
- Free hosting from GitHub
- Simple setup
- Good for open source projects

### Deployment Steps

#### Automatic Deployment (GitHub Actions)

1. **Enable GitHub Pages in Repository Settings**
   - Go to your repository on GitHub
   - Settings → Pages
   - Source: **GitHub Actions**

2. **Push the workflow file** (already included)
   - The workflow file is at `.github/workflows/deploy-pages.yml`
   - Push to `main` branch
   - GitHub Actions will automatically deploy

3. **Access your site**
   - Available at: `https://username.github.io/kata-rom/`
   - Check Actions tab for deployment status

#### Manual Deployment

```bash
# Install gh-pages utility
npm install -g gh-pages

# Navigate to project root
cd kata-rom

# Deploy web directory to gh-pages branch
npx gh-pages -d web

# Your site will be live at:
# https://username.github.io/kata-rom/
```

---

## Option 4: Firebase Hosting

### Why Firebase?
- Google infrastructure
- Excellent performance
- Free tier includes HTTPS
- Good integration with other Google services

### Deployment Steps

1. **Install Firebase CLI**
   ```bash
   npm install -g firebase-tools
   ```

2. **Login to Firebase**
   ```bash
   firebase login
   ```

3. **Initialize Firebase in your project**
   ```bash
   cd kata-rom
   firebase init hosting

   # Choose:
   # - Create a new project or use existing
   # - Public directory: web
   # - Single-page app: No
   # - Set up automatic builds: No
   # - Don't overwrite index.html
   ```

4. **Deploy**
   ```bash
   firebase deploy --only hosting
   ```

5. **Access your site**
   - URL will be shown after deployment
   - Format: `https://project-id.web.app`

---

## Testing Before Deployment

Always test locally before deploying:

```bash
# Option 1: Python (simplest)
cd web
python3 -m http.server 8000
# Open http://localhost:8000

# Option 2: Node.js serve
npx serve web
# Open http://localhost:3000

# Option 3: PHP
cd web
php -S localhost:8000
# Open http://localhost:8000
```

**Important**: Camera access requires either:
- `https://` (production)
- `localhost` (development)
- `127.0.0.1` (development)

---

## Environment Variables

This app has NO environment variables or secrets. Everything runs client-side.

---

## Troubleshooting

### Camera not working after deployment
- **Cause**: Site is not served over HTTPS
- **Solution**: All recommended platforms (Netlify, Vercel, GitHub Pages, Firebase) automatically provide HTTPS

### MediaPipe models not loading
- **Cause**: CORS or CDN issues
- **Solution**: Models are loaded from official MediaPipe CDN. Check browser console for errors.

### Video processing is slow
- **Cause**: Client-side processing depends on device performance
- **Solution**: This is expected behavior. Recommend desktop for long videos.

### FFmpeg conversion fails (WebM → MP4)
- **Cause**: Browser doesn't support H.264 MediaRecorder or FFmpeg.wasm loading failed
- **Solution**:
  - Use Chrome/Edge for best compatibility
  - Uncheck "Force MP4" to output WebM instead
  - Some browsers may block FFmpeg.wasm CDN

---

## Post-Deployment Checklist

- [ ] Site loads over HTTPS
- [ ] Camera permission prompt appears
- [ ] Webcam live mode works
- [ ] Video file selection works
- [ ] Skeleton overlay renders correctly
- [ ] Angle measurements display
- [ ] Video export downloads successfully
- [ ] Works on mobile (iOS Safari, Android Chrome)

---

## Performance Optimization

All platforms provide:
- ✅ Global CDN distribution
- ✅ Automatic HTTPS
- ✅ Gzip/Brotli compression
- ✅ HTTP/2 support

### Additional Optimizations (Optional)

The `netlify.toml` and `vercel.json` files already include:
- Aggressive caching for static assets
- Security headers
- Proper MIME types

---

## Monitoring & Analytics

### Netlify Analytics
- Enable in Site settings → Analytics
- Provides traffic stats, bandwidth usage

### Vercel Analytics
- Enable in Project settings → Analytics
- Real-time visitor data

### Google Analytics (Optional)
Add to `index.html` before `</head>`:

```html
<!-- Google Analytics -->
<script async src="https://www.googletagmanager.com/gtag/js?id=GA_MEASUREMENT_ID"></script>
<script>
  window.dataLayer = window.dataLayer || [];
  function gtag(){dataLayer.push(arguments);}
  gtag('js', new Date());
  gtag('config', 'GA_MEASUREMENT_ID');
</script>
```

---

## Cost Estimate

All options are **FREE** for typical usage:

| Platform      | Free Tier Limits                      | Cost After Limit |
|---------------|---------------------------------------|------------------|
| **Netlify**   | 100 GB bandwidth/month, 300 build min | $19/month        |
| **Vercel**    | 100 GB bandwidth/month, unlimited requests | $20/month   |
| **GitHub Pages** | 100 GB bandwidth/month, 10 builds/hour | N/A (soft limits)|
| **Firebase**  | 10 GB storage, 360 MB/day transfer    | Pay as you go    |

For a demo/portfolio site, you'll likely **never hit these limits**.

---

## Updating Your Deployment

All platforms support automatic deployment from Git:

1. Make changes to files in `web/` directory
2. Commit and push to GitHub:
   ```bash
   git add web/
   git commit -m "Update web app"
   git push origin main
   ```
3. Deployment happens automatically (usually within 1-2 minutes)

---

## Rollback

### Netlify
- Deploys → Click on previous deployment → "Publish deploy"

### Vercel
- Deployments → Click on previous deployment → "Promote to Production"

### GitHub Pages
- Actions → Select successful workflow → Re-run jobs

### Firebase
- `firebase hosting:rollback` (requires CLI)

---

## Security Considerations

✅ All sensitive headers are configured in `netlify.toml`, `vercel.json`, and `_headers`:
- X-Frame-Options: DENY (prevents clickjacking)
- X-Content-Type-Options: nosniff
- X-XSS-Protection: 1; mode=block
- Referrer-Policy: strict-origin-when-cross-origin
- Permissions-Policy: camera=* (required for camera access)

✅ No backend = No server-side vulnerabilities
✅ No user data collection = No privacy concerns
✅ All processing happens on-device

---

## Recommended Choice

**For most users**: **Netlify**
- Easiest setup
- Best free tier
- Great documentation
- Automatic SSL
- Deploy previews for PRs

**For developers**: **Vercel**
- Excellent CLI
- Fast edge network
- Great DX (developer experience)

**For open source**: **GitHub Pages**
- Free forever
- No external account needed
- Good for portfolio

---

## Support

If you encounter issues:
1. Check deployment platform status page
2. Review browser console for errors
3. Test locally first: `python3 -m http.server 8000`
4. Check HTTPS is enabled (required for camera)

---

## Quick Start Summary

**Fastest deployment (< 5 minutes)**:

1. Push code to GitHub
2. Go to [Netlify](https://app.netlify.com/)
3. Click "Add new site" → "Import from Git"
4. Select repository, set base directory to `web`
5. Deploy!

Your app will be live instantly with a working camera and all features enabled.
