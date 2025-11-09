import { PoseLandmarker, FilesetResolver, DrawingUtils } from "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";

const $ = (s) => document.querySelector(s);
const video = $("#video");
const overlay = $("#overlay");
const ctx = overlay.getContext("2d");
const modeSel = $("#mode");
const sideSel = $("#side");
const startBtn = $("#startLive");
const stopBtn = $("#stopLive");
const switchCameraBtn = $("#switchCamera");
const fileInput = $("#videoFile");
const processBtn = $("#processVideo");
const forceMp4 = $("#forceMp4");
const progressRow = $("#progressRow");
const progressText = $("#progressText");
const cancelBtn = $("#cancelBtn");
const statusEl = $("#status");
const downloadLink = $("#downloadLink");
const resultVideo = $("#result");
const currentAngleEl = $("#currentAngle");
const peakAngleEl = $("#peakAngle");
const displayModeEl = $("#displayMode");

let landmarker;
let runningLive = false;
let currentFacingMode = 'environment'; // 'environment' (back) or 'user' (front)

// Angle utilities (JS port)
const Mode = { ABDUCTION: "ABDUCTION", FLEXION: "FLEXION" };
const Side = { LEFT: "LEFT", RIGHT: "RIGHT" };
const idx = (side, joint) => ({
  shoulder: side === Side.LEFT ? 11 : 12,
  elbow: side === Side.LEFT ? 13 : 14,
  wrist: side === Side.LEFT ? 15 : 16,
  hip: side === Side.LEFT ? 23 : 24,
}[joint]);
const mid = (a, b) => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, z: (a.z + b.z) / 2 });
const angleBetween = (a, b, c) => {
  const ba = [a.x - b.x, a.y - b.y, a.z - b.z];
  const bc = [c.x - b.x, c.y - b.y, c.z - b.z];
  const dot = ba[0]*bc[0] + ba[1]*bc[1] + ba[2]*bc[2];
  const na = Math.hypot(ba[0], ba[1], ba[2]);
  const nc = Math.hypot(bc[0], bc[1], bc[2]);
  const cosv = Math.min(1, Math.max(-1, dot/(na*nc)));
  return (Math.acos(cosv) * 180) / Math.PI;
}
const angle2D = (a,b,c,plane="xy") => {
  const P = (p) => plane === "xz" ? {x:p.x, y:0, z:p.z} : (plane === "yz" ? {x:0,y:p.y,z:p.z}:{x:p.x,y:p.y,z:0});
  return angleBetween(P(a), P(b), P(c));
}
function computeAngle(result, side, mode) {
  if (!result.landmarks?.length) return null;
  const lm = result.landmarks[0];
  const sh = lm[idx(side, 'shoulder')];
  const el = lm[idx(side, 'elbow')];
  const wr = lm[idx(side, 'wrist')];
  const hipL = lm[23], hipR = lm[24];
  const shL = lm[11], shR = lm[12];
  const midHip = mid(hipL, hipR);
  const shouldersZDiff = Math.abs(shL.z - shR.z);

  if (mode === Mode.ABDUCTION && shouldersZDiff > 0.12) return null;
  if (mode === Mode.FLEXION && shouldersZDiff < 0.10) return null;

  switch (mode) {
    case Mode.ABDUCTION: {
      // Abduction: angle between vertical axis and humerus (upper arm bone)
      // 基本軸: 体幹に平行（垂直下向き）で肩峰を通る線
      // 移動軸: 上腕骨（肩→肘）
      // Create a pure vertical reference point below the shoulder
      const verticalDown = {x: sh.x, y: sh.y + 0.5, z: sh.z};
      // Measure angle: verticalDown-shoulder-elbow (base axis → moving axis)
      return angle2D(verticalDown, sh, el, 'xy');
    }
    case Mode.FLEXION: {
      // Flexion: angle between trunk axis and humerus (upper arm bone)
      // 基本軸: 体幹に平行（垂直下向き）で肩峰を通る線
      // 移動軸: 上腕骨（肩→肘）
      // When viewing from the side (shouldersZDiff >= 0.10), flexion is in the xy plane
      // Create a pure vertical reference point below the shoulder
      const verticalDown = {x: sh.x, y: sh.y + 0.5, z: sh.z};
      // Measure angle: verticalDown-shoulder-elbow (base axis → moving axis)
      return angle2D(verticalDown, sh, el, 'xy');
    }
  }
}

