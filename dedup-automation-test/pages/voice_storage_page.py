from appium.webdriver.common.appiumby import AppiumBy
from pages.base_page import BasePage

class VoiceStoragePage(BasePage):
    # Locators
    TEXT_INPUT = (AppiumBy.XPATH, "//android.widget.EditText")
    SEND_BTN = (AppiumBy.XPATH, "//*[contains(@content-desc, 'Send') or contains(@text, 'Send')]")
    MIC_BTN = (AppiumBy.XPATH, "//*[contains(@content-desc, 'Mic') or contains(@content-desc, 'Listen') or contains(@resource-id, 'MicButton')]")
    
    # Header Toolbar actions
    TTS_TOGGLE_BTN = (AppiumBy.XPATH, "//*[contains(@content-desc, 'TTS')]")
    VIBRATION_TOGGLE_BTN = (AppiumBy.XPATH, "//*[contains(@content-desc, 'vibration')]")
    CLEAR_HISTORY_BTN = (AppiumBy.XPATH, "//*[contains(@content-desc, 'Clear history')]")
    
    # Conversation Bubbles
    USER_BUBBLE = (AppiumBy.XPATH, "//*[contains(@text, 'senior_principal_fellow_query') or @resource-id='UserBubble']")
    BOT_BUBBLE = (AppiumBy.XPATH, "//*[contains(@text, 'files') or contains(@text, 'duplicates') or @resource-id='BotBubble']")
    STREAMING_BUBBLE = (AppiumBy.XPATH, "//*[contains(@resource-id, 'streaming')]")
    
    # Suggestion Chips
    SUGGESTION_CHIP = (AppiumBy.XPATH, "//*[contains(@text, 'Show') or contains(@text, 'Find') or contains(@text, 'Clean')]")
    
    # Storage preview cards
    PREVIEW_STRIP = (AppiumBy.XPATH, "//*[contains(@content-desc, 'Expand to full screen') or contains(@resource-id, 'StoragePreviewRow')]")
    EXPAND_PREVIEW_BTN = (AppiumBy.XPATH, "//*[contains(@content-desc, 'Expand to full screen')]")

    # Full Screen Preview Locators
    FULL_SCREEN_PREVIEW_CONTAINER = (AppiumBy.XPATH, "//*[contains(@resource-id, 'FullScreenPreview')]")
    FULL_SCREEN_CLOSE_BTN = (AppiumBy.XPATH, "//*[contains(@content-desc, 'Close') or contains(@text, 'Close')]")
    DELETE_PREVIEW_ITEMS_BTN = (AppiumBy.XPATH, "//*[contains(@text, 'Delete') or contains(@text, 'Remove')]")

    # Recording Permission Gate
    RECORD_AUDIO_SYSTEM_ALLOW_BTN = (AppiumBy.XPATH, "//*[contains(@resource-id, 'permission_allow_button') or contains(@text, 'Allow') or contains(@text, 'while using the app')]")

    def enter_query_and_send(self, query: str):
        self.send_keys(self.TEXT_INPUT, query)
        self.click(self.SEND_BTN)

    def click_microphone(self):
        self.click(self.MIC_BTN)

    def click_allow_audio_permission(self):
        self.click(self.RECORD_AUDIO_SYSTEM_ALLOW_BTN, timeout_type="short")

    def click_first_suggestion_chip(self):
        self.click(self.SUGGESTION_CHIP)

    def is_inline_preview_strip_visible(self) -> bool:
        return self.is_element_present(self.PREVIEW_STRIP, timeout_type="default")

    def expand_preview_to_full_screen(self):
        self.click(self.EXPAND_PREVIEW_BTN)

    def is_full_screen_preview_visible(self) -> bool:
        return self.is_element_present(self.FULL_SCREEN_PREVIEW_CONTAINER, timeout_type="default")

    def close_full_screen_preview(self):
        self.click(self.FULL_SCREEN_CLOSE_BTN)

    def click_clear_history(self):
        self.click(self.CLEAR_HISTORY_BTN)

    def toggle_tts(self):
        self.click(self.TTS_TOGGLE_BTN)

    def toggle_vibration(self):
        self.click(self.VIBRATION_TOGGLE_BTN)
