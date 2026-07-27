from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class BigFileMapPage(BasePage):
    # Locators
    TREEMAP_NODE = (AppiumBy.ACCESSIBILITY_ID, "TreemapNode")
    UP_NAVIGATION_BTN = (AppiumBy.ACCESSIBILITY_ID, "NavigateUpFolderButton")
    CURRENT_PATH_TXT = (AppiumBy.ACCESSIBILITY_ID, "CurrentTreemapPath")

    def click_node_by_index(self, index: int):
        nodes = self.driver.find_elements(*self.TREEMAP_NODE)
        if len(nodes) > index:
            nodes[index].click()

    def get_current_path(self) -> str:
        return self.get_text(self.CURRENT_PATH_TXT)

    def navigate_up(self):
        self.click(self.UP_NAVIGATION_BTN)

    def is_treemap_visible(self) -> bool:
        return self.is_element_present(self.TREEMAP_NODE, timeout_type="default")
