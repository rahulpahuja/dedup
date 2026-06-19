from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class ContactsPage(BasePage):
    # Locators
    CONTACT_ROW = (AppiumBy.ACCESSIBILITY_ID, "ContactItemRow")
    MERGE_PREVIEW_BTN = (AppiumBy.ACCESSIBILITY_ID, "MergePreviewButton")
    
    # Merge preview dialog
    PREVIEW_DIALOG = (AppiumBy.ACCESSIBILITY_ID, "MergePreviewDialog")
    CONFIRM_MERGE_BTN = (AppiumBy.ACCESSIBILITY_ID, "ConfirmMergeButton")
    
    SUCCESS_TOAST = (AppiumBy.XPATH, "//*[contains(@text, 'Contacts merged') or contains(@text, 'fusionados')]")

    def get_duplicate_contacts_count(self) -> int:
        return len(self.driver.find_elements(*self.CONTACT_ROW))

    def preview_merge(self):
        self.click(self.MERGE_PREVIEW_BTN)

    def is_preview_visible(self) -> bool:
        return self.is_element_present(self.PREVIEW_DIALOG, timeout_type="default")

    def confirm_merge(self):
        self.click(self.CONFIRM_MERGE_BTN)

    def is_merge_success_toast_visible(self) -> bool:
        return self.is_element_present(self.SUCCESS_TOAST, timeout_type="default")
