package com.example.scanmate;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class CameraCaptureActivity extends AppCompatActivity {

    private static final SparseIntArray DEVICE_ORIENTATIONS = new SparseIntArray();

    static {
        DEVICE_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEVICE_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEVICE_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEVICE_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private TextureView textureCameraPreview;
    private TextView txtCaptureHint;
    private TextView btnFlash;
    private TextView btnHd;
    private TextView btnCaptureDone;
    private TextView btnModeWord;
    private TextView btnModeSignature;
    private TextView btnModeSingle;
    private TextView btnModeContinuous;
    private TextView btnModeErase;

    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private Uri fallbackPhotoUri;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private Size previewSize;
    private String cameraId;
    private String documentTitle;
    private String captureMode;
    private int sensorOrientation = 90;
    private boolean previewReady = false;
    private boolean cameraUnavailable = false;
    private boolean continuousMode = false;
    private boolean flashOff = true;
    private boolean hdEnabled = true;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    startCameraThread();
                    startCameraPreview();
                } else {
                    showHint("未取得相機權限，仍可導入圖片掃描");
                }
            });

    private final ActivityResultLauncher<Uri> fallbackCameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && fallbackPhotoUri != null) {
                    openCropScreen(fallbackPhotoUri);
                } else {
                    showHint("尚未拍攝，請重新按下快門或導入圖片");
                }
            });

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    openCropScreen(uri);
                }
            });

    private final ActivityResultLauncher<String[]> importDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importPdfDocument(uri);
                }
            });

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            cameraUnavailable = false;
            createPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraUnavailable = true;
            closeCamera();
            showHint("相機啟動失敗，請改用導入圖片");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_capture);

        DocumentStore.init(this);

        textureCameraPreview = findViewById(R.id.textureCameraPreview);
        txtCaptureHint = findViewById(R.id.txtCaptureHint);
        btnFlash = findViewById(R.id.btnFlash);
        btnHd = findViewById(R.id.btnHd);
        btnCaptureDone = findViewById(R.id.btnCaptureDone);
        btnModeWord = findViewById(R.id.btnModeWord);
        btnModeSignature = findViewById(R.id.btnModeSignature);
        btnModeSingle = findViewById(R.id.btnModeSingle);
        btnModeContinuous = findViewById(R.id.btnModeContinuous);
        btnModeErase = findViewById(R.id.btnModeErase);

        documentTitle = getIntent().getStringExtra("document_title");
        if (documentTitle == null || documentTitle.trim().isEmpty()) {
            documentTitle = generateDocumentTitle();
        }

        View btnClose = findViewById(R.id.btnCaptureClose);
        View btnMenu = findViewById(R.id.btnCaptureMenu);
        View btnShutter = findViewById(R.id.btnShutter);
        View btnAllFunctions = findViewById(R.id.btnCaptureAllFunctions);
        View btnImportImage = findViewById(R.id.btnCaptureImportImage);
        View btnImportDocument = findViewById(R.id.btnCaptureImportDocument);

        btnClose.setOnClickListener(v -> finish());
        btnCaptureDone.setOnClickListener(v -> finishContinuousCapture());
        btnMenu.setOnClickListener(v -> showCaptureOptions());
        btnFlash.setOnClickListener(v -> toggleFlashState());
        btnHd.setOnClickListener(v -> toggleHdState());
        btnShutter.setOnClickListener(v -> captureStillImage());
        btnAllFunctions.setOnClickListener(v -> startActivity(new Intent(this, ToolboxActivity.class)));
        btnImportImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnImportDocument.setOnClickListener(v -> importDocumentLauncher.launch(new String[]{"application/pdf"}));
        btnModeWord.setOnClickListener(v -> setCaptureWorkflowMode("word", "拍圖轉 Word：拍攝後確認即可進入文字辨識"));
        btnModeSignature.setOnClickListener(v -> setCaptureWorkflowMode("signature", "電子簽名模式：掃描後可在編輯頁加入簽名"));
        btnModeSingle.setOnClickListener(v -> setCaptureWorkflowMode("scan", "單張拍攝模式"));
        btnModeContinuous.setOnClickListener(v -> setContinuousMode(true));
        btnModeErase.setOnClickListener(v -> setCaptureWorkflowMode("erase", "AI 擦除模式：先套用去陰影清理，後續可升級物件擦除"));

        captureMode = getIntent().getStringExtra("capture_mode");
        if (!"append".equals(captureMode)) {
            ScanDraftStore.clear();
        }
        if ("import_image".equals(captureMode)) {
            txtCaptureHint.postDelayed(() -> pickImageLauncher.launch("image/*"), 250);
        } else if ("continuous".equals(captureMode)) {
            setContinuousMode(true);
        } else if ("word".equals(captureMode)) {
            highlightMode(btnModeWord);
            showHint("拍圖轉 Word：拍攝後確認即可進入文字辨識");
        } else if ("signature".equals(captureMode)) {
            highlightMode(btnModeSignature);
            showHint("電子簽名模式：掃描後可在編輯頁加入簽名");
        } else if ("erase".equals(captureMode)) {
            highlightMode(btnModeErase);
            showHint("AI 擦除模式：先套用去陰影清理");
        } else if ("id".equals(captureMode)) {
            if (documentTitle.startsWith("ScanMate ")) {
                documentTitle = "IDScan " + new SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(new Date());
            }
            showHint("證件掃描模式：請將證件置於畫面中央");
        } else if ("book".equals(captureMode)) {
            showHint("書籍掃描模式：請讓頁面攤平並保持光線均勻");
        } else if ("ppt".equals(captureMode)) {
            showHint("PPT 掃描模式：請對準投影片或螢幕邊界");
        } else if ("whiteboard".equals(captureMode)) {
            showHint("白板掃描模式：請避開反光並包含完整白板");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraThread();
        if (!"import_image".equals(captureMode)) {
            startCameraPreview();
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopCameraThread();
        super.onPause();
    }

    private void startCameraPreview() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        if (textureCameraPreview.isAvailable()) {
            openCamera();
        } else {
            textureCameraPreview.setSurfaceTextureListener(textureListener);
        }
    }

    private void openCamera() {
        startCameraThread();
        if (cameraDevice != null) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            cameraUnavailable = false;
            previewReady = false;
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            cameraId = findBackCameraId(manager);
            if (cameraId == null) {
                cameraUnavailable = true;
                showHint("找不到可用相機，請改用導入圖片");
                return;
            }

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orientation == null ? 90 : orientation;
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                cameraUnavailable = true;
                showHint("相機規格讀取失敗");
                return;
            }

            previewSize = chooseSize(map.getOutputSizes(SurfaceTexture.class), 1280, 720);
            Size photoSize = chooseSize(map.getOutputSizes(ImageFormat.JPEG), 1920, 1080);
            imageReader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(reader -> saveCapturedImage(reader), cameraHandler);
            manager.openCamera(cameraId, cameraStateCallback, cameraHandler);
        } catch (Exception e) {
            showHint("相機啟動失敗：" + e.getMessage());
        }
    }

    private String findBackCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        String[] ids = manager.getCameraIdList();
        return ids.length == 0 ? null : ids[0];
    }

    private Size chooseSize(Size[] choices, int targetWidth, int targetHeight) {
        if (choices == null || choices.length == 0) {
            return new Size(targetWidth, targetHeight);
        }

        Size best = choices[0];
        long bestArea = Math.abs((long) best.getWidth() * best.getHeight() - (long) targetWidth * targetHeight);
        for (Size size : choices) {
            long areaDelta = Math.abs((long) size.getWidth() * size.getHeight() - (long) targetWidth * targetHeight);
            if (areaDelta < bestArea) {
                best = size;
                bestArea = areaDelta;
            }
        }
        return best;
    }

    private void createPreviewSession() {
        try {
            SurfaceTexture texture = textureCameraPreview.getSurfaceTexture();
            if (texture == null || cameraDevice == null || previewSize == null) {
                return;
            }

            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraSession = session;
                            try {
                                cameraSession.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler);
                                previewReady = true;
                                runOnUiThread(() -> txtCaptureHint.setVisibility(View.GONE));
                            } catch (Exception e) {
                                showHint("相機預覽失敗");
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            showHint("相機預覽設定失敗");
                        }
                    },
                    cameraHandler
            );
        } catch (Exception e) {
            showHint("相機預覽失敗：" + e.getMessage());
        }
    }

    private void captureStillImage() {
        if (cameraUnavailable) {
            launchFallbackCamera();
            return;
        }

        if (!previewReady || cameraDevice == null || cameraSession == null || imageReader == null) {
            showHint("相機初始化中，請稍候再拍");
            return;
        }

        try {
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            if (!flashOff) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            }
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation());
            cameraSession.capture(captureBuilder.build(), null, cameraHandler);
        } catch (Exception e) {
            cameraUnavailable = true;
            showHint("內建拍攝失敗，改用系統相機");
            launchFallbackCamera();
        }
    }

    private int getJpegOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int deviceOrientation = DEVICE_ORIENTATIONS.get(rotation, 90);
        return (deviceOrientation + sensorOrientation + 270) % 360;
    }

    private void launchFallbackCamera() {
        try {
            File photoFile = new File(getCacheDir(), "scanmate_fallback_" + System.currentTimeMillis() + ".jpg");
            fallbackPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            fallbackCameraLauncher.launch(fallbackPhotoUri);
        } catch (Exception e) {
            showHint("相機無法使用，請改用導入圖片：" + e.getMessage());
        }
    }

    private void saveCapturedImage(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            File photoFile = new File(getCacheDir(), "scanmate_capture_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream outputStream = new FileOutputStream(photoFile)) {
                outputStream.write(bytes);
            }

            Uri imageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            runOnUiThread(() -> {
                if (continuousMode) {
                    processContinuousCapture(imageUri);
                } else {
                    openCropScreen(imageUri);
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> showHint("照片儲存失敗：" + e.getMessage()));
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private void showCaptureOptions() {
        String[] options = {"系統相機備援", "導入圖片", "導入文檔"};
        new AlertDialog.Builder(this)
                .setTitle("拍攝選項")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchFallbackCamera();
                    } else if (which == 1) {
                        pickImageLauncher.launch("image/*");
                    } else {
                        importDocumentLauncher.launch(new String[]{"application/pdf"});
                    }
                })
                .show();
    }

    private void openCropScreen(Uri imageUri) {
        Intent intent = new Intent(this, CropActivity.class);
        intent.putExtra("image_uri", imageUri.toString());
        intent.putExtra("document_title", documentTitle);
        intent.putExtra("capture_mode", captureMode);
        startActivity(intent);
    }

    private void toggleFlashState() {
        flashOff = !flashOff;
        btnFlash.setText(flashOff ? "閃光關" : "閃光開");
        showModeHint(flashOff ? "閃光燈已關閉" : "拍攝時會嘗試自動閃光");
    }

    private void toggleHdState() {
        hdEnabled = !hdEnabled;
        btnHd.setText(hdEnabled ? "HD" : "SD");
        btnHd.setTextColor(hdEnabled ? Color.WHITE : Color.parseColor("#A8ABB2"));
        showModeHint(hdEnabled ? "高畫質掃描已開啟" : "快速掃描模式");
    }

    private void setContinuousMode(boolean enabled) {
        continuousMode = enabled;
        if (enabled) {
            captureMode = "continuous";
            highlightMode(btnModeContinuous);
        } else {
            captureMode = "scan";
            highlightMode(btnModeSingle);
        }
        btnCaptureDone.setVisibility(ScanDraftStore.getPageCount() > 0 ? View.VISIBLE : View.GONE);
        showModeHint(enabled ? "連續拍攝模式：每次快門會加入一頁" : "單張拍攝模式");
    }

    private void setCaptureWorkflowMode(String mode, String hint) {
        continuousMode = false;
        captureMode = mode;
        if ("word".equals(mode)) {
            documentTitle = "WordScan " + new SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(new Date());
            highlightMode(btnModeWord);
        } else if ("signature".equals(mode)) {
            documentTitle = "SignScan " + new SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(new Date());
            highlightMode(btnModeSignature);
        } else if ("erase".equals(mode)) {
            highlightMode(btnModeErase);
        } else {
            highlightMode(btnModeSingle);
        }
        btnCaptureDone.setVisibility(View.GONE);
        showModeHint(hint);
    }

    private void highlightMode(TextView activeButton) {
        TextView[] buttons = {btnModeWord, btnModeSignature, btnModeSingle, btnModeContinuous, btnModeErase};
        for (TextView button : buttons) {
            boolean active = button == activeButton;
            button.setTextColor(active ? Color.parseColor("#40D6C1") : Color.parseColor("#C4C7CC"));
            button.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void finishContinuousCapture() {
        if (ScanDraftStore.getPageCount() == 0) {
            showHint("尚未拍攝任何頁面");
            return;
        }
        startActivity(new Intent(this, ScanPreviewActivity.class));
    }

    private void processContinuousCapture(Uri imageUri) {
        showHint("正在加入掃描頁面");
        new Thread(() -> {
            Bitmap bitmap = decodeBitmap(imageUri);
            Bitmap scanned = bitmap == null ? null : runOpenCvAutoScan(bitmap);
            if (scanned == null) {
                scanned = bitmap;
            }

            Bitmap finalBitmap = scanned;
            if (finalBitmap != null && "id".equals(captureMode)) {
                finalBitmap = IdCardComposer.composeA4Page(finalBitmap, ScanDraftStore.getPageCount() + 1, documentTitle);
            }
            Bitmap pageBitmap = finalBitmap;
            runOnUiThread(() -> {
                if (pageBitmap == null) {
                    showHint("此頁處理失敗，請重新拍攝");
                    return;
                }
                ScanDraftStore.start(imageUri, pageBitmap, documentTitle);
                ScanDraftStore.commitCurrentPage();
                ScanDraftStore.saveDraft(this);
                btnCaptureDone.setVisibility(View.VISIBLE);
                showHint("已加入第 " + ScanDraftStore.getPageCount() + " 頁，可繼續拍攝或按完成");
            });
        }).start();
    }

    private Bitmap decodeBitmap(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap runOpenCvAutoScan(Bitmap bitmap) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }

            PyObject module = Python.getInstance().getModule("scan_cv");
            PyObject result = module.callAttr("canny_process", outputStream.toByteArray());
            byte[] pngBytes = result.toJava(byte[].class);
            return BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void importPdfDocument(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        String title = resolveDisplayName(uri, "Imported_" + System.currentTimeMillis() + ".pdf");
        int pages = countPdfPages(uri);
        String dateTime = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date());
        DocumentStore.add(new DocumentItem(title, dateTime, pages, uri, "PDF"));
        Toast.makeText(this, "已導入文檔：" + title, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, DocumentsActivity.class));
    }

    private int countPdfPages(Uri uri) {
        ParcelFileDescriptor descriptor = null;
        PdfRenderer renderer = null;
        try {
            descriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (descriptor == null) {
                return 1;
            }
            renderer = new PdfRenderer(descriptor);
            return Math.max(1, renderer.getPageCount());
        } catch (Exception ignored) {
            return 1;
        } finally {
            try {
                if (renderer != null) renderer.close();
                if (descriptor != null) descriptor.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String resolveDisplayName(Uri uri, String fallbackName) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String displayName = cursor.getString(nameIndex);
                    if (displayName != null && !displayName.trim().isEmpty()) {
                        return displayName;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fallbackName;
    }

    private void startCameraThread() {
        if (cameraThread != null) {
            return;
        }
        cameraThread = new HandlerThread("ScanMateCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        if (cameraThread == null) {
            return;
        }
        cameraThread.quitSafely();
        try {
            cameraThread.join();
        } catch (InterruptedException ignored) {
        }
        cameraThread = null;
        cameraHandler = null;
    }

    private void closeCamera() {
        try {
            if (cameraSession != null) {
                cameraSession.close();
                cameraSession = null;
            }
            previewReady = false;
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception ignored) {
        }
    }

    private String generateDocumentTitle() {
        return "ScanMate " + new SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(new Date());
    }

    private void showHint(String text) {
        runOnUiThread(() -> {
            txtCaptureHint.setVisibility(View.VISIBLE);
            txtCaptureHint.setText(text);
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        });
    }

    private void showModeHint(String text) {
        runOnUiThread(() -> {
            if (previewReady) {
                txtCaptureHint.setVisibility(View.GONE);
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
            } else {
                txtCaptureHint.setVisibility(View.VISIBLE);
                txtCaptureHint.setText(text);
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