function drawOverlay(result, angle, peak, mode, side, skipClear = false) {
  // Only resize canvas if not skipping clear (i.e., not during video processing)
  if (!skipClear) {
    overlay.width = video.videoWidth || overlay.width;
    overlay.height = video.videoHeight || overlay.height;
  }
  if (!skipClear) ctx.clearRect(0,0,overlay.width, overlay.height);
  if (!result?.landmarks?.length) return;
  const utils = new DrawingUtils(ctx);
  const lm = result.landmarks[0];

  // skeleton subset with high contrast: black stroke under white stroke
  const lines = [[11,12],[11,13],[13,15],[12,14],[14,16],[23,24],[11,23],[12,24]];
  ctx.lineCap = 'round'; ctx.lineJoin = 'round';
  ctx.lineWidth = 10; ctx.strokeStyle = 'rgba(0,0,0,.5)';
  for (const [a,b] of lines) utils.drawConnectors([{x:lm[a].x*overlay.width,y:lm[a].y*overlay.height},{x:lm[b].x*overlay.width,y:lm[b].y*overlay.height}], [[0,1]], {color: ctx.strokeStyle, lineWidth: ctx.lineWidth});
  ctx.lineWidth = 6; ctx.strokeStyle = '#fff';
  for (const [a,b] of lines) utils.drawConnectors([{x:lm[a].x*overlay.width,y:lm[a].y*overlay.height},{x:lm[b].x*overlay.width,y:lm[b].y*overlay.height}], [[0,1]], {color: ctx.strokeStyle, lineWidth: ctx.lineWidth});

  // joints
  for (const i of [11,12,13,14,15,16,23,24]) {
    const p = lm[i];
    const x = p.x * overlay.width, y = p.y * overlay.height;
    ctx.fillStyle = 'rgba(0,0,0,.5)'; ctx.beginPath(); ctx.arc(x,y,8,0,Math.PI*2); ctx.fill();
    ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(x,y,5,0,Math.PI*2); ctx.fill();
  }

  // Draw measurement axes (移動軸・基本軸)
  if (lm.length >= 25) {
    const sidx = (j) => side === 'LEFT' ? (j === 'shoulder' ? 11 : (j === 'elbow' ? 13 : 15)) : (j === 'shoulder' ? 12 : (j === 'elbow' ? 14 : 16));
    const sh = lm[sidx('shoulder')], el = lm[sidx('elbow')];
    const hipL = lm[23], hipR = lm[24];

    if (sh && el && hipL && hipR) {
      const midHip = {x: (hipL.x + hipR.x) / 2, y: (hipL.y + hipR.y) / 2};
      const toScreen = (p) => ({x: p.x * overlay.width, y: p.y * overlay.height});
      const shS = toScreen(sh), elS = toScreen(el), hipS = toScreen(midHip);

      // Draw axes based on mode
      ctx.lineWidth = 3; ctx.setLineDash([8, 4]);
      if (mode === 'ABDUCTION') {
        // Moving axis (移動軸): humerus (上腕骨) = shoulder → elbow
        ctx.strokeStyle = 'rgba(255, 200, 0, 0.9)'; // yellow/orange
        ctx.beginPath(); ctx.moveTo(shS.x, shS.y); ctx.lineTo(elS.x, elS.y); ctx.stroke();
        // Base axis (基本軸): vertical line through acromion (体幹に平行で肩峰を通る垂直線)
        ctx.strokeStyle = 'rgba(0, 200, 255, 0.9)'; // cyan
        ctx.beginPath(); ctx.moveTo(shS.x, shS.y - 100); ctx.lineTo(shS.x, shS.y + 100); ctx.stroke();
      } else if (mode === 'FLEXION') {
        // Moving axis (移動軸): humerus (上腕骨) = shoulder → elbow
        ctx.strokeStyle = 'rgba(255, 200, 0, 0.9)'; // yellow/orange
        ctx.beginPath(); ctx.moveTo(shS.x, shS.y); ctx.lineTo(elS.x, elS.y); ctx.stroke();
        // Base axis (基本軸): vertical downward (体幹に平行で垂直下向き)
        const verticalEnd = {x: shS.x, y: shS.y + 150};
        ctx.strokeStyle = 'rgba(0, 200, 255, 0.9)'; // cyan
        ctx.beginPath(); ctx.moveTo(shS.x, shS.y); ctx.lineTo(verticalEnd.x, verticalEnd.y); ctx.stroke();
      }
      ctx.setLineDash([]); // reset
    }
  }

  // angle label + mode/side
  const pad = 8, text = angle!=null ? `${angle.toFixed(1)}°` : '--.-°';
  ctx.font = `${Math.round(overlay.width*0.04)}px system-ui, sans-serif`;
  const tw = ctx.measureText(text).width; const th = overlay.width*0.05;
  const left = (overlay.width - tw)/2 - pad, top = pad*2;
  ctx.fillStyle = 'rgba(0,0,0,.6)'; roundRect(ctx, left, top, tw + pad*2, th + pad*2, 12, true);
  ctx.fillStyle = '#fff'; ctx.fillText(text, left + pad, top + th);

  const sub = `${mode.slice(0,3)} ${side==='LEFT'?'L':'R'}`;
  const stw = ctx.measureText(sub).width; const sth = th;
  const sTop = top + th + pad*2;
  ctx.fillStyle = 'rgba(0,0,0,.6)'; roundRect(ctx, (overlay.width-stw)/2 - pad, sTop, stw+pad*2, sth+pad*2, 12, true);
  ctx.fillStyle = '#fff'; ctx.fillText(sub, (overlay.width-stw)/2, sTop + sth);

  // ROM bar
  const maxAngle = 180;
  const winStart = 150;
  const winEnd = 180;
  const barMargin = 16, barH = 10, bottom = overlay.height - 16, topBar = bottom - barH;
  const xOf = (deg) => (barMargin + Math.max(0, Math.min(1, (deg||0)/maxAngle)) * (overlay.width - barMargin*2));
  ctx.fillStyle = 'rgba(0,0,0,.6)'; ctx.fillRect(barMargin, topBar, overlay.width - barMargin*2, barH);
  ctx.fillStyle = 'rgba(255,255,255,.7)'; ctx.fillRect(xOf(winStart), topBar, xOf(winEnd) - xOf(winStart), barH);
  ctx.strokeStyle = '#fff'; ctx.lineWidth = 2; ctx.strokeRect(barMargin+.5, topBar+.5, overlay.width - barMargin*2-1, barH-1);
  if (angle!=null) { ctx.strokeStyle = '#fff'; ctx.lineWidth=3; const x=xOf(angle); ctx.beginPath(); ctx.moveTo(x, topBar-6); ctx.lineTo(x, bottom+6); ctx.stroke(); }
  if (peak!=null) { ctx.strokeStyle = 'rgba(255,255,255,.6)'; ctx.lineWidth=2; const x=xOf(peak); ctx.beginPath(); ctx.moveTo(x, topBar-4); ctx.lineTo(x, bottom+4); ctx.stroke(); }
}

