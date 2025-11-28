# OCR  ​  [<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="60">](https://f-droid.org/packages/io.github.subhamtyagi.ocr/)

<a href="https://hosted.weblate.org/projects/android-ocr" alt="Translate on Weblate"><img src="https://img.shields.io/badge/Translation-Weblate-red" ></a>

## 📥 Download APK

**[Download Latest APK](https://drive.google.com/file/d/1ITmaLOuYINQgJ5SaMQ5jStLcgmFQH9Cl/view?usp=sharing)**

---

An OCR app that can recognize texts on images using advanced optical character recognition technology.

This App is based on <a href="https://github.com/tesseract-ocr/tesseract/blob/master/README.md">Tesseract 5</a> and is the first app based on Tesseract 5. This app is made possible by the library [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android).

---

## 📋 Table of Contents

- [Features](#-features)
- [How OCR Works](#-how-ocr-works)
- [Project Structure](#-project-structure)
- [Installation](#-installation)
- [Usage](#-usage)
- [Testing](#-testing)
- [Required Permissions](#-required-permissions)
- [Screenshots](#-screenshots)
- [Contributors](#-code-contributors)
- [License](#-licenses-of-various-libraries)

---

## 🚀 Features

* **Extract Text From Images** - Capture or select images and extract text automatically
* **Copy data to Clipboard** - Easily copy extracted text for use in other apps
* **Select any part of Text** - Choose specific portions of recognized text
* **Multi-language Processing** - Process multiple languages in a single image (configurable in settings)
* **Share Menu Integration** - Process images directly from gallery via share menu
* **Latest Training Data** - Based on the latest [Training Data](https://github.com/tesseract-ocr/tessdoc/blob/master/Data-Files.md)
* **120+ Languages Supported** - Recognize [120+ languages](https://tesseract-ocr.github.io/tessdoc/Data-Files)
* **3 Data Types** - Choose between 'Best', 'Fast', or 'Standard' for accuracy vs speed trade-offs ([more info](https://github.com/tesseract-ocr/tessdoc/blob/master/Data-Files.md))
* **Math/Equation Detection** - Recognize mathematical expressions and equations
* **Camera Integration** - Built-in camera with resolution control and image cropping
* **Image Pre-processing** - Automatic contrast normalization, unsharp masking, and deskewing

### Translate this app on [Weblate](https://hosted.weblate.org/projects/android-ocr).

---

## 🔍 How OCR Works

### Overview

**Optical Character Recognition (OCR)** is the technology that converts different types of documents—such as scanned paper documents, PDF files, or images captured by a camera—into editable and searchable data.

### Tesseract Engine

This app uses **Tesseract 5**, one of the most accurate open-source OCR engines available:

1. **Image Input**: The user provides an image via camera or gallery
2. **Pre-processing**: The image is enhanced using:
   - **Contrast Normalization** - Improves text visibility
   - **Unsharp Masking** - Sharpens text edges
   - **Otsu Thresholding** - Converts to binary image for better recognition
   - **Deskewing** - Automatically corrects rotated text
3. **Text Recognition**: Tesseract analyzes the processed image and extracts text
4. **Output**: Recognized text is displayed with confidence score

### Training Data Types

| Type | Accuracy | Speed | Use Case |
|------|----------|-------|----------|
| **Best** | Highest | Slowest | Documents requiring high accuracy |
| **Standard** | Medium | Medium | General purpose usage |
| **Fast** | Lower | Fastest | Quick scans, low-power devices |

---

## 📁 Project Structure

```
android-ocr/
├── app/                              # Main application module
│   ├── build.gradle                  # App-level build configuration
│   ├── proguard-rules.pro            # ProGuard rules for release builds
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml   # App manifest with permissions
│           ├── java/io/github/subhamtyagi/ocr/
│           │   ├── App.java                    # Application class (Dynamic Colors)
│           │   ├── MainActivity.java           # Main OCR activity
│           │   ├── CameraActivity.java         # Camera capture activity
│           │   ├── SettingsActivity.java       # App settings
│           │   ├── BottomSheetResultsFragment.java  # OCR results display
│           │   ├── AutoFitTextureView.java     # Camera preview view
│           │   ├── ocr/
│           │   │   └── ImageTextReader.java    # Core OCR engine wrapper
│           │   └── utils/
│           │       ├── Constants.java          # App constants & URLs
│           │       ├── Language.java           # Language model
│           │       ├── SpUtil.java             # SharedPreferences utility
│           │       └── Utils.java              # Image processing utilities
│           └── res/                  # Resources (layouts, strings, drawables)
│               ├── layout/           # XML layouts
│               ├── values/           # Default strings, styles, colors
│               ├── values-*/         # Localized strings (40+ languages)
│               └── mipmap-*/         # App icons
├── cropper/                          # Image cropping library module
│   └── src/main/
│       ├── java/com/theartofdev/    # Cropper implementation
│       └── res/                      # Cropper resources
├── gradle/                           # Gradle wrapper
├── fastlane/                         # App store metadata & screenshots
├── build.gradle                      # Project-level build configuration
├── settings.gradle                   # Project settings
└── parameters.md                     # Tesseract parameters documentation
```

### Key Components

| Component | Description |
|-----------|-------------|
| `MainActivity` | Main screen with image selection, OCR processing, and result display |
| `CameraActivity` | Custom camera interface with resolution options and cropping |
| `ImageTextReader` | Wrapper for Tesseract API, handles text extraction |
| `Utils` | Image pre-processing (contrast, binarization, deskewing) |
| `BottomSheetResultsFragment` | Displays OCR results in a bottom sheet |

---

## 🛠 Installation

### Prerequisites

- **Android Studio** Arctic Fox (2020.3.1) or later
- **JDK 8** or higher
- **Android SDK** with:
  - Compile SDK: 35
  - Minimum SDK: 21 (Android 5.0 Lollipop)
  - Target SDK: 33

### Clone the Repository

```bash
git clone https://github.com/Kritchanaxt/android-ocr.git
cd android-ocr
```

### Build with Android Studio

1. **Open Project**
   - Launch Android Studio
   - Select `File > Open`
   - Navigate to the cloned `android-ocr` folder
   - Click `OK`

2. **Sync Gradle**
   - Android Studio will automatically sync Gradle
   - Wait for the sync to complete (check bottom progress bar)

3. **Build the Project**
   ```
   Build > Make Project (Ctrl+F9 / Cmd+F9)
   ```

4. **Run on Device/Emulator**
   ```
   Run > Run 'app' (Shift+F10 / Ctrl+R)
   ```

### Build with Command Line

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

### Output APK Locations

- Debug: `app/build/outputs/apk/debug/`
- Release: `app/build/outputs/apk/release/`

### ABI-specific APKs

The project generates separate APKs for different CPU architectures:
- `x86` - Intel emulators
- `x86_64` - 64-bit Intel emulators
- `armeabi-v7a` - 32-bit ARM devices
- `arm64-v8a` - 64-bit ARM devices (most modern phones)
- `universal` - All architectures (larger file size)

---

## 📱 Usage

### First Launch

1. **Select Language**
   - On first launch, select your target OCR language(s)
   - The app will download the required training data (~1-15MB per language)

2. **Choose Data Type** (Optional)
   - Go to Settings to select between Best/Standard/Fast
   - Default is "Fast" for quick processing

### Extracting Text from Images

#### Method 1: From Gallery
1. Tap the **FAB (Floating Action Button)** on the home screen
2. Select an image from your gallery
3. Crop the image to focus on the text area
4. Wait for OCR processing
5. View results in the bottom sheet

#### Method 2: From Camera
1. Tap the **Camera button**
2. Point at the text you want to capture
3. Adjust resolution if needed (tap resolution button)
4. Capture the image
5. Crop and process

#### Method 3: Share from Other Apps
1. Open any image in Gallery or another app
2. Tap **Share > OCR**
3. Crop and process

### Settings

Access settings via the menu (⋮) button:

| Setting | Description |
|---------|-------------|
| **Language** | Select OCR language(s) - supports multi-language |
| **Training Data Type** | Best/Standard/Fast accuracy modes |
| **Page Segmentation Mode** | Auto, Single block, Single line, etc. |
| **Pre-processing** | Enable/disable contrast, sharpening, deskewing |
| **Persist Data** | Keep last processed image and text |

### Tips for Better Results

- ✅ Use good lighting when capturing images
- ✅ Keep the document flat and avoid shadows
- ✅ Crop to include only the text area
- ✅ Use "Best" data type for complex fonts
- ✅ Enable pre-processing for poor quality images

---

## 🧪 Testing

### Unit Tests

```bash
# Run all unit tests
./gradlew test

# Run tests for specific module
./gradlew :app:test
```

### Instrumentation Tests

```bash
# Run on connected device/emulator
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.subhamtyagi.ocr.ExampleInstrumentedTest
```

### Manual Testing Checklist

- [ ] Image selection from gallery
- [ ] Camera capture functionality
- [ ] Multi-language OCR processing
- [ ] Text copying to clipboard
- [ ] Settings persistence
- [ ] Language data download
- [ ] Different image formats (JPEG, PNG)
- [ ] Rotated/skewed text recognition
- [ ] Share intent handling

### Debug Build

The debug build includes:
- Application ID suffix: `.dev`
- App name: "OCR Dev"
- No ProGuard obfuscation
- Debugging enabled

---

## 🔐 Required Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Downloading OCR training data |
| `ACCESS_NETWORK_STATE` | Check network connectivity before download |
| `CAMERA` | Capture images for OCR processing |

---

## 📸 Screenshots

| HOME | SETTING | RESULTS |LANGUAGE|
|:-:|:-:|:-:|:-:|
| ![HOME](fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg?raw=true "home") | ![RESULT](fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg?raw=true "RESULT") | ![SETTINGS](fastlane/metadata/android/en-US/images/phoneScreenshots/7.jpg?raw=true "SETTINGS") | ![LANGUAGES](fastlane/metadata/android/en-US/images/phoneScreenshots/8.jpg?raw=true "LANGUAGES") |

---

## 👥 Code Contributors

* **Shubham Tyagi** - Original author
* **[Hannes Gehrold](https://github.com/h4n23s)** - New UI design
* **[urlordjames](https://github.com/urlordjames)** - Contributions

---

## 🎨 Icon

*  App icon is designed by [Nucleus-ffm](https://github.com/nucleus-ffm)
   - Mastodon: [Nucleus](https://social.tchncs.de/@Nucleus) 

* Old App icon was conceptualized by [mondstern](https://mastodon.technology/@mondstern)
   - Mastodon: [mondstern](https://mastodon.technology/@mondstern)
   - Website: [here](https://www.moooon.de/)

---

## 📓 Licenses of various Libraries

| Library | License |
|---------|---------|
| [Tesseract & Tesseract Data](https://github.com/tesseract-ocr/tesseract/blob/master/LICENSE) | Apache 2.0 |
| [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android/blob/master/LICENSE) | Apache 2.0 |
| [ImageCropper](https://github.com/ArthurHub/Android-Image-Cropper/blob/master/LICENSE.txt) | Apache 2.0 |
| [SpinnerDialog](https://github.com/MdFarhanRaja/SearchableSpinner/blob/master/LICENSE) | Apache 2.0 |

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

