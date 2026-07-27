from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from appium.webdriver.common.appiumby import AppiumBy
from config import TestConfig

class BasePage:
    def __init__(self, driver):
        self.driver = driver
        self.wait = WebDriverWait(self.driver, TestConfig.DEFAULT_TIMEOUT)
        self.short_wait = WebDriverWait(self.driver, TestConfig.SHORT_TIMEOUT)
        self.long_wait = WebDriverWait(self.driver, TestConfig.LONG_TIMEOUT)

    def find_element(self, locator, timeout_type="default"):
        """Finds an element using the configured timeout type."""
        wait_obj = self.wait
        if timeout_type == "short":
            wait_obj = self.short_wait
        elif timeout_type == "long":
            wait_obj = self.long_wait
        return wait_obj.until(EC.presence_of_element_located(locator))

    def click(self, locator, timeout_type="default"):
        """Clicks an element after confirming it is clickable."""
        wait_obj = self.wait
        if timeout_type == "short":
            wait_obj = self.short_wait
        elif timeout_type == "long":
            wait_obj = self.long_wait
        element = wait_obj.until(EC.element_to_be_clickable(locator))
        element.click()

    def send_keys(self, locator, text, timeout_type="default"):
        """Clears and inputs text to an element."""
        element = self.find_element(locator, timeout_type)
        element.clear()
        element.send_keys(text)

    def is_element_present(self, locator, timeout_type="short") -> bool:
        """Returns True if the element exists, False otherwise."""
        try:
            self.find_element(locator, timeout_type)
            return True
        except Exception:
            return False

    def get_text(self, locator, timeout_type="default") -> str:
        """Retrieves text attribute from an element."""
        return self.find_element(locator, timeout_type).text

    def swipe_down(self):
        """Simulates scrolling down (swiping up)."""
        size = self.driver.get_window_size()
        start_x = size['width'] / 2
        start_y = size['height'] * 0.8
        end_y = size['height'] * 0.2
        self.driver.swipe(start_x, start_y, start_x, end_y, 800)

    def swipe_up(self):
        """Simulates scrolling up (swiping down)."""
        size = self.driver.get_window_size()
        start_x = size['width'] / 2
        start_y = size['height'] * 0.2
        end_y = size['height'] * 0.8
        self.driver.swipe(start_x, start_y, start_x, end_y, 800)

    def go_back(self):
        """Triggers the hardware back key."""
        self.driver.back()

    def wait_for_loading_to_disappear(self, loading_locator, timeout_type="long"):
        """Waits for a loading indicator to go away."""
        wait_obj = self.long_wait if timeout_type == "long" else self.wait
        wait_obj.until(EC.invisibility_of_element_located(loading_locator))
