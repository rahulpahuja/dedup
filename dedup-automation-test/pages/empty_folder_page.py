from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class EmptyFolderPage(BasePage):
    # Locators
    EMPTY_FOLDER_TITLE = (AppiumBy.XPATH, "//*[contains(@text, 'Empty Folders') or contains(@text, 'Carpetas vacías')]")
    FOLDER_ROW = (AppiumBy.ACCESSIBILITY_ID, "EmptyFolderRow")
    CLEAN_ALL_FOLDERS_BTN = (AppiumBy.ACCESSIBILITY_ID, "CleanAllFoldersButton")
    NO_FOLDERS_TEXT = (AppiumBy.XPATH, "//*[contains(@text, 'No empty folders found') or contains(@text, 'No se encontraron')]")

    def get_empty_folders_count(self) -> int:
        return len(self.driver.find_elements(*self.FOLDER_ROW))

    def click_clean_all(self):
        self.click(self.CLEAN_ALL_FOLDERS_BTN)

    def is_no_folders_message_visible(self) -> bool:
        return self.is_element_present(self.NO_FOLDERS_TEXT, timeout_type="short")
