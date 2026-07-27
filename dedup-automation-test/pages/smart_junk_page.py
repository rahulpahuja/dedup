from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class SmartJunkPage(BasePage):
    # Locators
    SCREENSHOTS_CARD = (AppiumBy.ACCESSIBILITY_ID, "ScreenshotsCategoryCard")
    MEMES_CARD = (AppiumBy.ACCESSIBILITY_ID, "MemesCategoryCard")
    RECEIPTS_CARD = (AppiumBy.ACCESSIBILITY_ID, "ReceiptsCategoryCard")
    BLURRY_CARD = (AppiumBy.ACCESSIBILITY_ID, "BlurryCategoryCard")
    
    JUNK_ITEM = (AppiumBy.ACCESSIBILITY_ID, "JunkImageItem")
    BULK_CLEAN_BTN = (AppiumBy.ACCESSIBILITY_ID, "BulkCleanButton")
    
    # Category results list
    CATEGORY_TITLE = (AppiumBy.ACCESSIBILITY_ID, "SmartJunkCategoryTitle")

    def select_screenshots(self):
        self.click(self.SCREENSHOTS_CARD)

    def select_memes(self):
        self.click(self.MEMES_CARD)

    def select_receipts(self):
        self.click(self.RECEIPTS_CARD)

    def select_blurry(self):
        self.click(self.BLURRY_CARD)

    def get_junk_items_count(self) -> int:
        return len(self.driver.find_elements(*self.JUNK_ITEM))

    def trigger_bulk_clean(self):
        self.click(self.BULK_CLEAN_BTN)
