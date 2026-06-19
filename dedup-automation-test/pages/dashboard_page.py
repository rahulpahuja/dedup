from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class DashboardPage(BasePage):
    # Locators
    DRAWER_TOGGLE = (AppiumBy.ACCESSIBILITY_ID, "Navigation drawer")
    STORAGE_USED_TXT = (AppiumBy.ACCESSIBILITY_ID, "StorageUsedText")
    STORAGE_FREE_TXT = (AppiumBy.ACCESSIBILITY_ID, "StorageFreeText")
    
    # Category Tiles
    IMAGE_CATEGORY = (AppiumBy.XPATH, "//*[contains(@content-desc, 'Images') or contains(@text, 'Images')]")
    VIDEO_CATEGORY = (AppiumBy.XPATH, "//*[contains(@content-desc, 'Videos') or contains(@text, 'Videos')]")
    AUDIO_CATEGORY = (AppiumBy.XPATH, "//*[contains(@content-desc, 'Audio') or contains(@text, 'Audio')]")
    DOCS_CATEGORY = (AppiumBy.XPATH, "//*[contains(@content-desc, 'Documents') or contains(@text, 'Documents')]")
    APK_CATEGORY = (AppiumBy.XPATH, "//*[contains(@content-desc, 'APKs') or contains(@text, 'APKs')]")
    
    # Quick Navigation Buttons
    DEEP_OPTIMIZATION_BTN = (AppiumBy.XPATH, "//*[contains(@text, 'Deep System Optimization') or contains(@text, 'Optimización')]")
    BIG_FILES_BTN = (AppiumBy.XPATH, "//*[contains(@text, 'Big File Map') or contains(@text, 'Archivos Grandes')]")

    # App Drawer items
    DRAWER_HEADER = (AppiumBy.ACCESSIBILITY_ID, "DrawerHeader")
    DRAWER_SETTINGS_LINK = (AppiumBy.XPATH, "//*[contains(@text, 'Settings') or contains(@text, 'Configuración')]")
    DRAWER_HISTORY_LINK = (AppiumBy.XPATH, "//*[contains(@text, 'Scan History') or contains(@text, 'Historial')]")
    DRAWER_LOGS_LINK = (AppiumBy.XPATH, "//*[contains(@text, 'Activity Log') or contains(@text, 'Actividad')]")

    def is_dashboard_visible(self) -> bool:
        return self.is_element_present(self.STORAGE_USED_TXT, timeout_type="default")

    def get_used_storage(self) -> str:
        return self.get_text(self.STORAGE_USED_TXT)

    def get_free_storage(self) -> str:
        return self.get_text(self.STORAGE_FREE_TXT)

    def navigate_to_images(self):
        self.click(self.IMAGE_CATEGORY)

    def navigate_to_videos(self):
        self.click(self.VIDEO_CATEGORY)

    def navigate_to_deep_optimization(self):
        self.click(self.DEEP_OPTIMIZATION_BTN)

    def navigate_to_big_files(self):
        self.click(self.BIG_FILES_BTN)

    def open_drawer(self):
        self.click(self.DRAWER_TOGGLE)

    def navigate_via_drawer(self, drawer_link_locator):
        self.open_drawer()
        self.click(drawer_link_locator)
