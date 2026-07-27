import pytest
from pages.splash_page import SplashPage
from pages.login_page import LoginPage
from pages.dashboard_page import DashboardPage

def test_splash_screen_initialization(driver):
    """Test that the splash screen displays on launch and successfully transitions to login or dashboard."""
    splash = SplashPage(driver)
    
    # Assert splash logo is visible during initial load
    assert splash.is_splash_visible(), "Splash logo was not displayed on app launch."
    
    # Wait for transition (either login page or dashboard appears depending on session state)
    login_page = LoginPage(driver)
    dashboard = DashboardPage(driver)
    
    is_login_visible = login_page.is_login_screen_visible()
    is_dashboard_visible = dashboard.is_dashboard_visible()
    
    assert is_login_visible or is_dashboard_visible, "App failed to navigate to Login or Dashboard after splash."

def test_root_warning_handling_proceed(driver, adb):
    """Test root detection warning when root binaries are mocked on device."""
    # Senior Principal Tip: Simulate root by pushing a dummy 'su' binary to device path
    adb.execute("shell mkdir -p /system/xbin")
    adb.create_mock_file("/system/xbin/su", size_in_bytes=100)
    
    # Force close and relaunch app to trigger fresh root check
    adb.force_stop()
    
    splash = SplashPage(driver)
    try:
        # Verify the root warning dialog displays
        assert splash.wait_for_root_warning(), "Root warning dialog did not display on a rooted device."
        
        # Click Proceed to allow advanced users to bypass
        splash.accept_root_warning()
        
        # Verify transition continues to app interface
        login_page = LoginPage(driver)
        assert login_page.is_login_screen_visible(), "Failed to proceed past root warning screen."
    finally:
        # Cleanup mocked root file
        adb.delete_file("/system/xbin/su")
