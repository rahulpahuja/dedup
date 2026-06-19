from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class WhatsAppCleanerPage(BasePage):
    # Locators
    TAB_RECEIVED = (AppiumBy.XPATH, "//*[contains(@text, 'Received') or contains(@text, 'Recibidos')]")
    TAB_SENT = (AppiumBy.XPATH, "//*[contains(@text, 'Sent') or contains(@text, 'Enviados')]")
    TAB_STATUSES = (AppiumBy.XPATH, "//*[contains(@text, 'Statuses') or contains(@text, 'Estados')]")
    
    WHATSAPP_FILE_ROW = (AppiumBy.ACCESSIBILITY_ID, "WhatsAppFileRow")
    SELECT_ALL_CHECKBOX = (AppiumBy.ACCESSIBILITY_ID, "SelectAllWhatsAppCheckbox")
    DELETE_WHATSAPP_BTN = (AppiumBy.ACCESSIBILITY_ID, "DeleteWhatsAppSelectedButton")

    def switch_to_sent_tab(self):
        self.click(self.TAB_SENT)

    def switch_to_received_tab(self):
        self.click(self.TAB_RECEIVED)

    def switch_to_statuses_tab(self):
        self.click(self.TAB_STATUSES)

    def select_all_files(self):
        self.click(self.SELECT_ALL_CHECKBOX)

    def get_files_list_count(self) -> int:
        return len(self.driver.find_elements(*self.WHATSAPP_FILE_ROW))

    def click_delete_selected(self):
        self.click(self.DELETE_WHATSAPP_BTN)
