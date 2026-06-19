from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class DeepOptimizationPage(BasePage):
    # Locators
    OPTIMIZATION_TITLE = (AppiumBy.XPATH, "//*[contains(@text, 'System Optimization') or contains(@text, 'Deep System')]")
    WELLNESS_SCORE = (AppiumBy.ACCESSIBILITY_ID, "WellnessScoreValue")
    
    CACHE_CLEANER_ROW = (AppiumBy.XPATH, "//*[contains(@text, 'Cache Cleaner') or contains(@text, 'Caché')]")
    EMPTY_FOLDER_ROW = (AppiumBy.XPATH, "//*[contains(@text, 'Empty Folders') or contains(@text, 'Carpetas vacías')]")
    SOCIAL_CLEANER_ROW = (AppiumBy.XPATH, "//*[contains(@text, 'Social Media Cleaner') or contains(@text, 'Redes sociales')]")
    
    SYSTEM_CLEAN_ALL_BTN = (AppiumBy.ACCESSIBILITY_ID, "SystemCleanAllButton")

    def get_wellness_score(self) -> str:
        return self.get_text(self.WELLNESS_SCORE)

    def navigate_to_cache_cleaner(self):
        self.click(self.CACHE_CLEANER_ROW)

    def navigate_to_empty_folder_cleaner(self):
        self.click(self.EMPTY_FOLDER_ROW)

    def navigate_to_social_cleaner(self):
        self.click(self.SOCIAL_CLEANER_ROW)

    def click_clean_all(self):
        self.click(self.SYSTEM_CLEAN_ALL_BTN)
