import pytest
import time
from pages.login_page import LoginPage
from pages.dashboard_page import DashboardPage
from pages.deep_optimization_page import DeepOptimizationPage
from pages.empty_folder_page import EmptyFolderPage

def test_deep_system_optimization_and_empty_folders(driver, adb):
    """Test Wellness score checks and empty folders cleanup."""
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()
        
    dashboard = DashboardPage(driver)
    dashboard.navigate_to_deep_optimization()
    
    deep_opt = DeepOptimizationPage(driver)
    assert deep_opt.is_element_present(deep_opt.WELLNESS_SCORE), "Wellness score not displayed."
    
    score_before = deep_opt.get_wellness_score()
    
    # Let's mock empty folders on storage card
    empty_dir_1 = "/sdcard/Download/EmptyFolderTest1"
    empty_dir_2 = "/sdcard/Download/EmptyFolderTest2"
    adb.execute(f"shell mkdir -p {empty_dir_1}")
    adb.execute(f"shell mkdir -p {empty_dir_2}")
    
    try:
        # Navigate to Empty Folder Cleaner
        deep_opt.navigate_to_empty_folder_cleaner()
        
        empty_page = EmptyFolderPage(driver)
        assert empty_page.get_empty_folders_count() >= 2, "Mocked empty folders were not detected by scanner."
        
        # Click clean all
        empty_page.click_clean_all()
        time.sleep(1.5)
        
        # Verify empty folder count drops to 0 or 'no folders found' message appears
        assert empty_page.is_no_folders_message_visible() or empty_page.get_empty_folders_count() == 0, "Failed to remove empty folders."
        
        # Go back to deep optimization dashboard and check score updates
        empty_page.go_back()
        time.sleep(1)
        score_after = deep_opt.get_wellness_score()
        # Wellness score should have updated (e.g. higher score)
        
    finally:
        # Cleanup mock folders
        adb.execute(f"shell rmdir {empty_dir_1}")
        adb.execute(f"shell rmdir {empty_dir_2}")
