package io.github.subhamtyagi.ocr;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
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
import android.text.format.Formatter;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 101;

    private List<Size> mMasterResolutionList;
    private List<Size> mHardwareSupportedResolutions;
    private Size mTargetResolution;
    private int mCurrentResolutionIndex = 0;

    private AutoFitTextureView mTextureView;
    private ImageButton mCaptureButton;
    private ImageButton mSwitchCameraButton;
    private ImageButton mCheckCamerasButton;
    private ImageButton mChangeResolutionButton;

    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Size mPreviewSize;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;

    private int mCurrentLensFacing = CameraCharacteristics.LENS_FACING_BACK;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        initializeResolutions();

        mTextureView = findViewById(R.id.texture_view);
        mCaptureButton = findViewById(R.id.btn_capture);
        mSwitchCameraButton = findViewById(R.id.btn_switch_camera);
        mCheckCamerasButton = findViewById(R.id.btn_check_cameras);
        mChangeResolutionButton = findViewById(R.id.btn_change_resolution);

        mCaptureButton.setOnClickListener(v -> captureImage());
        mSwitchCameraButton.setOnClickListener(v -> switchCamera());
        mCheckCamerasButton.setOnClickListener(v -> enumerateCameras()); //

        mChangeResolutionButton.setOnClickListener(v -> showResolutionSelectionDialog());
        mChangeResolutionButton.setEnabled(false);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            startBackgroundThread();
            if (mTextureView.isAvailable()) {
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
        }
    }

    private void initializeResolutions() {
        mHardwareSupportedResolutions = new ArrayList<>();
        mMasterResolutionList = new ArrayList<>(Arrays.asList(
                new Size(3024, 3024),
                new Size(2560, 2560),
                new Size(2160, 2160),
                new Size(2048, 2048),
                new Size(1920, 1920),
                new Size(1600, 1600),
                new Size(1440, 1440),
                new Size(1280, 1280),
                new Size(1200, 1200),
                new Size(1080, 1080),
                new Size(960, 960),
                new Size(720, 720)
        ));
        Collections.sort(mMasterResolutionList, (s1, s2) -> Integer.compare(s2.getWidth() * s2.getHeight(), s1.getWidth() * s1.getHeight()));
        mTargetResolution = new Size(720, 720);
    }

    private void showResolutionSelectionDialog() {
        if (mMasterResolutionList.isEmpty()) {
            Toast.makeText(this, "No resolutions available.", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] resolutionItems = new CharSequence[mMasterResolutionList.size()];
        for (int i = 0; i < mMasterResolutionList.size(); i++) {
            Size s = mMasterResolutionList.get(i);
            String resolutionString = s.getWidth() + "x" + s.getHeight();
            if (i == 0) {
                resolutionItems[i] = "สูงสุด (" + resolutionString + ")";
            } else {
                resolutionItems[i] = resolutionString;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("เลือกความละเอียด");
        builder.setItems(resolutionItems, (dialog, which) -> {
            mTargetResolution = mMasterResolutionList.get(which);
            Toast.makeText(this, "เลือก: " + mTargetResolution.toString(), Toast.LENGTH_SHORT).show();
        });

        builder.create().show();
    }

    // --- START: ADDED MISSING METHOD ---
    /**
     * Logs the details of all available cameras on the device to Logcat.
     * This is useful for debugging camera issues.
     */
    private void enumerateCameras() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = manager.getCameraIdList();
            Log.d(TAG, "===== Camera Enumeration Report =====");
            Log.d(TAG, "Total cameras found: " + cameraIds.length);
            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                String facingStr = (facing == null) ? "Unknown" : (facing == CameraCharacteristics.LENS_FACING_FRONT ? "Front" : "Back");
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                boolean isUsable = map != null && map.getOutputSizes(ImageFormat.JPEG).length > 0;
                String status = isUsable ? "Usable" : "Not Usable";
                Log.d(TAG, "Camera ID: " + cameraId + ", Facing: " + facingStr + ", Status: " + status);
            }
            Log.d(TAG, "=====================================");
            Toast.makeText(this, "Camera list logged to Logcat.", Toast.LENGTH_SHORT).show();
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access camera list.", e);
        }
    }
    // --- END: ADDED MISSING METHOD ---


    private void openCamera(int width, int height) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == mCurrentLensFacing) {
                    mCameraId = cameraId;
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    setupHardwareResolutions(map);
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                    mTextureView.setAspectRatio(1, 1);
                    configureTransform(width, height);
                    manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
                    return;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to open camera", e);
        }
    }

    private void setupHardwareResolutions(StreamConfigurationMap map) {
        if (map == null) return;
        mHardwareSupportedResolutions.clear();
        Size[] outputSizes = map.getOutputSizes(ImageFormat.JPEG);
        if (outputSizes != null) {
            for (Size size : outputSizes) {
                if (size.getWidth() == size.getHeight()) {
                    mHardwareSupportedResolutions.add(size);
                }
            }
        }
        if (!mHardwareSupportedResolutions.isEmpty()) {
            Collections.sort(mHardwareSupportedResolutions, (s1, s2) -> Integer.compare(s2.getWidth() * s2.getHeight(), s1.getWidth() * s1.getHeight()));
            mChangeResolutionButton.setEnabled(true);
            runOnUiThread(() -> Toast.makeText(this, "ตั้งค่าเริ่มต้น: " + mTargetResolution.toString(), Toast.LENGTH_SHORT).show());
        } else {
            mChangeResolutionButton.setEnabled(false);
            runOnUiThread(() -> Toast.makeText(this, "ไม่พบความละเอียด 1:1 ที่รองรับ", Toast.LENGTH_LONG).show());
        }
    }

    private Size findBestCaptureSize(Size target) {
        if (mHardwareSupportedResolutions.isEmpty()) {
            return target;
        }
        Size bestSize = mHardwareSupportedResolutions.get(0);
        for (int i = mHardwareSupportedResolutions.size() - 1; i >= 0; i--) {
            Size supported = mHardwareSupportedResolutions.get(i);
            if (supported.getWidth() >= target.getWidth() && supported.getHeight() >= target.getHeight()) {
                return supported;
            }
        }
        return bestSize;
    }

    private void captureImage() {
        if (mCameraDevice == null) return;
        try {
            final Size captureSize = findBestCaptureSize(mTargetResolution);
            Log.d(TAG, "Target Res: " + mTargetResolution + " | Capture Res: " + captureSize);

            mImageReader = ImageReader.newInstance(captureSize.getWidth(), captureSize.getHeight(), ImageFormat.JPEG, 1);

            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(mImageReader.getSurface());
            outputSurfaces.add(new Surface(mTextureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

            final File file = new File(getExternalFilesDir(null), UUID.randomUUID().toString() + ".jpg");

            ImageReader.OnImageAvailableListener readerListener = reader -> {
                try (Image image = reader.acquireLatestImage()) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                    if (bitmap.getWidth() != mTargetResolution.getWidth() || bitmap.getHeight() != mTargetResolution.getHeight()) {
                        Log.d(TAG, "Resizing image from " + bitmap.getWidth() + "x" + bitmap.getHeight() + " to " + mTargetResolution.toString());
                        bitmap = Bitmap.createScaledBitmap(bitmap, mTargetResolution.getWidth(), mTargetResolution.getHeight(), true);
                    }

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos);
                    byte[] finalBytes = bos.toByteArray();

                    try (FileOutputStream output = new FileOutputStream(file)) {
                        output.write(finalBytes);
                    }
                    runOnUiThread(() -> onImageCaptured(file));
                } catch (IOException e) {
                    Log.e(TAG, "Failed to save image", e);
                }
            };

            mImageReader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Failed to capture image", e);
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to setup capture", e);
        }
    }


    private void onImageCaptured(File imageFile) {
        Uri imageUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".image-cropper.provider", imageFile);

        long fileSizeInBytes = imageFile.length();
        String fileSizeStr = Formatter.formatShortFileSize(this, fileSizeInBytes);
        String fileFormat = "JPEG";
        String resolutionStr = mTargetResolution.getWidth() + "x" + mTargetResolution.getHeight();

        String imageInfo = "ขนาด: " + fileSizeStr + "\n" +
                "ฟอร์แมต: " + fileFormat + "\n" +
                "ความละเอียด: " + resolutionStr;

        new AlertDialog.Builder(this)
                .setTitle("รายละเอียดรูปภาพ")
                .setMessage(imageInfo)
                .setPositiveButton("ตัดภาพ (Crop Image)", (dialog, which) -> {
                    CropImage.activity(imageUri)
                            .setGuidelines(CropImageView.Guidelines.ON)
                            .setAspectRatio(1, 1)
                            .setInitialCropWindowRectangle(getStandardIdCardCropRect(mTargetResolution.getWidth(), mTargetResolution.getHeight()))
                            .start(this);
                })
                .setNegativeButton("ยกเลิก", (dialog, which) -> {
                    imageFile.delete();
                })
                .setCancelable(false)
                .show();
    }


    private void switchCamera() {
        if (mCurrentLensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            mCurrentLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
        } else {
            mCurrentLensFacing = CameraCharacteristics.LENS_FACING_BACK;
        }
        closeCamera();
        openCamera(mTextureView.getWidth(), mTextureView.getHeight());
    }

    private void closeCamera() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }
        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }
        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
    };

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            createCameraPreviewSession();
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface surface = new Surface(texture);
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (mCameraDevice == null) return;
                    mCaptureSession = session;
                    try {
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Failed to start preview session", e);
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(CameraActivity.this, "Failed to configure camera", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create preview session", e);
        }
    }

    private Rect getStandardIdCardCropRect(int imageWidth, int imageHeight) {
        int cropWidth = (int) (imageWidth * 0.95);
        int cropHeight = (int) (cropWidth * (5.4 / 8.56));
        int left = (imageWidth - cropWidth) / 2;
        int top = (imageHeight / 2) - (int)(cropHeight * 0.7);
        int right = left + cropWidth;
        int bottom = top + cropHeight;
        return new Rect(left, top, right, bottom);
    }

    private Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight) {
        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        int maxPreviewWidth = 1920;
        int maxPreviewHeight = 1080;
        for (Size option : choices) {
            if (option.getWidth() <= maxPreviewWidth && option.getHeight() <= maxPreviewHeight) {
                if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, (s1, s2) -> Long.compare((long) s1.getWidth() * s1.getHeight(), (long) s2.getWidth() * s2.getHeight()));
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, (s1, s2) -> Long.compare((long) s1.getWidth() * s1.getHeight(), (long) s2.getWidth() * s2.getHeight()));
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }


    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop background thread", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recreate();
            } else {
                Toast.makeText(this, "Camera permission is required to use this feature.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                CropImage.ActivityResult result = CropImage.getActivityResult(data);
                Intent resultIntent = new Intent();
                resultIntent.putExtra(CropImage.CROP_IMAGE_EXTRA_RESULT, result);
                setResult(RESULT_OK, resultIntent);
                finish();
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Toast.makeText(this, "Image crop failed.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                finish();
            }
        }
    }
}