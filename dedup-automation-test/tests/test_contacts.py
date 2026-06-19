import pytest
import time
from pages.login_page import LoginPage
from pages.contacts_page import ContactsPage

def test_contact_deduplication_and_merging(driver, adb):
    """Test contact scanner scanning duplicates, previewing merges, and executing merges."""
    login_page = LoginPage(driver)
    if login_page.is_login_screen_visible():
        login_page.click_continue_as_guest()
        
    adb.grant_permission("android.permission.READ_CONTACTS")
    adb.grant_permission("android.permission.WRITE_CONTACTS")
    
    # Senior Principal Fellow Tip: Mock duplicate contacts inside Android database using content provider insert
    # This ensures hermetic test data!
    adb.execute("shell content insert --uri content://com.android.contacts/contacts --bind name:s:'Fellow Duplicate' --bind number:s:'9999999999'")
    adb.execute("shell content insert --uri content://com.android.contacts/contacts --bind name:s:'Fellow Duplicate' --bind number:s:'9999999999'")
    
    try:
        # Launch Contacts Scanner screen directly via activity
        adb.execute("shell am start -n com.rp.dedup/com.rp.dedup.DemotestActivity -e screen contacts_scanner")
        time.sleep(2.5)
        
        contacts = ContactsPage(driver)
        
        # Verify duplicate rows are found
        assert contacts.get_duplicate_contacts_count() > 0, "No duplicate contacts found in scanner."
        
        # Click preview merge
        contacts.preview_merge()
        assert contacts.is_preview_visible(), "Merge preview dialog was not displayed."
        
        # Click confirm merge
        contacts.confirm_merge()
        
        # Verify that a success message/toast displays
        assert contacts.is_merge_success_toast_visible(), "Success toast did not appear after contacts merge."
        
    finally:
        # Clean up contacts database
        adb.execute("shell content delete --uri content://com.android.contacts/contacts --where \"display_name='Fellow Duplicate'\"")
