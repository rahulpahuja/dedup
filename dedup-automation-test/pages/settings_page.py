from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class SettingsPage(BasePage):
    # Locators
    SETTINGS_TITLE = (AppiumBy.XPATH, "//*[contains(@text, 'Settings') or contains(@text, 'Configuración')]")
    
    # Theme settings
    THEME_OPTION = (AppiumBy.ACCESSIBILITY_ID, "ThemeSettingsOption")
    DARK_THEME_RADIO = (AppiumBy.XPATH, "//*[contains(@text, 'Dark') or contains(@text, 'Oscuro')]")
    LIGHT_THEME_RADIO = (AppiumBy.XPATH, "//*[contains(@text, 'Light') or contains(@text, 'Claro')]")
    SOLAR_THEME_RADIO = (AppiumBy.XPATH, "//*[contains(@text, 'Solar') or contains(@text, 'Solar')]")
    
    # Language settings
    LANGUAGE_OPTION = (AppiumBy.ACCESSIBILITY_ID, "LanguageSettingsOption")
    SPANISH_LANG_RADIO = (AppiumBy.XPATH, "//*[contains(@text, 'Spanish') or contains(@text, 'Español')]")
    ENGLISH_LANG_RADIO = (AppiumBy.XPATH, "//*[contains(@text, 'English') or contains(@text, 'Inglés')]")

    # Similarity Slider
    SIMILARITY_SLIDER = (AppiumBy.ACCESSIBILITY_ID, "SimilaritySlider")

    def is_settings_screen_visible(self) -> bool:
        return self.is_element_present(self.SETTINGS_TITLE, timeout_type="default")

    def select_theme_dark(self):
        self.click(self.THEME_OPTION)
        self.click(self.DARK_THEME_RADIO)

    def select_language_spanish(self):
        self.click(self.LANGUAGE_OPTION)
        self.click(self.SPANISH_LANG_RADIO)

    def adjust_similarity_slider(self, percentage: float):
        """Drags the slider to the specified percentage of the screen width of the slider."""
        slider = self.find_element(self.SIMILARITY_SLIDER)
        location = slider.location
        size = slider.size
        
        start_x = location['x']
        end_x = start_x + size['width']
        y = location['y'] + (size['height'] / 2)
        
        target_x = start_x + (size['width'] * percentage)
        
        # Swipe from current middle to target_x
        self.driver.swipe(start_x + (size['width'] / 2), y, target_x, y, 600)
