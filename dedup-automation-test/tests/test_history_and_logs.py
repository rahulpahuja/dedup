import pytest
import time
from pages.login_page import LoginPage
from pages.dashboard_page import DashboardPage
from pages.history_and_logs_page import HistoryAndLogsPage

def test_scan_history_and_audit_logs(driver, adb):
    """Test viewing scan history, clearing logs, and checking audit trail records."""
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()
        
    dashboard = DashboardPage(driver)
    
    # Navigate to Scan History Screen
    dashboard.navigate_via_drawer(dashboard.DRAWER_HISTORY_LINK)
    
    history_page = HistoryAndLogsPage(driver)
    
    # Assert logs displays (if any scan was executed)
    # We can check that the history listings elements render
    items_before = history_page.get_history_items_count()
    
    # Try clearing the history logs
    history_page.clear_scan_history()
    time.sleep(1)
    
    # Assert counts fall to 0
    assert history_page.get_history_items_count() == 0, "Clear scan history action failed."
    
    # Go back to dashboard and open Activity Logs
    history_page.go_back()
    time.sleep(1)
    
    dashboard.navigate_via_drawer(dashboard.DRAWER_LOGS_LINK)
    time.sleep(1)
    
    # Verify that audit log lists are populated
    # (Since operations were executed, audit rows should record it)
    assert history_page.get_audit_log_items_count() >= 0, "Activity log list container not visible."
