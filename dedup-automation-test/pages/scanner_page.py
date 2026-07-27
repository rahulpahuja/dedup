from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class ScannerPage(BasePage):
    # Locators
    SCAN_BTN = (AppiumBy.ACCESSIBILITY_ID, "ScanButton")
    CANCEL_SCAN_BTN = (AppiumBy.ACCESSIBILITY_ID, "CancelScanButton")
    SCAN_PROGRESS = (AppiumBy.ACCESSIBILITY_ID, "ScanProgressBar")
    SCANNING_STATUS_TEXT = (AppiumBy.ACCESSIBILITY_ID, "ScanningStatusText")

    # Permissions Gate Locators
    PERMISSION_EXPLANATION_DIALOG = (AppiumBy.ACCESSIBILITY_ID, "PermissionExplanationDialog")
    EXPLANATION_AGREE_BTN = (AppiumBy.XPATH, "//*[contains(@text, 'GRANT') or contains(@text, 'PERMITIR') or contains(@text, 'Agree')]")
    
    # System Permissions Dialog (UI Automator)
    SYSTEM_ALLOW_BUTTON = (AppiumBy.XPATH, "//*[contains(@resource-id, 'permission_allow_button') or contains(@text, 'Allow') or contains(@text, 'Permitir')]")

    def click_scan(self):
        self.click(self.SCAN_BTN)

    def is_permission_gate_visible(self) -> bool:
        return self.is_element_present(self.PERMISSION_EXPLANATION_DIALOG, timeout_type="short")

    def accept_permission_gate(self):
        if self.is_permission_gate_visible():
            self.click(self.EXPLANATION_AGREE_BTN)
            
    def click_allow_on_system_dialog(self):
        # We wait for the Android system permission request popup and allow it
        self.click(self.SYSTEM_ALLOW_BUTTON, timeout_type="short")

    def cancel_scan(self):
        self.click(self.CANCEL_SCAN_BTN)

    def is_scanning(self) -> bool:
        return self.is_element_present(self.SCAN_PROGRESS, timeout_type="short")

    def wait_for_scan_completion(self):
        self.wait_for_loading_to_disappear(self.SCAN_PROGRESS, timeout_type="long")
