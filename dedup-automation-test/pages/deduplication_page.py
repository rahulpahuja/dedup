from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class DeduplicationPage(BasePage):
    # Locators
    DEDUPLICATION_TITLE = (AppiumBy.XPATH, "//*[contains(@text, 'Deduplication Results') or contains(@text, 'Duplicate Clusters')]")
    CLUSTER_ITEM = (AppiumBy.ACCESSIBILITY_ID, "ClusterItem")
    BEST_SHOT_LABEL = (AppiumBy.XPATH, "//*[contains(@text, 'Best Shot') or contains(@text, 'Recomendado')]")
    DUPLICATE_CHECKBOX = (AppiumBy.ACCESSIBILITY_ID, "DuplicateCheckbox")
    DELETE_SELECTED_BTN = (AppiumBy.ACCESSIBILITY_ID, "DeleteSelectedButton")
    
    # Deletion Confirmation Dialog
    CONFIRMATION_DIALOG = (AppiumBy.ACCESSIBILITY_ID, "DeletionConfirmationDialog")
    CONFIRM_DELETE_BTN = (AppiumBy.XPATH, "//*[contains(@text, 'DELETE') or contains(@text, 'ELIMINAR') or contains(@text, 'Confirm')]")
    CANCEL_DELETE_BTN = (AppiumBy.XPATH, "//*[contains(@text, 'CANCEL') or contains(@text, 'CANCELAR')]")

    def is_results_screen_visible(self) -> bool:
        return self.is_element_present(self.DEDUPLICATION_TITLE, timeout_type="default")

    def get_duplicate_checkboxes(self) -> list:
        # Returns all visible checkbox elements
        return self.driver.find_elements(*self.DUPLICATE_CHECKBOX)

    def is_best_shot_recommended(self) -> bool:
        return self.is_element_present(self.BEST_SHOT_LABEL, timeout_type="short")

    def click_delete_selected(self):
        self.click(self.DELETE_SELECTED_BTN)

    def is_confirmation_dialog_visible(self) -> bool:
        return self.is_element_present(self.CONFIRMATION_DIALOG, timeout_type="short")

    def confirm_deletion(self):
        if self.is_confirmation_dialog_visible():
            self.click(self.CONFIRM_DELETE_BTN)

    def cancel_deletion(self):
        if self.is_confirmation_dialog_visible():
            self.click(self.CANCEL_DELETE_BTN)
