import os

class TestConfig:
    # Appium Server Config
    APPIUM_SERVER_URL = "http://127.0.0.1:4723"

    # Android Desired Capabilities
    CAPABILITIES = {
        "platformName": "Android",
        "automationName": "UiAutomator2",
        "deviceName": "Android_Emulator",
        "appPackage": "com.rp.dedup",
        "appActivity": "com.rp.dedup.MainActivity",
        "noReset": False,
        "fullReset": False,
        "autoGrantPermissions": False,  # We want to test our permission gates!
        "newCommandTimeout": 300,
        "isHeadless": False
    }

    # Timeouts (in seconds)
    SHORT_TIMEOUT = 5
    DEFAULT_TIMEOUT = 15
    LONG_TIMEOUT = 45

    # Path to App APK (if running installer tests)
    APK_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "../app/build/outputs/apk/debug/app-debug.apk"))