function roundRect(ctx, x, y, w, h, r, fill) {
  ctx.beginPath();
  ctx.moveTo(x+r, y);
  ctx.arcTo(x+w, y, x+w, y+h, r);
  ctx.arcTo(x+w, y+h, x, y+h, r);
  ctx.arcTo(x, y+h, x, y, r);
  ctx.arcTo(x, y, x+w, y, r);
  ctx.closePath();
  if (fill) ctx.fill();
}

// Pose setup
async function initPose() {
  status('モデル読み込み中…');
  const filesetResolver = await FilesetResolver.forVisionTasks(
    // Use CDN for WASM assets
    "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14/wasm"
  );
  landmarker = await PoseLandmarker.createFromOptions(filesetResolver, {
    baseOptions: {
      // Prefer CDN model; if hosting locally, place the task file and switch to './models/pose_landmarker_lite.task'
      modelAssetPath: "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
    },
    runningMode: 'VIDEO',
    numPoses: 1,
    minPoseDetectionConfidence: 0.6,
    minPosePresenceConfidence: 0.6,
    minTrackingConfidence: 0.6,
  });
  status('準備完了');
}

function status(s) { statusEl.textContent = s; }

// Update angle display panel
function updateAngleDisplay(angle, peak, mode, side) {
  if (angle != null) {
    currentAngleEl.textContent = `${angle.toFixed(1)}°`;
  } else {
    currentAngleEl.textContent = '--';
  }

  if (peak != null) {
    peakAngleEl.textContent = `${peak.toFixed(1)}°`;
  } else {
    peakAngleEl.textContent = '--';
  }

  const modeText = mode === 'ABDUCTION' ? 'ABD' : 'FLEX';
  const sideText = side === 'LEFT' ? 'L' : 'R';
  displayModeEl.textContent = `${modeText} ${sideText}`;
}

// Live webcam
let peakAngle = null;
let emaY = null; const alpha = 0.08;
function ema(x) { emaY = (emaY==null)? x : (alpha*x + (1-alpha)*emaY); return emaY; }

