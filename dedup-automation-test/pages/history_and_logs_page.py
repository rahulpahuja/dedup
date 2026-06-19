from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class HistoryAndLogsPage(BasePage):
    # Locators
    HISTORY_ITEM = (AppiumBy.ACCESSIBILITY_ID, "ScanHistoryItem")
    AUDIT_LOG_ITEM = (AppiumBy.ACCESSIBILITY_ID, "ActivityLogItem")
    CLEAR_HISTORY_BTN = (AppiumBy.ACCESSIBILITY_ID, "ClearHistoryButton")
    
    # Dialogs
    CONFIRM_CLEAR_BTN = (AppiumBy.XPATH, "//*[contains(@text, 'CLEAR') or contains(@text, 'CONFIRM')]")

    def get_history_items_count(self) -> int:
        return len(self.driver.find_elements(*self.HISTORY_ITEM))

    def get_audit_log_items_count(self) -> int:
        return len(self.driver.find_elements(*self.AUDIT_LOG_ITEM))

    def clear_scan_history(self):
        if self.is_element_present(self.CLEAR_HISTORY_BTN, timeout_type="short"):
            self.click(self.CLEAR_HISTORY_BTN)
            self.click(self.CONFIRM_CLEAR_BTN)
