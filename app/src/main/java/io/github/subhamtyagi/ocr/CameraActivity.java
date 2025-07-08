package io.github.subhamtyagi.ocr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 101;

    private List<Size> mMasterResolutionList;
    private List<Size> mHardwareSupportedResolutions;
    private Size mTargetResolution;

    private AutoFitTextureView mTextureView;
    private ImageButton mCaptureButton;
    private ImageButton mSwitchCameraButton;
    private ImageButton mCheckCamerasButton;
    private ImageButton mChangeResolutionButton;
    private TextView mCameraStatusTextView;

    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Size mPreviewSize;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;

    private int mCurrentLensFacing = CameraCharacteristics.LENS_FACING_BACK;

    public static class CameraInfo {
        public final String title;
        public final String cameraId;
        public final String cameraType;
        @DrawableRes
        public final int iconResId;
        public final boolean isAvailable;
        public final List<String> physicalCameraIds;

        public CameraInfo(String title, String cameraId, String cameraType, @DrawableRes int iconResId, boolean isAvailable, List<String> physicalCameraIds) {
            this.title = title;
            this.cameraId = cameraId;
            this.cameraType = cameraType;
            this.iconResId = iconResId;
            this.isAvailable = isAvailable;
            this.physicalCameraIds = physicalCameraIds;
        }
    }

    private static class CameraListAdapter extends ArrayAdapter<CameraInfo> {
        private final String mActiveCameraId;
        private final Context mContext;
        private static final int TYPE_ITEM = 0;
        private static final int TYPE_DIVIDER = 1;


        public CameraListAdapter(@NonNull Context context, List<CameraInfo> cameras, String activeCameraId) {
            super(context, 0, cameras); // Use 0 for resource ID since we are inflating manually
            this.mContext = context;
            this.mActiveCameraId = activeCameraId;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).cameraType.equals("Divider") ? TYPE_DIVIDER : TYPE_ITEM;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEnabled(int position) {
            // A divider is not clickable
            return getItemViewType(position) == TYPE_ITEM;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            int viewType = getItemViewType(position);
            CameraInfo item = getItem(position);
            LayoutInflater inflater = LayoutInflater.from(mContext);

            if (convertView == null) {
                if (viewType == TYPE_ITEM) {
                    convertView = inflater.inflate(android.R.layout.select_dialog_item, parent, false);
                } else { // TYPE_DIVIDER
                    TextView divider = new TextView(mContext);
                    divider.setPadding(16, 8, 16, 8);
                    divider.setTypeface(null, Typeface.BOLD);
                    divider.setGravity(Gravity.CENTER);
                    convertView = divider;
                }
            }


            if (viewType == TYPE_ITEM) {
                TextView textView = convertView.findViewById(android.R.id.text1);
                String displayText = item.title;

                if (item.isAvailable && item.cameraId.equals(mActiveCameraId)) {
                    displayText += " (Selected)";
                    textView.setTypeface(null, Typeface.BOLD);
                } else {
                    textView.setTypeface(null, Typeface.NORMAL);
                }
                textView.setText(displayText);

                if (item.iconResId != 0) {
                    Drawable icon = ContextCompat.getDrawable(getContext(), item.iconResId);
                    if(icon!=null) icon.setTint(Color.WHITE);
                    textView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                    textView.setCompoundDrawablePadding(24);
                }

                textView.setTextColor(Color.WHITE);

                if (item.isAvailable) {
                    textView.setAlpha(1.0f);
                } else {
                    textView.setAlpha(0.5f);
                }

            } else { // TYPE_DIVIDER
                TextView dividerTextView = (TextView) convertView;
                dividerTextView.setText(item.title);
                dividerTextView.setTextColor(Color.LTGRAY);
            }


            return convertView;
        }
    }


    @SuppressLint("InlinedApi")
    public static List<CameraInfo> enumerateCamerasDetailed(CameraManager cameraManager) {
        Map<String, CameraInfo> detectedCamerasMap = new HashMap<>();
        String[] allCameraIds;
        try {
            allCameraIds = cameraManager.getCameraIdList();
        } catch (Exception e) {
            allCameraIds = new String[0];
        }

        List<String> predefinedCameraTypes = Arrays.asList(
                "Front Camera", "Front Ultra Wide Camera", "Back Camera (Main)",
                "Back Triple Camera", "Back Dual Camera", "Back Ultra Wide Camera",
                "Back Telephoto Camera", "External Camera"
        );
        float ultraWideFocalLengthThreshold = 2.2f;
        float telephotoFocalLengthThreshold = 7.5f;

        for (String id : allCameraIds) {
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer orientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                if (capabilities == null) capabilities = new int[0];
                float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

                boolean isLogicalMultiCamera = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    for (int cap : capabilities) if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) { isLogicalMultiCamera = true; break; }
                }

                List<String> physicalCameraIds = new ArrayList<>();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isLogicalMultiCamera) {
                    physicalCameraIds.addAll(characteristics.getPhysicalCameraIds());
                }

                String determinedType = null;
                if (orientation != null) {
                    switch (orientation) {
                        case CameraCharacteristics.LENS_FACING_FRONT:
                            determinedType = (focalLengths != null && focalLengths.length == 1 && focalLengths[0] < ultraWideFocalLengthThreshold) ? "Front Ultra Wide Camera" : "Front Camera";
                            break;
                        case CameraCharacteristics.LENS_FACING_BACK:
                            if (isLogicalMultiCamera && !physicalCameraIds.isEmpty()) {
                                switch (physicalCameraIds.size()) {
                                    case 3: determinedType = "Back Triple Camera"; break;
                                    case 2: determinedType = "Back Dual Camera"; break;
                                    default: determinedType = "Back Multi-Camera (" + physicalCameraIds.size() + " Lenses)"; break;
                                }
                            } else {
                                boolean isTelephoto = false, isUltraWide = false;
                                if (focalLengths != null) for (float f : focalLengths) {
                                    if (f > telephotoFocalLengthThreshold) isTelephoto = true;
                                    if (f < ultraWideFocalLengthThreshold) isUltraWide = true;
                                }
                                if (isTelephoto) determinedType = "Back Telephoto Camera";
                                else if (isUltraWide) determinedType = "Back Ultra Wide Camera";
                                else determinedType = "Back Camera (Main)";
                            }
                            break;
                        case CameraCharacteristics.LENS_FACING_EXTERNAL:
                            determinedType = "External Camera";
                            break;
                    }
                }

                if (determinedType != null) {
                    int iconRes = orientation == CameraCharacteristics.LENS_FACING_FRONT ? android.R.drawable.ic_menu_camera : android.R.drawable.ic_menu_gallery;
                    CameraInfo camInfo = new CameraInfo(determinedType, id, determinedType, iconRes, true, physicalCameraIds);
                    detectedCamerasMap.put(determinedType, camInfo);
                }
            } catch (Exception e) {
                // Ignore errors
            }
        }

        List<CameraInfo> availableCameras = new ArrayList<>();
        List<CameraInfo> unavailableCameras = new ArrayList<>();

        predefinedCameraTypes.forEach(typeName -> {
            CameraInfo detectedItem = detectedCamerasMap.get(typeName);
            if (detectedItem != null) {
                if (availableCameras.stream().noneMatch(c -> c.cameraId.equals(detectedItem.cameraId))) {
                    availableCameras.add(detectedItem);
                }
            } else {
                unavailableCameras.add(new CameraInfo(typeName, "", typeName, android.R.drawable.ic_menu_close_clear_cancel, false, Collections.emptyList()));
            }
        });

        availableCameras.sort(Comparator.comparingInt(c -> predefinedCameraTypes.indexOf(c.cameraType)));

        List<CameraInfo> finalList = new ArrayList<>();
        finalList.addAll(availableCameras);

        if (!availableCameras.isEmpty() && !unavailableCameras.isEmpty()) {
            finalList.add(new CameraInfo("Unavailable", "", "Divider", 0, false, Collections.emptyList()));
        }

        finalList.addAll(unavailableCameras);

        return finalList;
    }

    private void updateCameraStatusText() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String[] allCameraIds;
        try {
            allCameraIds = manager.getCameraIdList();
        } catch (CameraAccessException e) {
            allCameraIds = new String[0];
        }

        List<CameraInfo> allCamerasInList = enumerateCamerasDetailed(manager);
        long availableCount = allCamerasInList.stream().filter(c -> c.isAvailable).count();

        String statusText = "Detected: " + allCameraIds.length + " / Available: " + availableCount;
        mCameraStatusTextView.setText(statusText);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        initializeResolutions();

        mTextureView = findViewById(R.id.texture_view);
        // Set the aspect ratio of the view to 1:1 as soon as the activity is created.
        // This ensures the view has the correct dimensions before the camera listener starts.
        mTextureView.setAspectRatio(1, 1);

        mCaptureButton = findViewById(R.id.btn_capture);
        mSwitchCameraButton = findViewById(R.id.btn_switch_camera);
        mCheckCamerasButton = findViewById(R.id.btn_check_cameras);
        mChangeResolutionButton = findViewById(R.id.btn_change_resolution);
        mCameraStatusTextView = findViewById(R.id.camera_status_text);

        mCaptureButton.setOnClickListener(v -> captureImage());
        mSwitchCameraButton.setOnClickListener(v -> switchCamera());

        mCheckCamerasButton.setOnClickListener(v -> {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            List<CameraInfo> cameraList = enumerateCamerasDetailed(manager);

            CameraListAdapter adapter = new CameraListAdapter(this, cameraList, mCameraId);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Available Cameras");
            builder.setAdapter(adapter, (dialog, which) -> {
                CameraInfo selectedCamera = adapter.getItem(which);
                if (selectedCamera != null && selectedCamera.isAvailable && !selectedCamera.cameraId.isEmpty()) {
                    if (!selectedCamera.cameraId.equals(mCameraId)) {
                        selectCameraById(selectedCamera.cameraId);
                    }
                    dialog.dismiss();
                }
            });
            builder.setPositiveButton("CLOSE", (dialog, which) -> dialog.dismiss());

            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(dialogInterface -> {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#2C2C2C")));
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(Color.CYAN);
            });

            dialog.show();
        });


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
                new Size(3024, 3024), new Size(2560, 2560), new Size(2160, 2160),
                new Size(2048, 2048), new Size(1920, 1920), new Size(1440, 1440),
                new Size(1080, 1080), new Size(720, 720)
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
            if (i == 0) resolutionItems[i] = "Maximum (" + resolutionString + ")";
            else resolutionItems[i] = resolutionString;
        }

        int checkedItem = mMasterResolutionList.indexOf(mTargetResolution);
        if (checkedItem < 0) checkedItem = mMasterResolutionList.size() -1;


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select resolution");

        builder.setSingleChoiceItems(resolutionItems, checkedItem, (dialog, which) -> {
            mTargetResolution = mMasterResolutionList.get(which);
            Toast.makeText(this, "Selected: " + mTargetResolution.toString(), Toast.LENGTH_SHORT).show();
        });

        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }


    private void openCamera(int width, int height) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // The aspect ratio is now set in onCreate, so we don't need it here.

            if (mCameraId == null || mCameraId.isEmpty()) {
                for (String cameraId : manager.getCameraIdList()) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == mCurrentLensFacing) {
                        mCameraId = cameraId;
                        break;
                    }
                }
            }

            if (mCameraId == null || mCameraId.isEmpty()) {
                mCameraId = manager.getCameraIdList()[0];
            }


            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            setupHardwareResolutions(map);
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);

            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            runOnUiThread(this::updateCameraStatusText);


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
            runOnUiThread(() -> Toast.makeText(this, "Default Res: " + mTargetResolution.toString(), Toast.LENGTH_SHORT).show());
        } else {
            mChangeResolutionButton.setEnabled(false);
            runOnUiThread(() -> Toast.makeText(this, "No 1:1 resolutions supported.", Toast.LENGTH_LONG).show());
        }
    }

    private Size findBestCaptureSize(Size target) {
        if (mHardwareSupportedResolutions.isEmpty()) return target;

        Size bestSize = mHardwareSupportedResolutions.get(0);
        for (int i = mHardwareSupportedResolutions.size() - 1; i >= 0; i--) {
            Size supported = mHardwareSupportedResolutions.get(i);
            if (supported.getWidth() >= target.getWidth() && supported.getHeight() >= target.getHeight()) {
                return supported;
            }
        }
        return bestSize;
    }

    private int getJpegOrientation() {
        int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
        int rotationCompensation = 0;
        switch (deviceRotation) {
            case Surface.ROTATION_0: rotationCompensation = 0; break;
            case Surface.ROTATION_90: rotationCompensation = 90; break;
            case Surface.ROTATION_180: rotationCompensation = 180; break;
            case Surface.ROTATION_270: rotationCompensation = 270; break;
        }

        int sensorOrientation = 90;
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            sensorOrientation = manager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get sensor orientation", e);
        }

        int jpegOrientation;
        if (mCurrentLensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            jpegOrientation = (sensorOrientation + rotationCompensation + 360) % 360;
        } else {
            jpegOrientation = (sensorOrientation - rotationCompensation + 360) % 360;
        }

        Log.d(TAG, "Calculated JPEG orientation: " + jpegOrientation);
        return jpegOrientation;
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
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation());

            final File file = new File(getExternalFilesDir(null), UUID.randomUUID().toString() + ".jpg");

            ImageReader.OnImageAvailableListener readerListener = reader -> {
                try (Image image = reader.acquireLatestImage()) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    try (FileOutputStream output = new FileOutputStream(file)) {
                        output.write(bytes);
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
        String imageInfo = "File Size: " + fileSizeStr + "\n" + "Format: " + fileFormat + "\n" + "Resolution: " + resolutionStr;

        new AlertDialog.Builder(this)
                .setTitle("Image details")
                .setMessage(imageInfo)
                .setPositiveButton("Crop Image", (dialog, which) -> {
                    android.graphics.Rect idCardCropRect = new android.graphics.Rect(100, 350, 980, 750);
                    CropImage.activity(imageUri)
                            .setGuidelines(CropImageView.Guidelines.ON)
                            .setAspectRatio(85, 54)
                            .setInitialCropWindowRectangle(idCardCropRect)
                            .start(this);
                })
                .setNegativeButton("Cancel", (dialog, which) -> imageFile.delete())
                .setCancelable(false)
                .show();
    }

    private void selectCameraById(String newCameraId) {
        if (newCameraId == null || newCameraId.isEmpty()) return;

        closeCamera();
        mCameraId = newCameraId;

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null) {
                mCurrentLensFacing = facing;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not get characteristics for selected camera.", e);
            mCurrentLensFacing = CameraCharacteristics.LENS_FACING_BACK;
        }

        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }


    private void switchCamera() {
        if (mCurrentLensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            mCurrentLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
        } else {
            mCurrentLensFacing = CameraCharacteristics.LENS_FACING_BACK;
        }
        mCameraId = null;
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
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) { return true; }
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

                        runOnUiThread(() -> {
                            if (mTextureView != null && mTextureView.isAvailable()) {
                                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                            }
                        });

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
        if (null == mTextureView || null == mPreviewSize) return;
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) viewHeight / mPreviewSize.getHeight(), (float) viewWidth / mPreviewSize.getWidth());
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
            } else {
                finish();
            }
        }
    }
}