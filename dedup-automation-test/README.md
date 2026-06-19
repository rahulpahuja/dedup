# DeDup Automation Test Pipeline (Python Appium Edition)

This folder contains an enterprise-grade, **Page Object Model (POM)** test automation framework built using **Python**, **Appium**, and **pytest**. 

It is designed to automate and test every functional aspect of the DeDup Android application—from low-level permission gates and background scanning workers to database encryption and dynamic localization swaps.

For a detailed breakdown of test scenarios, step-by-step instructions, and assertions for each app screen, check the [Screen-by-Screen Automation Test Cases](file:///Users/rahulpahuja/AndroidStudioProjects/dedup/dedup-automation-test/SCREEN_TEST_CASES.md) document.

---

## 🏗️ Framework Architecture

The framework is structured as follows:

```
dedup-automation-test/
├── requirements.txt         # Core dependencies (pytest, Appium-Python-Client)
├── config.py                # Device capabilities, package/activity metadata, and timeouts
├── conftest.py              # Driver lifecycle management and ADB command injection helpers
├── pages/                   # Page Object Model elements and actions
│   ├── base_page.py         # Base interactions (wait, swipe, click, input, alerts)
│   ├── splash_page.py       # Splash screen & Root Detection warning handlers
│   ├── login_page.py        # Authentication buttons & Guest Mode fallback
│   ├── dashboard_page.py    # Main screen counters & Navigation Drawer linkages
│   ├── scanner_page.py      # Scanners, permissions explanations, system dialogs
│   ├── deduplication_page.py# Results clustering, manual checks, deletion confirmations
│   └── settings_page.py     # Theme swaps, translation configs, and similarity sliders
└── tests/                   # Test scenarios mapped by screen/feature
    ├── test_splash.py       # Tests root detection alerts and boot transitions
    ├── test_login.py        # Tests guest mode bypass and Google login triggers
    ├── test_dashboard.py    # Tests circular gauges, card listings, and lateral drawers
    ├── test_deduplication.py# Mocks and scans duplicate files, checks deletion confirmations
    └── test_settings.py     # Checks instant translation swaps and slider persistence
```

---

## 🛠️ Setup Instructions

### 1. Prerequisites
Ensure you have the following installed on your host machine:
* **Python 3.8+**
* **Android SDK** (with `platform-tools` added to your system `PATH`)
* **Node.js** (to install and manage the Appium server)
* **Appium Server & UIAutomator2 Driver**:
  ```bash
  npm install -g appium
  appium driver install uiautomator2
  ```

### 2. Installation
Navigate to this directory and install the required Python packages:
```bash
pip install -r requirements.txt
```

### 3. Start Appium Server
Start the Appium server locally:
```bash
appium
```

### 4. Running the Tests
To run all test cases and generate an interactive HTML report:
```bash
pytest --html=reports/test_report.html --self-contained-html
```

To run a specific test file (e.g., Settings Page tests):
```bash
pytest tests/test_settings.py -v
```

### 📦 Running with Android App Bundles (.aab)
Android devices cannot install `.aab` files directly. To test an App Bundle, you have two options:

#### Option A: Let Appium Handle it Dynamically (Recommended)
Appium's `UiAutomator2` driver can install `.aab` files on-the-fly, but it requires Google's **`bundletool`** to be available in your system path:
1. Download the latest `bundletool-all.jar` from the [Google bundletool GitHub releases](https://github.com/google/bundletool/releases).
2. Rename the jar to `bundletool.jar` and save it to a local folder (e.g., `/usr/local/bin/` or `C:\bundletool\`).
3. Add a wrapper script or alias named `bundletool` to your system PATH so Appium can invoke it as a command:
   * **macOS/Linux**: Create a file named `bundletool` in `/usr/local/bin/` containing:
     ```bash
     #!/bin/bash
     java -jar /usr/local/bin/bundletool.jar "$@"
     ```
     Then make it executable: `chmod +x /usr/local/bin/bundletool`
4. Update `"app"` capability in `config.py` to point directly to your `.aab` file:
   ```python
   "app": "/absolute/path/to/your/app-release.aab"
   ```

#### Option B: Pre-convert to APK Set (.apks) manually
If you do not want to configure Appium's PATH, you can convert the `.aab` to `.apks` and install it manually before running the tests:
1. **Generate the APK Set** (extracts universal APKs for testing):
   ```bash
   java -jar bundletool.jar build-apks \
     --bundle=app-release.aab \
     --output=app.apks \
     --mode=universal
   ```
2. **Install the APK Set** on your connected device/emulator:
   ```bash
   java -jar bundletool.jar install-apks --apks=app.apks
   ```
3. Set `noReset: True` in `config.py` desired capabilities so Appium does not overwrite the manually installed application.

---


## 🔬 Senior Principal Fellow Technical Design Details

As a mobile architecture lead, several critical design patterns have been built into this framework to handle the challenges of testing local-first Android apps:

### 1. Jetpack Compose Accessibility Locator Strategy
Since DeDup is built with Jetpack Compose, standard Android view resource IDs do not exist. To avoid unstable XPath selectors:
* We configure UI elements with `Modifier.semantics { testTag = "TagName" }` or `Modifier.clearAndSetSemantics { contentDescription = "TagName" }` in Kotlin.
* In Appium, we locate these directly using `AppiumBy.ACCESSIBILITY_ID` for reliability and speed:
  ```python
  SPLASH_LOGO = (AppiumBy.ACCESSIBILITY_ID, "SplashLogo")
  ```

### 2. File & State Mocking via ADB Fixture (`conftest.py`)
To test the core duplicate engine, we do not rely on manual user interactions to download photos. The `adb` fixture simulates this:
1. Pushes two identical test files directly into the emulator's public directory:
   ```bash
   adb shell 'echo "test_bytes" > /sdcard/Download/file1.jpg'
   adb shell 'echo "test_bytes" > /sdcard/Download/file2.jpg'
   ```
2. Broadcasts a `MEDIA_SCANNER_SCAN_FILE` intent to force the Android MediaStore database to catalog the mock files:
   ```bash
   adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/file1.jpg
   ```
3. Performs the app scan, deletes the duplicates, and automatically cleans up the mocked directories in the test `finally` block.

### 3. Root Detection Alert Verification
We verify security mitigations by simulating a rooted device prior to application startup:
* The test script uses ADB to write a dummy `su` executable path inside the standard paths scanned by the app (e.g. `/system/xbin/su` or `/system/bin/su`).
* We launch the app, assert the `Root Warning` screen intercepts the user, test both "Proceed" and "Exit" actions, and then clean up the binary path.

### 4. Dynamic Localization & Configuration Assertions
When the user switches languages (e.g., English to Spanish), standard tests hardcode specific text strings. This framework uses flexible checks:
* We verify text modifications (e.g., header title changing from "Settings" to "Configuración") and verify that changes apply *instantly* without crashing the app shell.

### 5. Automated Failure Diagnostics
When a test fails:
* The custom `pytest_runtest_makereport` hook captures a device screenshot from the active Appium session and writes it to the `screenshots/` directory, naming it after the failing test case for easy debug analysis.

---

## 📁 Screen-by-Screen Test Cases

For a detailed breakdown of test scenarios, step-by-step instructions, and assertions for each app screen, check the [Screen-by-Screen Automation Test Cases](file:///Users/rahulpahuja/AndroidStudioProjects/dedup/dedup-automation-test/SCREEN_TEST_CASES.md) document.

---

## 📊 Report Generation Capabilities

This framework features double-layered, automated report generation pipelines which run instantly upon test suite completion:

1. **Self-Contained HTML Reports (`reports/test_report.html`)**:
   * Generates interactive execution pages containing timestamps, metadata, and tables.
   * **Base64 Inline Screenshots**: When a test fails, Appium extracts the screen stream, encodes it as a Base64 string, and attaches it directly within the HTML markup. The resulting report is completely self-contained—meaning you can share the single `.html` file via Slack, email, or drive, and it will load failure screenshots offline without external link dependencies.

2. **Markdown Execution Dashboards (`reports/LATEST_RUN_SUMMARY.md`)**:
   * Pytest's lifecycle hooks compile metrics (Total, Passed, Failed, Skipped, Suite Duration) dynamically on execution finish.
   * Outputs clean Markdown tables matching test logs to their statuses and durations, linking failed scripts to their saved screenshot attachments.
   * Includes a **Failure Analysis** section displaying exact tracebacks and exceptions for diagnostic debugging.