async function startLive() {
  if (!landmarker) await initPose();
  const stream = await navigator.mediaDevices.getUserMedia({
    video: { facingMode: currentFacingMode },
    audio: false
  });
  video.srcObject = stream; await video.play();
  runningLive = true;
  startBtn.disabled = true;
  stopBtn.disabled = false;
  switchCameraBtn.disabled = false;
  peakAngle = null; emaY = null;
  loopLive();
}

function stopLive() {
  runningLive = false;
  startBtn.disabled = false;
  stopBtn.disabled = true;
  switchCameraBtn.disabled = true;
  const s = video.srcObject; if (s) { s.getTracks().forEach(t=>t.stop()); video.srcObject = null; }
  ctx.clearRect(0,0,overlay.width, overlay.height);
  // Reset angle display
  currentAngleEl.textContent = '--';
  peakAngleEl.textContent = '--';
  displayModeEl.textContent = '--';
}

async function switchCamera() {
  if (!runningLive) return;

  // Toggle facing mode
  currentFacingMode = currentFacingMode === 'environment' ? 'user' : 'environment';

  // Stop current stream
  const s = video.srcObject;
  if (s) {
    s.getTracks().forEach(t => t.stop());
  }

  // Start new stream with new facing mode
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: currentFacingMode },
      audio: false
    });
    video.srcObject = stream;
    await video.play();
  } catch (err) {
    console.error('Error switching camera:', err);
    // If switch fails, try to restart with original facing mode
    currentFacingMode = currentFacingMode === 'environment' ? 'user' : 'environment';
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: currentFacingMode },
      audio: false
    });
    video.srcObject = stream;
    await video.play();
  }
}

function loopLive() {
  if (!runningLive) return;
  if (video.readyState >= 2) {
    const now = performance.now();
    const res = landmarker.detectForVideo(video, now);
    const m = modeSel.value, s = sideSel.value;
    const angleRaw = computeAngle(res, s, m);
    let angle = null;
    if (angleRaw!=null) { angle = Math.max(0, Math.min(180, ema(angleRaw))); peakAngle = Math.max(peakAngle||0, angle); }
    drawOverlay(res, angle, peakAngle, m, s);
    updateAngleDisplay(angle, peakAngle, m, s);
  }
  requestAnimationFrame(loopLive);
}

// Video file overlay & export (WebM/MP4 depending on browser)
let cancelRequested = false;
cancelBtn.addEventListener('click', ()=> { cancelRequested = true; status('キャンセルしました'); hideProgress(); });

function showProgress(text) { progressRow.style.display = 'flex'; progressText.textContent = text; }
function hideProgress() { progressRow.style.display = 'none'; }

