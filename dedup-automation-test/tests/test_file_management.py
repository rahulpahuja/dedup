import pytest
import time
from pages.login_page import LoginPage
from pages.dashboard_page import DashboardPage
from pages.big_file_map_page import BigFileMapPage
from pages.file_browser_page import FileBrowserPage

def test_big_file_map_treemap_interactions(driver, adb):
    """Test loading and interacting with the Big File Map Treemap visualization."""
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()
        
    dashboard = DashboardPage(driver)
    dashboard.navigate_to_big_files()
    
    treemap = BigFileMapPage(driver)
    assert treemap.is_treemap_visible(), "Treemap node visualizations failed to render."
    
    initial_path = treemap.get_current_path()
    
    # Click first large folder node in treemap to drill down
    treemap.click_node_by_index(0)
    time.sleep(1)
    
    # Assert directory path header changes
    new_path = treemap.get_current_path()
    assert initial_path != new_path, "Treemap failed to drill down into folder."
    
    # Click Up navigation to return to root
    treemap.navigate_up()
    time.sleep(1)
    assert treemap.get_current_path() == initial_path, "Up navigation inside Treemap did not return to root path."

def test_file_browser_sort_and_quick_share(driver, adb):
    """Test listing, sorting, and contextual shares in FileBrowser screen."""
    # Launch File Browser directly via test activity
    adb.execute("shell am start -n com.rp.dedup/com.rp.dedup.DemotestActivity -e screen file_browser")
    time.sleep(2)
    
    browser = FileBrowserPage(driver)
    
    # Assert browser lists items
    assert len(driver.find_elements(*browser.FILE_ROW)) > 0, "File browser is empty."
    
    # Perform sorting by size
    browser.select_sort_by_size()
    time.sleep(1)
    
    # Try triggering quick share on the first item
    browser.trigger_share_action(index=0)
    time.sleep(1.5)
    
    # Verify that the Android system share sheet opens (resource packages include 'resolver' or 'chooser')
    system_sharesheet_visible = browser.is_element_present(
        ("xpath", "//*[contains(@resource-id, 'resolver_list') or contains(@text, 'Share with') or contains(@text, 'Compartir')]"),
        timeout_type="default"
    )
    assert system_sharesheet_visible, "Android System Sharesheet did not appear on share command."
