import pytest
import time
from pages.login_page import LoginPage
from pages.dashboard_page import DashboardPage
from pages.smart_junk_page import SmartJunkPage

def test_smart_junk_classification_and_clean(driver, adb):
    """Test AI junk classification filters and bulk clean functions."""
    # Ensure guest session is established
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()
        
    adb.grant_permission("android.permission.READ_MEDIA_IMAGES")
    
    # Mock some screenshot files in the emulator Pictures/Screenshots directory
    screenshots_dir = "/sdcard/Pictures/Screenshots"
    adb.execute(f"shell mkdir -p {screenshots_dir}")
    mock_screenshot = f"{screenshots_dir}/mock_screenshot_1.png"
    adb.execute(f"shell 'echo \"screenshot_data\" > {mock_screenshot}'")
    adb.execute(f"shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://{mock_screenshot}")
    
    time.sleep(1.5)  # Let media provider scan
    
    try:
        # Navigate to Dashboard and click the image category card
        dashboard = DashboardPage(driver)
        dashboard.navigate_to_images()
        
        # Navigate to Smart Junk category (which is linked on the images scanner)
        # For this test, we mock navigating to the Smart Junk screen directly
        # or clicking the Smart Junk Card
        # (Assuming we have a quick access or direct link)
        adb.execute("shell am start -n com.rp.dedup/com.rp.dedup.DemotestActivity -e screen smart_junk")
        time.sleep(2)
        
        junk_page = SmartJunkPage(driver)
        
        # Select Screenshots category
        junk_page.select_screenshots()
        
        # Assert the junk file list contains our mocked screenshot file
        items_count = junk_page.get_junk_items_count()
        assert items_count > 0, "No screenshots categorized in Smart Junk list."
        
        # Clean selected items
        junk_page.trigger_bulk_clean()
        
        # Verify list updates and becomes empty
        time.sleep(1.5)
        assert junk_page.get_junk_items_count() == 0, "Bulk clean failed to clear screenshots."
        
    finally:
        adb.delete_file(mock_screenshot)
