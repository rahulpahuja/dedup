from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class SplashPage(BasePage):
    # Locators
    # Compose test tags typically compile down to content-desc or resource-id
    SPLASH_LOGO = (AppiumBy.ACCESSIBILITY_ID, "SplashLogo")
    ROOT_WARNING_DIALOG = (AppiumBy.XPATH, "//*[contains(@text, 'Root Detection Warning') or contains(@resource-id, 'RootWarning')]")
    ROOT_PROCEED_BUTTON = (AppiumBy.XPATH, "//*[contains(@text, 'PROCEED') or contains(@text, 'Aceptar')]")
    ROOT_EXIT_BUTTON = (AppiumBy.XPATH, "//*[contains(@text, 'EXIT') or contains(@text, 'Salir')]")

    def is_splash_visible(self) -> bool:
        return self.is_element_present(self.SPLASH_LOGO, timeout_type="short")

    def wait_for_root_warning(self) -> bool:
        return self.is_element_present(self.ROOT_WARNING_DIALOG, timeout_type="short")

    def accept_root_warning(self):
        if self.wait_for_root_warning():
            self.click(self.ROOT_PROCEED_BUTTON)

    def exit_due_to_root(self):
        if self.wait_for_root_warning():
            self.click(self.ROOT_EXIT_BUTTON)
