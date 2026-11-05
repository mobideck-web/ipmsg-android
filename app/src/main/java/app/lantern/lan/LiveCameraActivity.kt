package app.lantern.lan

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/** Camera access is foreground-only. Starting and recording always involve the owner. */
class LiveCameraActivity : ComponentActivity() {
    private val model by lazy { Model.get(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val cameraThread=android.os.HandlerThread("IPMsg-camera-control").apply{start()}
    private val cameraHandler=Handler(cameraThread.looper)
    private lateinit var previewBox: android.widget.FrameLayout
    private lateinit var preview: TextureView
    private var frameReader: android.media.ImageReader? = null
    @Volatile private var frameRotation=90
    @Volatile private var availableFrames=0L
    @Volatile private var lastPublishedFrameAt=0L
    private var lastFrameAt=0L
    private var frameBuffer:ByteArray?=null
    private val frameWorkspace=CameraFrames.Workspace()
    private val jpegBuffer=ByteArrayOutputStream(64*1024)
    private lateinit var status: TextView
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var server: LiveCameraServer? = null
    private var cameraIdentity: CameraIdentity? = null
    private var cameraAddress: String? = null
    private val deadline = android.os.SystemClock.elapsedRealtime() + 600_000
    @Volatile private var lastViewerAction=""
    @Volatile private var viewerActionAt=0L
    private var size = Size(640, 480)
    private var previewSize = Size(640,480)
    private var orientation = 90
    private var epoch = 0
    @Volatile private var opening = false
    @Volatile private var closing = false
    @Volatile private var recording = false
    @Volatile private var remoteStatus = "Live · no audio"
    @Volatile private var frameBusy = false
    private val encoder = Executors.newSingleThreadExecutor()
    @Volatile private var quality = CameraQuality.SMOOTH
    @Volatile private var qualityPercent=50
    private val peerIp get() = intent.getStringExtra("peerIp").orEmpty()
    private val peerUser get() = intent.getStringExtra("peerUser").orEmpty()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else { message("Camera permission declined"); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quality=CameraQuality.from(model.preferences.getString("camera-quality",null))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        val layout = LinearLayout(this).apply {
            this.orientation = LinearLayout.VERTICAL; setPadding(24, 36, 24, 20); setBackgroundColor(0xff14251f.toInt())
        }
        status = TextView(this).apply { text = "Live camera"; textSize = 18f; setTextColor(0xffeff9f5.toInt()); setPadding(8, 8, 8, 16) }
        layout.addView(status)
        preview = TextureView(this)
        previewBox=android.widget.FrameLayout(this).apply{setBackgroundColor(android.graphics.Color.BLACK);addView(preview,android.widget.FrameLayout.LayoutParams(-1,-1,android.view.Gravity.CENTER))}
        layout.addView(previewBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        previewBox.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->transform()}
        fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layout.addView(this)}
        var controls=row()
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label;isAllCaps=false;textSize=14f;setTextColor(0xffeff9f5.toInt());backgroundTintList=android.content.res.ColorStateList.valueOf(0xff24483d.toInt())
            setOnClickListener { action() };controls.addView(this,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        }
        button("Certificate & setup") {
            val identity = cameraIdentity
            if(identity == null) message("Camera is not ready") else AlertDialog.Builder(this).setTitle("Verify this camera").setMessage("SHA-256 certificate fingerprint:\n${identity.fingerprint}\n\nIn Edge or Chrome, inspect this site's certificate and compare every group with this phone before allowing a site-specific exception. Do not disable certificate validation globally or install a general CA. Reverify if the IP or certificate changes. Managed browsers may refuse local certificates; use photo/video sharing instead.").setPositiveButton("Close",null).show().findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
        }
        button("Send viewing link") { shareLink() }
        controls=row()
        val qualityButton=button("Quality · 50%") {}
        val qualitySlider=android.widget.SeekBar(this).apply {
            max=90;progress=40;visibility=android.view.View.GONE
            setOnSeekBarChangeListener(object:android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:android.widget.SeekBar?,value:Int,fromUser:Boolean){
                    qualityPercent=value+10;qualityButton.text="Quality · $qualityPercent%"
                    quality=if(qualityPercent<=50)CameraQuality.SMOOTH else CameraQuality.CLEAR
                }
                override fun onStartTrackingTouch(bar:android.widget.SeekBar?){}
                override fun onStopTrackingTouch(bar:android.widget.SeekBar?){model.preferences.edit().putInt("camera-quality-percent",qualityPercent).apply()}
            })
        }
        qualityPercent=model.preferences.getInt("camera-quality-percent",50).coerceIn(10,100)
        quality=if(qualityPercent<=50)CameraQuality.SMOOTH else CameraQuality.CLEAR
        qualitySlider.progress=qualityPercent-10;qualityButton.text="Quality · $qualityPercent%"
        qualityButton.setOnClickListener{qualitySlider.visibility=if(qualitySlider.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE}
        layout.addView(qualitySlider)
        button("Stop camera") { finish() }
        setContentView(layout)
        val connectivity=getSystemService(android.net.ConnectivityManager::class.java)
        val initialNetwork=connectivity.activeNetwork
        networkCallback=object:android.net.ConnectivityManager.NetworkCallback(){
            override fun onAvailable(network:android.net.Network){if(network!=initialNetwork) handler.post{if(!closing)finish()}}
            override fun onLost(network:android.net.Network){if(network==initialNetwork) handler.post{if(!closing)finish()}}
        }.also{connectivity.registerDefaultNetworkCallback(it)}
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) { startCamera() }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) { transform() }
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { shutdown(); return true }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) { }
        }
        handler.postDelayed({ if (!closing) { message("Camera session expired"); finish() } }, 600_000)
    }

    private fun message(value: String) { handler.post { if(!isDestroyed) status.text = value } }
    private fun confirm(text: String, action: () -> Unit) {
        if (closing || isFinishing) return
        AlertDialog.Builder(this).setTitle("Owner permission").setMessage(text)
            .setPositiveButton("Allow") { _, _ -> if (!closing) action() }
            .setNegativeButton("Cancel", null).show()
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun startCamera() {
        if(Thread.currentThread()!=cameraThread) {cameraHandler.post{startCamera()};return}
        if (closing || opening || camera != null || !preview.isAvailable) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { handler.post{permission.launch(Manifest.permission.CAMERA)}; return }
        runCatching {
            require(peerIp.isNotBlank() && model.devices.approved(peerIp,peerUser)) { "Device unavailable" }
            val manager = getSystemService(CameraManager::class.java)
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: manager.cameraIdList.first()
            val chars = manager.getCameraCharacteristics(id)
            orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val map = requireNotNull(chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
            val frameSizes=map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888).toSet()
            val sizes = map.getOutputSizes(SurfaceTexture::class.java).filter { it in frameSizes }
            size = sizes.maxByOrNull { it.width * it.height } ?: error("No compatible camera size")
            // Keep the phone preview at PREVIEW size while the YUV stream uses its native output.
            val previewSizes=map.getOutputSizes(SurfaceTexture::class.java).filter{it.width<=1920 && it.height<=1080}
            previewSize=previewSizes.filter{kotlin.math.abs(it.width.toDouble()/it.height-size.width.toDouble()/size.height)<0.03}.maxByOrNull{it.width*it.height}
                ?: previewSizes.maxByOrNull{it.width*it.height} ?: size
            preview.surfaceTexture!!.setDefaultBufferSize(previewSize.width, previewSize.height)
            surface = Surface(preview.surfaceTexture)
            frameReader=android.media.ImageReader.newInstance(size.width,size.height,android.graphics.ImageFormat.YUV_420_888,3).also { reader->
                reader.setOnImageAvailableListener({source->
                    val image=runCatching{source.acquireLatestImage()}.getOrNull() ?: return@setOnImageAvailableListener
                    availableFrames++
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(closing || frameBusy || now-lastFrameAt<quality.intervalMs){image.close();return@setOnImageAvailableListener}
                    frameBusy=true;lastFrameAt=now
                    try{encoder.execute{
                        try{
                            val rotation=frameRotation;val percent=qualityPercent
                            val sw=((image.width*percent/100) and -2).coerceAtLeast(2);val sh=((image.height*percent/100) and -2).coerceAtLeast(2)
                            val count=sw*sh*3/2
                            val output=frameBuffer?.takeIf{it.size==count} ?: ByteArray(count).also{frameBuffer=it}
                            val planes=image.planes.map{CameraFrames.Plane(it.buffer,it.rowStride,it.pixelStride)}
                            val (w,h)=CameraFrames.packScaled(image.width,image.height,planes[0],planes[1],planes[2],rotation,percent,output,frameWorkspace)
                            jpegBuffer.reset()
                            check(android.graphics.YuvImage(output,android.graphics.ImageFormat.NV21,w,h,null).compressToJpeg(android.graphics.Rect(0,0,w,h),(45+percent*50/100),jpegBuffer))
                            if(!closing){server?.frame=jpegBuffer.toByteArray();lastPublishedFrameAt=android.os.SystemClock.elapsedRealtime()}
                        }catch(error:Exception){if(!closing)android.util.Log.w("IPMsgCamera","Frame skipped",error)}finally{image.close();frameBusy=false}
                    }}catch(_:java.util.concurrent.RejectedExecutionException){image.close();frameBusy=false}
                },cameraHandler)
            }
            opening = true
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    opening = false
                    if (closing) { device.close(); return }
                    camera = device
                    encoder.execute {
                        runCatching {
                            val local = DatagramSocket().use { it.connect(InetAddress.getByName(peerIp), 2425); it.localAddress.hostAddress!! }
                            val identity = CameraTls.load(this@LiveCameraActivity,local)
                            cameraIdentity = identity; cameraAddress = local
                            LiveCameraServer(local, peerIp, { handler.post { startRemoteRecording() } }, { handler.post { stopRemoteRecording() } }, { remoteStatus }, tls=identity.context,
                                snapshotTaken={cameraNotice("Receiver captured a photo")})
                        }.onSuccess { live -> handler.post {
                            if (closing) live.close() else {
                                server = live
                                runCatching { createSession(); snapshots.run(); transform(); message("Live · no audio · share the link when ready") }.onFailure { fail(it) }
                            }
                        } }.onFailure { error -> handler.post { fail(error) } }
                    }
                }
                override fun onDisconnected(device: CameraDevice) { device.close(); fail(IllegalStateException("Camera disconnected")) }
                override fun onError(device: CameraDevice, error: Int) { device.close(); fail(IllegalStateException("Camera error $error")) }
            }, cameraHandler)
        }.onFailure { fail(it) }
    }

    @Suppress("DEPRECATION")
    private fun transform() {
        if(!preview.isAvailable || previewBox.width==0 || previewBox.height==0) return
        val rotation=windowManager.defaultDisplay.rotation
        frameRotation=(orientation-rotation*90+360)%360
        val portrait=frameRotation%180!=0
        val imageWidth=if(portrait)previewSize.height else previewSize.width;val imageHeight=if(portrait)previewSize.width else previewSize.height
        val scale=minOf(previewBox.width.toFloat()/imageWidth,previewBox.height.toFloat()/imageHeight)
        val w=(imageWidth*scale).toInt().coerceAtLeast(1);val h=(imageHeight*scale).toInt().coerceAtLeast(1)
        val params=preview.layoutParams as android.widget.FrameLayout.LayoutParams
        if(params.width!=w || params.height!=h){params.width=w;params.height=h;preview.layoutParams=params}
        // SurfaceTexture already handles the camera sensor's portrait rotation. Do not rotate it twice.
        val matrix=android.graphics.Matrix()
        if(rotation==Surface.ROTATION_90 || rotation==Surface.ROTATION_270){
            val view=android.graphics.RectF(0f,0f,w.toFloat(),h.toFloat())
            val buffer=android.graphics.RectF(0f,0f,previewSize.height.toFloat(),previewSize.width.toFloat())
            buffer.offset(view.centerX()-buffer.centerX(),view.centerY()-buffer.centerY())
            matrix.setRectToRect(view,buffer,android.graphics.Matrix.ScaleToFit.FILL)
            val fill=maxOf(h.toFloat()/previewSize.height,w.toFloat()/previewSize.width)
            matrix.postScale(fill,fill,view.centerX(),view.centerY());matrix.postRotate((90*(rotation-2)).toFloat(),view.centerX(),view.centerY())
        }else if(rotation==Surface.ROTATION_180)matrix.postRotate(180f,w/2f,h/2f)
        preview.setTransform(matrix)
    }

    @Suppress("DEPRECATION")
    private fun createSession() {
        if(Thread.currentThread()!=cameraThread) {cameraHandler.post{createSession()};return}
        val device=camera ?: return
        val targets=listOfNotNull(surface,frameReader?.surface)
        val generation=++epoch
        device.createCaptureSession(targets,object:CameraCaptureSession.StateCallback(){
            override fun onConfigured(configured:CameraCaptureSession){
                if(closing || generation!=epoch){configured.close();return}
                session=configured
                runCatching {
                    val request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    targets.forEach(request::addTarget)
                    request.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO)
                    configured.setRepeatingRequest(request.build(),null,cameraHandler)
                }.onFailure{fail(it)}
            }
            override fun onConfigureFailed(configured:CameraCaptureSession){configured.close();fail(IllegalStateException("Camera preview unavailable"))}
        },cameraHandler)
    }

    private val snapshots = object : Runnable {
        override fun run() {
            if (closing) return
            if(!Events.cameraAlertsEnabled(this@LiveCameraActivity)){message("Camera activity notifications are disabled");finish();return}
            val seconds = ((deadline - android.os.SystemClock.elapsedRealtime())/1000).coerceAtLeast(0)
            if(cameraAddress != null && cameraAddress !in LocalAddresses.list() && cameraAddress != "127.0.0.1") { message("Network changed"); finish(); return }
            if(!model.devices.approved(peerIp,peerUser)) { finish(); return }
            status.text = "${if(recording) "● Recording" else "Live · no audio"} · ${seconds/60}:${(seconds%60).toString().padStart(2,'0')} remaining" + if(android.os.SystemClock.elapsedRealtime()-viewerActionAt<8000) "\n$lastViewerAction" else ""
            handler.postDelayed(this,1000)
        }
    }

    private fun cameraNotice(text:String){
        lastViewerAction=text;viewerActionAt=android.os.SystemClock.elapsedRealtime()
        Events.cameraAction(this,"${model.devices.label(peerIp,peerUser)} · $text")
        message(if(recording) "● Recording · $text" else text)
    }
    private fun startRemoteRecording(){
        if(closing || recording)return
        recording=true;remoteStatus="Recording · saved only by receiver"
        cameraNotice("Receiver started recording")
    }
    private fun stopRemoteRecording(){
        if(!recording)return
        recording=false;remoteStatus="Live · no audio"
        cameraNotice("Receiver stopped recording")
    }

    private fun shareLink() {
        val link = server?.url ?: return message("Camera is not ready")
        confirm("Send this temporary live camera link to $peerIp? The receiver can record and capture photos; captures download only to their browser. This phone receives activity alerts only. Viewing uses HTTPS without audio. Verify the certificate fingerprint on this phone before trusting the link in the browser. Keep this screen open.") { share(null, "Live camera (10 minutes, owner-controlled): $link") }
    }
    private fun share(uri: Uri?, text: String = "Live camera recording") {
        val engine = model.engine ?: return message("Start listening before sharing")
        if (model.blocked(peerIp)) return message("Device removed")
        val action = { engine.sendMessage(peerIp, text, uri, allowPlain = !engine.encrypted(peerIp), expectedUser = peerUser) { queued -> handler.post { message(if (queued) "Shared with $peerIp" else "Could not send; check connection") } }; Unit }
        if (!engine.encrypted(peerIp)) confirm("This device cannot use encrypted messaging. Send without encryption?", action) else action()
    }
    private fun fail(error: Throwable) { if(closing) return; android.util.Log.w("IPMsgCamera", "Live camera stopped", error); model.error(error); shutdown(); handler.post{finish()} }
    private fun shutdown() {
        if (closing) return
        closing = true; ++epoch
        networkCallback?.let {runCatching{getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(it)}};networkCallback=null
        handler.removeCallbacksAndMessages(null)
        server?.close(); server = null
        cameraHandler.post {
            recording=false
            runCatching { session?.close() }; session=null
            runCatching { camera?.close() }; camera=null
            surface?.release();surface=null
            val reader=frameReader;frameReader=null;reader?.setOnImageAvailableListener(null,null)
            encoder.execute {reader?.close();encoder.shutdown();cameraThread.quitSafely()}
        }
    }
    override fun onPause() {
        super.onPause()
        if (camera != null || opening || server != null) { shutdown(); finish() }
    }
    override fun onDestroy() { shutdown(); super.onDestroy() }
}