async function processSelectedVideo() {
  if (!landmarker) await initPose();
  const file = fileInput.files?.[0]; if (!file) return;
  status('動画準備中…'); showProgress('準備中…'); cancelRequested = false;
  downloadLink.style.display = 'none'; downloadLink.href = '';
  const url = URL.createObjectURL(file);
  const v = document.createElement('video');
  v.src = url;
  v.muted = true;
  v.playsInline = true;
  v.style.position = 'absolute';
  v.style.left = '-9999px';
  v.style.visibility = 'hidden';

  // Add video to DOM for WebGL compatibility
  document.body.appendChild(v);

  // Wait for video to be ready
  await new Promise((resolve) => {
    v.onloadedmetadata = () => {
      console.log(`Video loaded: ${v.videoWidth}x${v.videoHeight}, duration: ${v.duration}s`);
      // Set video element dimensions
      v.width = v.videoWidth;
      v.height = v.videoHeight;
      resolve();
    };
  });

  // downscale to ~720p height while keeping aspect
  const scale = v.videoHeight > 720 ? (720 / v.videoHeight) : 1;
  overlay.width = Math.round(v.videoWidth * scale);
  overlay.height = Math.round(v.videoHeight * scale);

  console.log(`Canvas size: ${overlay.width}x${overlay.height}`);

  const mimeMp4 = MediaRecorder.isTypeSupported('video/mp4;codecs=h264');
  const mime = mimeMp4 ? 'video/mp4' : 'video/webm';
  const stream = overlay.captureStream(20);
  const rec = new MediaRecorder(stream, { mimeType: mime });
  const chunks = [];
  rec.ondataavailable = (e)=> { if (e.data?.size) chunks.push(e.data); };
  const done = new Promise(resolve=> rec.onstop = resolve);

  let frameCount = 0;
  let videoPeakAngle = null;
  const drawFrame = () => {
    if (frameCount % 30 === 0) {
      console.log(`Drawing frame ${frameCount}, video time: ${v.currentTime.toFixed(2)}s, readyState: ${v.readyState}`);
    }
    frameCount++;

    ctx.clearRect(0, 0, overlay.width, overlay.height);
    ctx.drawImage(v, 0, 0, overlay.width, overlay.height);
    const res = landmarker.detectForVideo(v, performance.now());
    const m = modeSel.value, s = sideSel.value;
    const angle = computeAngle(res, s, m);
    if (angle != null) {
      videoPeakAngle = Math.max(videoPeakAngle || 0, angle);
    }
    drawOverlay(res, angle, videoPeakAngle, m, s, true); // skipClear=true to preserve video frame
    updateAngleDisplay(angle, videoPeakAngle, m, s);
  };

  status('録画中…'); showProgress('録画中…');
  rec.start(250);

  // Start video from beginning and wait for it to be ready to play
  v.currentTime = 0;
  await new Promise(resolve => {
    v.oncanplay = resolve;
    v.load(); // Ensure video is loaded
  });

  await v.play();

  const step = () => {
    if (cancelRequested) {
      try { v.pause(); rec.stop(); } catch(e){}
      // Cleanup video element
      v.remove();
      URL.revokeObjectURL(url);
      return;
    }
    if (v.paused || v.ended) return;
    drawFrame();
    requestAnimationFrame(step);
  };
  step();
  await new Promise(res => v.onended = res);
  drawFrame();
  rec.stop(); await done;

  // Cleanup video element
  v.remove();
  URL.revokeObjectURL(url);

  let blob = new Blob(chunks, { type: mime });
  // If force MP4 and we recorded WebM, try ffmpeg.wasm transcode
  if (!mimeMp4 && forceMp4.checked) {
    try {
      status('MP4変換中…'); showProgress('MP4変換中… 0%');
      blob = await convertWebMToMp4(blob, (p)=> { if (!cancelRequested) showProgress(`MP4変換中… ${Math.round(p*100)}%`); });
    } catch (e) {
      console.error(e);
      status('変換失敗、WebMを提供');
    }
  }
  const isMp4 = blob.type === 'video/mp4';
  const outUrl = URL.createObjectURL(blob);
  downloadLink.style.display = 'inline-block';
  downloadLink.href = outUrl;
  downloadLink.download = `overlay_${Date.now()}.${isMp4?'mp4':'webm'}`;
  downloadLink.textContent = `ダウンロード (${isMp4?'MP4':'WebM'})`;
  status('完了'); hideProgress();

  // Keep final angle display showing the peak from the video
  // No reset here - user can see the final result

  // Inline playback
  try { stopLive(); } catch(e){}
  resultVideo.style.display = 'block';
  resultVideo.src = outUrl;
  try { await resultVideo.play(); } catch(e) { /* user gesture may be required */ }
}

// Lightweight MP4 conversion with ffmpeg.wasm using mpeg4 codec (broad compatibility)
async function convertWebMToMp4(webmBlob, onProgress) {
  // Use globally loaded FFmpeg.wasm
  if (typeof FFmpeg === 'undefined' || typeof FFmpeg.createFFmpeg === 'undefined') {
    throw new Error('FFmpeg.wasm not loaded');
  }
  const { createFFmpeg, fetchFile } = FFmpeg;
  const ffmpeg = createFFmpeg({
    log: true,
    corePath: 'https://cdn.jsdelivr.net/npm/@ffmpeg/core@0.11.0/dist/ffmpeg-core.js'
  });
  if (onProgress) ffmpeg.setProgress(({ ratio }) => { onProgress(ratio); });
  await ffmpeg.load();
  ffmpeg.FS('writeFile', 'in.webm', await fetchFile(webmBlob));
  // Use mpeg4 codec to avoid libx264 dependency in standard builds
  await ffmpeg.run('-i', 'in.webm', '-c:v', 'mpeg4', '-qscale:v', '2', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-an', 'out.mp4');
  const data = ffmpeg.FS('readFile', 'out.mp4');
  return new Blob([data.buffer], { type: 'video/mp4' });
}

// Wire up
startBtn.addEventListener('click', startLive);
stopBtn.addEventListener('click', stopLive);
switchCameraBtn.addEventListener('click', switchCamera);
fileInput.addEventListener('change', ()=> { processBtn.disabled = !fileInput.files?.length; });
processBtn.addEventListener('click', processSelectedVideo);

status('待機中');
// HTTPS/localhost warning
if (!(location.protocol === 'https:' || location.hostname === 'localhost' || location.hostname === '127.0.0.1')) {
  const el = document.querySelector('#httpsWarn'); if (el) el.style.display = 'block';
}
