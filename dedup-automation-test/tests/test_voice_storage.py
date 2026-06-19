import pytest
import time
from pages.login_page import LoginPage
from pages.voice_storage_page import VoiceStoragePage

def test_conversational_voice_permission_gate(driver, adb):
    """Test clicking mic triggers RECORD_AUDIO system permission gate."""
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()
        
    adb.revoke_permission("android.permission.RECORD_AUDIO")
    
    # Launch conversational voice storage screen directly via activity
    adb.execute("shell am start -n com.rp.dedup/com.rp.dedup.DemotestActivity -e screen voice_storage")
    time.sleep(2.5)
    
    chat_page = VoiceStoragePage(driver)
    
    # Click Microphone
    chat_page.click_microphone()
    time.sleep(1)
    
    # Assert system permission warning dialog shows
    assert chat_page.is_element_present(chat_page.RECORD_AUDIO_SYSTEM_ALLOW_BTN, timeout_type="short"), \
        "RECORD_AUDIO system permission pop-up failed to show."
        
    # Accept permission
    chat_page.click_allow_audio_permission()

def test_conversational_query_and_preview_strip(driver, adb):
    """Test sending text query, checking bot reply, suggestions chips, and previewing results."""
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()
        
    adb.grant_permission("android.permission.RECORD_AUDIO")
    adb.grant_permission("android.permission.READ_MEDIA_IMAGES")
    
    # Pre-populate device storage with duplicate files to ensure voice storage query returns hits
    remote_file = "/sdcard/Download/test_duplicate_conversational.jpg"
    adb.execute(f"shell 'echo \"conversational_dup\" > {remote_file}'")
    adb.execute(f"shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://{remote_file}")
    
    time.sleep(1)
    
    try:
        adb.execute("shell am start -n com.rp.dedup/com.rp.dedup.DemotestActivity -e screen voice_storage")
        time.sleep(2.5)
        
        chat_page = VoiceStoragePage(driver)
        
        # Click first suggestion chip (e.g. "Find duplicates" or "Show images")
        chat_page.click_first_suggestion_chip()
        time.sleep(3)  # Wait for query processing & streaming response
        
        # Verify bot bubble has sent response
        assert chat_page.is_element_present(chat_page.BOT_BUBBLE, timeout_type="default"), \
            "Bot did not respond to selection suggestion."
            
        # Manually type a storage action query
        chat_page.enter_query_and_send("senior_principal_fellow_query: show duplicate images")
        time.sleep(3.5)
        
        # Assert bot responses and verify storage matches display in the inline preview row
        assert chat_page.is_inline_preview_strip_visible(), \
            "Storage preview row failed to display matching files."
            
        # Expand preview list
        chat_page.expand_preview_to_full_screen()
        time.sleep(1)
        assert chat_page.is_full_screen_preview_visible(), \
            "Full screen preview overlay failed to launch."
            
        # Close overlay
        chat_page.close_full_screen_preview()
        
    finally:
        adb.delete_file(remote_file)

def test_chat_toolbar_settings_and_clear(driver, adb):
    """Test toggling TTS speaking, vibration mode, and clearing chat history."""
    adb.execute("shell am start -n com.rp.dedup/com.rp.dedup.DemotestActivity -e screen voice_storage")
    time.sleep(2)
    
    chat_page = VoiceStoragePage(driver)
    
    # Toggle Speech Synthesis (TTS)
    chat_page.toggle_tts()
    
    # Toggle Vibration feedback
    chat_page.toggle_vibration()
    
    # Clear conversation history
    chat_page.click_clear_history()
    time.sleep(1)
    
    # Verify screen does not contain old message lists
    assert not chat_page.is_element_present(chat_page.USER_BUBBLE, timeout_type="short"), \
        "Clear history action failed to remove message logs."
