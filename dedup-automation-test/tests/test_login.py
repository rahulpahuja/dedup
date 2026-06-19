import pytest
from pages.login_page import LoginPage
from pages.dashboard_page import DashboardPage

def test_login_as_guest(driver, adb):
    """Test that continuing as a Guest correctly routes the user to the main dashboard."""
    # Start clean by clearing app data
    adb.clear_app_data()
    
    # Relaunching the app
    login_page = LoginPage(driver)
    
    # Verify login screen is visible
    assert login_page.is_login_screen_visible(), "Login screen was not shown."
    
    # Click Guest mode
    login_page.click_continue_as_guest()
    
    # Assert successful landing on dashboard
    dashboard = DashboardPage(driver)
    assert dashboard.is_dashboard_visible(), "Dashboard was not shown after clicking Guest Mode."

def test_google_sign_in_click(driver):
    """Verify that clicking Google Sign-In prompts account selection."""
    login_page = LoginPage(driver)
    
    # Click Google Sign In button
    login_page.click_google_sign_in()
    
    # Verify that either the system Google Sign-In popup or WebOAuth redirects
    # We look for Google Play Services account chooser by checking class name
    account_chooser_present = login_page.is_element_present(
        ("xpath", "//*[contains(@resource-id, 'account_picker') or contains(@text, 'Choose an account')]"),
        timeout_type="default"
    )
    assert account_chooser_present, "Google Play Services account selection did not show up."
