import pytest
import time
from pages.login_page import LoginPage
from pages.dashboard_page import DashboardPage
from pages.scanner_page import ScannerPage
from pages.deduplication_page import DeduplicationPage

def test_permission_gate_and_explanation(driver, adb):
    """Test that scanning triggers the permission explanation gate when permissions are missing."""
    # Ensure guest session is established
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()

    # Revoke media storage permissions to force warning trigger
    adb.revoke_permission("android.permission.READ_MEDIA_IMAGES")
    adb.revoke_permission("android.permission.READ_EXTERNAL_STORAGE")
    
    dashboard = DashboardPage(driver)
    dashboard.navigate_to_images()
    
    scanner = ScannerPage(driver)
    scanner.click_scan()
    
    # Assert explanation dialog is shown
    assert scanner.is_permission_gate_visible(), "Permission explanation gate dialog did not show up."
    
    # Click Agree/Grant on custom dialog
    scanner.accept_permission_gate()
    
    # Verify that the system permission popup displays
    # UI Automator is used here to allow access
    scanner.click_allow_on_system_dialog()

def test_duplicate_media_detection_and_deletion(driver, adb):
    """Integrates ADB mocks to write duplicate files on device storage and test app scans and deletes them."""
    # 1. Start session & pre-grant storage permissions via ADB for a smooth flow
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()
        
    adb.grant_permission("android.permission.READ_MEDIA_IMAGES")
    adb.grant_permission("android.permission.WRITE_EXTERNAL_STORAGE")
    adb.grant_permission("android.permission.READ_EXTERNAL_STORAGE")
    
    # 2. Mock 2 identical files on the emulator via ADB
    # We will write some arbitrary matching byte files
    remote_path_1 = "/sdcard/Download/test_duplicate_1.jpg"
    remote_path_2 = "/sdcard/Download/test_duplicate_2.jpg"
    
    # Create matching content files
    adb.execute(f"shell 'echo \"senior_principal_fellow_test_bytes\" > {remote_path_1}'")
    adb.execute(f"shell 'echo \"senior_principal_fellow_test_bytes\" > {remote_path_2}'")
    
    # Trigger Android MediaScanner to index these newly pushed files
    adb.execute(f"shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://{remote_path_1}")
    adb.execute(f"shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://{remote_path_2}")
    
    time.sleep(2)  # Allow MediaScanner a brief moment to process
    
    try:
        dashboard = DashboardPage(driver)
        dashboard.navigate_to_images()
        
        scanner = ScannerPage(driver)
        scanner.click_scan()
        
        # Verify scan progress starts
        assert scanner.is_scanning(), "Scanning progress did not launch."
        
        # Wait for scan to complete and show results screen
        scanner.wait_for_scan_completion()
        
        results = DeduplicationPage(driver)
        assert results.is_results_screen_visible(), "Deduplication results screen was not displayed."
        
        # Assert at least one duplicate group is found and has recommended 'Best Shot'
        assert results.is_best_shot_recommended(), "Best shot recommendation flag missing from duplicate cluster."
        
        checkboxes = results.get_duplicate_checkboxes()
        assert len(checkboxes) > 0, "No duplicate checkboxes detected for selection."
        
        # Verify deletion confirmation gate
        results.click_delete_selected()
        assert results.is_confirmation_dialog_visible(), "Deletion confirmation dialog did not show up."
        
        # Perform confirmation
        results.confirm_deletion()
        
        # Verify results reload/update and elements are clean
        time.sleep(2)
        assert not results.is_best_shot_recommended(), "Duplicate cluster still visible after deletion."
        
    finally:
        # Cleanup mock files via shell in case test fails
        adb.delete_file(remote_path_1)
        adb.delete_file(remote_path_2)
