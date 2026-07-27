import pytest
import time
from pages.login_page import LoginPage
from pages.whatsapp_cleaner_page import WhatsAppCleanerPage

def test_whatsapp_cleaner_received_sent_tabs(driver, adb):
    """Test standard WhatsApp cleaner layout, tab operations and deletion."""
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()
        
    adb.grant_permission("android.permission.WRITE_EXTERNAL_STORAGE")
    adb.grant_permission("android.permission.READ_EXTERNAL_STORAGE")
    
    # Mock WhatsApp media directory structure
    wa_received_dir = "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"
    wa_sent_dir = f"{wa_received_dir}/Sent"
    
    adb.execute(f"shell mkdir -p '{wa_sent_dir}'")
    
    mock_received = f"{wa_received_dir}/received_pic.jpg"
    mock_sent = f"{wa_sent_dir}/sent_pic.jpg"
    
    adb.execute(f"shell 'echo \"wa_received\" > {mock_received}'")
    adb.execute(f"shell 'echo \"wa_sent\" > {mock_sent}'")
    
    adb.execute(f"shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://{mock_received}")
    adb.execute(f"shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://{mock_sent}")
    
    time.sleep(1.5)
    
    try:
        # Launch WhatsApp Cleaner screen directly via test activity
        adb.execute("shell am start -n com.rp.dedup/com.rp.dedup.DemotestActivity -e screen whatsapp_cleaner")
        time.sleep(2)
        
        wa_cleaner = WhatsAppCleanerPage(driver)
        
        # Verify default received files appear
        assert wa_cleaner.get_files_list_count() > 0, "No received files displayed in WhatsApp Cleaner list."
        
        # Switch to Sent tab and verify sent files are indexed
        wa_cleaner.switch_to_sent_tab()
        time.sleep(1)
        assert wa_cleaner.get_files_list_count() > 0, "No sent files displayed in WhatsApp Sent list."
        
        # Select all and delete
        wa_cleaner.select_all_files()
        wa_cleaner.click_delete_selected()
        
        # Verify files count drops to 0
        time.sleep(1.5)
        assert wa_cleaner.get_files_list_count() == 0, "WhatsApp Sent cleanup failed to delete files."
        
    finally:
        adb.delete_file(mock_received)
        adb.delete_file(mock_sent)
