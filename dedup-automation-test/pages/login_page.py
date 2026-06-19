from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class LoginPage(BasePage):
    # Locators
    GOOGLE_SIGN_IN_BTN = (AppiumBy.ACCESSIBILITY_ID, "GoogleSignInButton")
    GUEST_MODE_BTN = (AppiumBy.XPATH, "//*[contains(@text, 'Continue as Guest') or contains(@text, 'Invitado')]")
    
    def click_google_sign_in(self):
        self.click(self.GOOGLE_SIGN_IN_BTN)
        
    def click_continue_as_guest(self):
        self.click(self.GUEST_MODE_BTN)

    def is_login_screen_visible(self) -> bool:
        return self.is_element_present(self.GUEST_MODE_BTN, timeout_type="default")
