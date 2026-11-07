package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ScanRecord
import org.json.JSONArray

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ThreeJsVisualizer(
    scan: ScanRecord,
    zScale: Float,
    renderStyle: String,
    colorThreshold: Float,
    filterMode: String,
    resetTrigger: Long = 0L,
    onFallbackToNative: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Prepare JSON array of data points
    val dataJson = remember(scan) {
        val arr = JSONArray()
        for (v in scan.data) {
            arr.put(v.toDouble())
        }
        arr.toString()
    }

    val htmlContent = remember(scan.id, scan.gridWidth, scan.gridLength) {
        generateThreeJsHtml(
            gridWidth = scan.gridWidth,
            gridLength = scan.gridLength,
            dataJson = dataJson,
            zScale = zScale,
            renderStyle = renderStyle,
            colorThreshold = colorThreshold,
            filterMode = filterMode
        )
    }

    // When parameters update (zScale, renderStyle, etc.), call JavaScript update function directly for 60fps responsiveness!
    LaunchedEffect(zScale, renderStyle, colorThreshold, filterMode) {
        webViewRef?.evaluateJavascript(
            "if (window.updateParams) { window.updateParams($zScale, '$renderStyle', $colorThreshold, '$filterMode'); }",
            null
        )
    }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0L) {
            webViewRef?.evaluateJavascript(
                "if (window.resetCamera) { window.resetCamera(); }",
                null
            )
        }
    }

    LaunchedEffect(scan.data) {
        webViewRef?.evaluateJavascript(
            "if (window.updateData) { window.updateData($dataJson, ${scan.gridWidth}, ${scan.gridLength}); }",
            null
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090C11))
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        allowFileAccess = true
                    }
                    setBackgroundColor(0xFF090C11.toInt())
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(
                                "if (window.updateParams) { window.updateParams($zScale, '$renderStyle', $colorThreshold, '$filterMode'); }",
                                null
                            )
                        }

                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                            // Returning true prevents the Android host app process from crashing
                            try {
                                view?.let {
                                    (it.parent as? android.view.ViewGroup)?.removeView(it)
                                    it.destroy()
                                }
                            } catch (_: Exception) {}
                            onFallbackToNative?.invoke()
                            return true
                        }
                    }
                    loadDataWithBaseURL("https://okm.visualizer.local/", htmlContent, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            update = { view ->
                webViewRef = view
            },
            onRelease = { webView ->
                try {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.destroy()
                } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Generates the complete HTML5 WebGL page with Three.js and OrbitControls
 * implementing the OKM/Proton 6-color Laser Neon colormap, dynamic 3D vertex colored surface,
 * glowing laser wireframe, and cyber grid base.
 */
private fun generateThreeJsHtml(
    gridWidth: Int,
    gridLength: Int,
    dataJson: String,
    zScale: Float,
    renderStyle: String,
    colorThreshold: Float,
    filterMode: String
): String {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no, maximum-scale=1.0">
  <title>OKM 3D Laser Engine</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body {
      width: 100%;
      height: 100%;
      overflow: hidden;
      background: radial-gradient(circle at center, #0F1420 0%, #07090E 100%);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      touch-action: none;
    }
    #canvas-container {
      width: 100%;
      height: 100%;
      position: absolute;
      top: 0;
      left: 0;
    }
    #hud-overlay {
      position: absolute;
      bottom: 12px;
      left: 12px;
      background: rgba(15, 20, 30, 0.75);
      border: 1px solid rgba(0, 229, 255, 0.3);
      border-radius: 8px;
      padding: 6px 10px;
      color: #90A0B7;
      font-size: 10px;
      pointer-events: none;
      backdrop-filter: blur(4px);
      display: flex;
      flex-direction: column;
      gap: 3px;
    }
    .hud-title {
      color: #FFC837;
      font-weight: bold;
      font-size: 11px;
    }
    .laser-badge {
      display: inline-block;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #00FF66;
      box-shadow: 0 0 8px #00FF66;
      margin-right: 4px;
    }
  </style>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js"></script>
</head>
<body>
  <div id="canvas-container"></div>
  <div id="hud-overlay">
    <div><span class="laser-badge"></span><span class="hud-title">OKM PROTON 3D LASER ENGINE</span></div>
    <div>شبکه: <span id="hud-grid" style="color:#00E5FF;">$gridWidth × $gridLength</span> | موتور: WebGL GPU</div>
  </div>

  <script>
    // Initial configuration
    let gridWidth = $gridWidth;
    let gridLength = $gridLength;
    let rawData = $dataJson;
    let currentZScale = $zScale;
    let currentStyle = '$renderStyle';
    let currentThreshold = $colorThreshold;
    let currentFilter = '$filterMode';

    const container = document.getElementById('canvas-container');

    // 1. Scene setup (No fog so colors remain 100% bright, crisp and saturated at all distances)
    const scene = new THREE.Scene();

    // 2. Camera setup - Balanced natural viewing distance (~37 units)
    // provides clean padding around the 3D terrain without feeling crowded or oversized
    const camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 1000);
    camera.position.set(18, 22, 24);

    // 3. Renderer setup - Lightweight & resilient on mobile WebGL / WebView
    let renderer = null;
    try {
      renderer = new THREE.WebGLRenderer({
        antialias: false,
        alpha: true,
        powerPreference: 'default',
        precision: 'mediump'
      });
      renderer.setSize(window.innerWidth, window.innerHeight);
      renderer.setPixelRatio(1.0);
      renderer.shadowMap.enabled = false;
      container.appendChild(renderer.domElement);

      renderer.domElement.addEventListener('webglcontextlost', (e) => {
        e.preventDefault();
        console.warn('WebGL context lost');
      }, false);
    } catch (e) {
      console.error('WebGL init error:', e);
    }

    // 4. OrbitControls
    const controls = new THREE.OrbitControls(camera, renderer ? renderer.domElement : container);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.maxPolarAngle = Math.PI / 2.05; // Prevent camera from going underneath ground
    controls.minDistance = 6;
    controls.maxDistance = 120;
    controls.target.set(0, 0.5, 0);

    // 5. Lighting (Clean Cyber Studio setup - balanced and natural without glare)
    // Upper hemisphere sky-ground ambient fill
    const hemiLight = new THREE.HemisphereLight(0xFFFFFF, 0x1E293B, 0.75);
    scene.add(hemiLight);

    // Uniform white ambient fill
    const ambientLight = new THREE.AmbientLight(0xFFFFFF, 0.75);
    scene.add(ambientLight);

    // Key directional sunlight
    const dirLight1 = new THREE.DirectionalLight(0xFFFFFF, 1.05);
    dirLight1.position.set(20, 35, 20);
    scene.add(dirLight1);

    // Cyber Cyan fill light from opposite angle
    const dirLight2 = new THREE.DirectionalLight(0x00E5FF, 0.45);
    dirLight2.position.set(-20, 20, -20);
    scene.add(dirLight2);

    // Gentle bottom bounce light for cavities
    const dirLight3 = new THREE.DirectionalLight(0x3B82F6, 0.35);
    dirLight3.position.set(0, -20, 0);
    scene.add(dirLight3);

    // 6. Subsurface Ground Reference Grid (Base Floor)
    const gridHelper = new THREE.GridHelper(30, 20, 0xFFC837, 0x1B2636);
    gridHelper.position.y = -0.1;
    scene.add(gridHelper);

    // Axis indicator
    const axesHelper = new THREE.AxesHelper(4);
    axesHelper.position.set(-15, 0, -15);
    scene.add(axesHelper);

    // Sweeping Scanner Laser Beam
    const laserGeometry = new THREE.PlaneGeometry(30, 0.3);
    const laserMaterial = new THREE.MeshBasicMaterial({
      color: 0x00FF66,
      side: THREE.DoubleSide,
      transparent: true,
      opacity: 0.75
    });
    const laserBeam = new THREE.Mesh(laserGeometry, laserMaterial);
    laserBeam.rotation.x = Math.PI / 2;
    laserBeam.position.y = 0.05;
    scene.add(laserBeam);

    // 7. Laser Neon Colormap function (Matching Proton/OKM exact vibrant palette):
    // #001BAA Electric Navy/Blue (Cavity / Void / Low signal - clearly illuminated)
    // #0055FF Royal Blue (Low boundary)
    // #00FFFF Electric Cyan (Damp / Ferrous)
    // #00FF66 Laser Neon Green (Normal background soil)
    // #FFFF00 Luminous Yellow (Mineralized / Medium target)
    // #FF0033 Radiant Laser Red (Highest / Metal target)
    function getLaserColor(normVal, threshold) {
      const clamped = Math.max(-1.0, Math.min(2.0, normVal));
      if (threshold > 0 && Math.abs(clamped) < threshold) {
        return new THREE.Color(0x161D2A);
      }

      // Map -1.0 .. 2.0 to 0.0 .. 1.0
      const t = Math.max(0, Math.min(1, (clamped + 1.0) / 3.0));

      const c = new THREE.Color();
      if (t < 0.2) {
        // Deep Electric Navy (0x001BAA) to Royal Blue (0x0055FF)
        c.lerpColors(new THREE.Color(0x001BAA), new THREE.Color(0x0055FF), t / 0.2);
      } else if (t < 0.4) {
        // Royal Blue (0x0055FF) to Electric Cyan (0x00FFFF)
        c.lerpColors(new THREE.Color(0x0055FF), new THREE.Color(0x00FFFF), (t - 0.2) / 0.2);
      } else if (t < 0.6) {
        // Electric Cyan (0x00FFFF) to Laser Neon Green (0x00FF66)
        c.lerpColors(new THREE.Color(0x00FFFF), new THREE.Color(0x00FF66), (t - 0.4) / 0.2);
      } else if (t < 0.8) {
        // Laser Neon Green (0x00FF66) to Luminous Yellow (0xFFFF00)
        c.lerpColors(new THREE.Color(0x00FF66), new THREE.Color(0xFFFF00), (t - 0.6) / 0.2);
      } else {
        // Luminous Yellow (0xFFFF00) to Radiant Laser Red (0xFF0033)
        c.lerpColors(new THREE.Color(0xFFFF00), new THREE.Color(0xFF0033), (t - 0.8) / 0.2);
      }
      return c;
    }

    // Safe memory disposal utility to prevent Chromium GPU memory leaks and crashes
    function safeDispose(obj) {
      if (!obj) return;
      scene.remove(obj);
      if (obj.geometry) {
        try { obj.geometry.dispose(); } catch (e) {}
      }
      if (obj.material) {
        try {
          if (Array.isArray(obj.material)) {
            obj.material.forEach(m => m && m.dispose && m.dispose());
          } else if (obj.material.dispose) {
            obj.material.dispose();
          }
        } catch (e) {}
      }
      if (obj.children && obj.children.length > 0) {
        for (let i = obj.children.length - 1; i >= 0; i--) {
          safeDispose(obj.children[i]);
        }
      }
    }

    // 8. Dynamic Mesh Builder
    let currentMesh = null;
    let currentWireframe = null;
    let currentPoints = null;

    function build3DMesh() {
      safeDispose(currentMesh);
      currentMesh = null;
      safeDispose(currentWireframe);
      currentWireframe = null;
      safeDispose(currentPoints);
      currentPoints = null;

      const w = gridWidth;
      const l = gridLength;
      if (!rawData || rawData.length === 0 || w < 2 || l < 2) return;

      const sizeX = 24;
      const sizeZ = 24 * (l / w);

      // Create plane geometry
      const geometry = new THREE.PlaneGeometry(sizeX, sizeZ, w - 1, l - 1);
      geometry.rotateX(-Math.PI / 2); // Lay flat on XZ plane

      const pos = geometry.attributes.position;
      const colors = [];

      for (let y = 0; y < l; y++) {
        for (let x = 0; x < w; x++) {
          const idx = y * w + x;
          const rawVal = rawData[idx] !== undefined ? rawData[idx] : 250;

          // Check filter mode
          let isFiltered = false;
          if (currentFilter === 'METAL_ONLY' && rawVal < 320) isFiltered = true;
          if (currentFilter === 'CAVITY_ONLY' && rawVal > 220) isFiltered = true;

          // Compute height elevation
          const normHeight = (rawVal - 250) / 200.0;
          const elevation = isFiltered ? 0 : normHeight * 3.5 * currentZScale;

          // Vertex index in PlaneGeometry
          const vertIdx = y * w + x;
          if (vertIdx < pos.count) {
            pos.setY(vertIdx, elevation);
          }

          // Compute laser neon color
          const vertColor = isFiltered ? new THREE.Color(0x161D2A) : getLaserColor(normHeight, currentThreshold);
          colors.push(vertColor.r, vertColor.g, vertColor.b);
        }
      }

      geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
      geometry.computeVertexNormals();

      if (currentStyle === 'WIREFRAME') {
        const wireMat = new THREE.MeshBasicMaterial({
          vertexColors: true,
          wireframe: true,
          wireframeLinewidth: 2
        });
        currentMesh = new THREE.Mesh(geometry, wireMat);
        scene.add(currentMesh);
      } else if (currentStyle === 'POINTS') {
        const pointMat = new THREE.PointsMaterial({
          size: 1.5,
          vertexColors: true,
          transparent: true,
          opacity: 0.95
        });
        currentPoints = new THREE.Points(geometry, pointMat);
        scene.add(currentPoints);
      } else if (currentStyle === 'VOXEL') {
        // Group of extruded pillars
        const voxelGroup = new THREE.Group();
        const pillarW = (sizeX / w) * 0.85;
        const pillarD = (sizeZ / l) * 0.85;

        for (let y = 0; y < l; y++) {
          for (let x = 0; x < w; x++) {
            const idx = y * w + x;
            const rawVal = rawData[idx] !== undefined ? rawData[idx] : 250;
            const normHeight = (rawVal - 250) / 200.0;
            const height = Math.max(0.2, (normHeight + 1.2) * 2.5 * currentZScale);
            const vertColor = getLaserColor(normHeight, currentThreshold);

            const pillarGeo = new THREE.BoxGeometry(pillarW, height, pillarD);
            const pillarMat = new THREE.MeshPhongMaterial({
              color: vertColor,
              shininess: 40,
              specular: 0x445566
            });
            const pillarMesh = new THREE.Mesh(pillarGeo, pillarMat);

            const posX = (x / (w - 1) - 0.5) * sizeX;
            const posZ = (y / (l - 1) - 0.5) * sizeZ;
            pillarMesh.position.set(posX, height / 2, posZ);
            voxelGroup.add(pillarMesh);
          }
        }
        currentMesh = voxelGroup;
        scene.add(currentMesh);
      } else {
        // Default: Shaded SURFACE with clean balanced luminosity and glowing wireframe overlay
        const surfaceMat = new THREE.MeshPhongMaterial({
          vertexColors: true,
          side: THREE.DoubleSide,
          shininess: 24,
          specular: 0x334455,
          flatShading: false
        });
        currentMesh = new THREE.Mesh(geometry, surfaceMat);
        currentMesh.receiveShadow = true;
        currentMesh.castShadow = true;
        scene.add(currentMesh);

        // Subtle glowing wireframe overlay for high-tech look
        const wireMat = new THREE.MeshBasicMaterial({
          color: 0x00FFFF,
          wireframe: true,
          transparent: true,
          opacity: 0.22
        });
        currentWireframe = new THREE.Mesh(geometry, wireMat);
        scene.add(currentWireframe);
      }
    }

    // Build initial mesh
    build3DMesh();

    // 9. Exposed functions to Android Kotlin via evaluateJavascript
    window.updateParams = function(zScale, style, threshold, filter) {
      currentZScale = zScale;
      currentStyle = style;
      currentThreshold = threshold;
      currentFilter = filter;
      build3DMesh();
    };

    window.updateData = function(data, w, l) {
      rawData = data;
      gridWidth = w;
      gridLength = l;
      document.getElementById('hud-grid').innerText = w + ' × ' + l;
      build3DMesh();
    };

    window.resetCamera = function() {
      camera.position.set(18, 22, 24);
      controls.target.set(0, 0.5, 0);
      controls.update();
    };

    // 10. Animation Loop
    let sweepZ = -15;
    let sweepDir = 1;

    function animate() {
      requestAnimationFrame(animate);

      // Animate laser scanner sweep back and forth
      sweepZ += 0.12 * sweepDir;
      if (sweepZ > 15) sweepDir = -1;
      if (sweepZ < -15) sweepDir = 1;
      laserBeam.position.z = sweepZ;

      controls.update();
      renderer.render(scene, camera);
    }
    animate();

    // Resize handler
    window.addEventListener('resize', () => {
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(window.innerWidth, window.innerHeight);
    });
  </script>
</body>
</html>
    """.trimIndent()
}
