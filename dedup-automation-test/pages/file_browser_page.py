from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class FileBrowserPage(BasePage):
    # Locators
    FILE_ROW = (AppiumBy.ACCESSIBILITY_ID, "FileBrowserRow")
    SORT_DROPDOWN = (AppiumBy.ACCESSIBILITY_ID, "SortDropdownSelector")
    SORT_BY_SIZE_OPT = (AppiumBy.XPATH, "//*[contains(@text, 'Size') or contains(@text, 'Tamaño')]")
    SORT_BY_DATE_OPT = (AppiumBy.XPATH, "//*[contains(@text, 'Date') or contains(@text, 'Fecha')]")
    
    FILE_LONG_PRESS_MENU = (AppiumBy.ACCESSIBILITY_ID, "FileActionMenu")
    MENU_SHARE_BTN = (AppiumBy.XPATH, "//*[contains(@text, 'Share') or contains(@text, 'Compartir')]")
    MENU_DELETE_BTN = (AppiumBy.XPATH, "//*[contains(@text, 'Delete') or contains(@text, 'Eliminar')]")

    def click_file_by_index(self, index: int):
        files = self.driver.find_elements(*self.FILE_ROW)
        if len(files) > index:
            files[index].click()

    def long_press_file_by_index(self, index: int):
        files = self.driver.find_elements(*self.FILE_ROW)
        if len(files) > index:
            # Appium actions to long press
            from appium.webdriver.common.touch_action import TouchAction
            action = TouchAction(self.driver)
            action.long_press(files[index]).release().perform()

    def select_sort_by_size(self):
        self.click(self.SORT_DROPDOWN)
        self.click(self.SORT_BY_SIZE_OPT)

    def trigger_share_action(self, index: int):
        self.long_press_file_by_index(index)
        self.click(self.MENU_SHARE_BTN)
        
    def trigger_delete_action(self, index: int):
        self.long_press_file_by_index(index)
        self.click(self.MENU_DELETE_BTN)
