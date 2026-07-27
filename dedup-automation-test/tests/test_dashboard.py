import pytest
from pages.login_page import LoginPage
from pages.dashboard_page import DashboardPage
from pages.settings_page import SettingsPage

def test_dashboard_storage_display_and_navigation(driver, adb):
    """Test dashboard elements rendering and navigation buttons."""
    # Ensure guest session is established
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()
        
    dashboard = DashboardPage(driver)
    assert dashboard.is_dashboard_visible(), "Dashboard did not display."
    
    # Assert storage strings display something containing 'GB' or 'MB'
    used_text = dashboard.get_used_storage()
    free_text = dashboard.get_free_storage()
    
    assert any(unit in used_text for unit in ["GB", "MB", "KB", "B"]), f"Used storage value '{used_text}' invalid."
    assert any(unit in free_text for unit in ["GB", "MB", "KB", "B"]), f"Free storage value '{free_text}' invalid."

def test_drawer_navigation_to_settings(driver):
    """Test drawer opening and navigating to the Settings screen."""
    dashboard = DashboardPage(driver)
    settings = SettingsPage(driver)
    
    # Navigate to Settings screen via the Navigation Drawer
    dashboard.navigate_via_drawer(dashboard.DRAWER_SETTINGS_LINK)
    
    # Verify settings page displays
    assert settings.is_settings_screen_visible(), "Settings screen was not displayed after drawer click."
    
    # Go back to dashboard
    settings.go_back()
    assert dashboard.is_dashboard_visible(), "Failed to return to Dashboard from Settings."
class_name = "test_dashboard"
