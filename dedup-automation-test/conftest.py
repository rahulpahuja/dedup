import pytest
import os
import subprocess
import time
from datetime import datetime
from appium import webdriver
from appium.options.android import UiAutomator2Options
from config import TestConfig

# Global test results collector
_test_results = []

@pytest.fixture(scope="function")
def driver():
    """Fixture to initialize and tear down the Appium driver."""
    options = UiAutomator2Options()
    for key, value in TestConfig.CAPABILITIES.items():
        options.set_capability(key, value)
    
    print(f"\nConnecting to Appium Server at {TestConfig.APPIUM_SERVER_URL}...")
    driver = webdriver.Remote(TestConfig.APPIUM_SERVER_URL, options=options)
    
    yield driver
    
    print("\nTearing down Appium Driver session...")
    driver.quit()

@pytest.fixture(scope="session")
def adb():
    """ADB Helper class to mock system state and files during tests."""
    class ADBHelper:
        def execute(self, cmd: str) -> str:
            full_cmd = f"adb {cmd}"
            result = subprocess.run(full_cmd, shell=True, capture_output=True, text=True)
            return result.stdout.strip()
        
        def push_file(self, local_path: str, remote_path: str):
            self.execute(f"push {local_path} {remote_path}")
            
        def delete_file(self, remote_path: str):
            self.execute(f"shell rm -f {remote_path}")
            
        def clear_app_data(self):
            self.execute(f"shell pm clear {TestConfig.CAPABILITIES['appPackage']}")
            
        def grant_permission(self, permission: str):
            self.execute(f"shell pm grant {TestConfig.CAPABILITIES['appPackage']} {permission}")
            
        def revoke_permission(self, permission: str):
            self.execute(f"shell pm revoke {TestConfig.CAPABILITIES['appPackage']} {permission}")
            
        def force_stop(self):
            self.execute(f"shell am force-stop {TestConfig.CAPABILITIES['appPackage']}")

        def create_mock_file(self, remote_path: str, size_in_bytes: int):
            self.execute(f"shell dd if=/dev/zero of={remote_path} bs=1 count={size_in_bytes}")

    return ADBHelper()

# Pytest hook to capture screenshots and embed them inside pytest-html reports on failure
@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    extra = getattr(rep, "extra", [])
    
    if rep.when == "call" and rep.failed:
        try:
            if "driver" in item.funcargs:
                driver = item.funcargs["driver"]
                # Save physical screenshot
                screenshot_dir = os.path.join(os.path.dirname(__file__), "reports", "screenshots")
                os.makedirs(screenshot_dir, exist_ok=True)
                screenshot_path = os.path.join(screenshot_dir, f"{item.name}.png")
                driver.save_screenshot(screenshot_path)
                
                # Base64 encode for pytest-html embedding
                base64_data = driver.get_screenshot_as_base64()
                pytest_html = item.config.pluginmanager.getplugin('html')
                if pytest_html:
                    html_embed = (
                        f'<div><img src="data:image/png;base64,{base64_data}" '
                        f'alt="failure_screenshot" style="width:250px;height:450px;border:1px solid #ddd;border-radius:4px;" '
                        f'onclick="window.open(this.src)" align="right"/></div>'
                    )
                    extra.append(pytest_html.extras.html(html_embed))
                    rep.extra = extra
        except Exception as e:
            print(f"\nFailed to capture/embed screenshot in HTML report: {e}")

# Collects results of every test in real-time
def pytest_runtest_logreport(report):
    if report.when in ["setup", "call", "teardown"]:
        # Log failures or call execution pass states
        if report.failed or (report.when == "call" and report.passed) or (report.when == "setup" and report.skipped):
            exists = any(r["nodeid"] == report.nodeid for r in _test_results)
            if not exists or (exists and report.failed):
                log = {
                    "nodeid": report.nodeid,
                    "name": report.nodeid.split("::")[-1],
                    "status": report.outcome.upper(),
                    "duration": round(report.duration, 2),
                    "error": report.longreprtext if report.failed else ""
                }
                if exists:
                    # Filter out older setup status if execution call fails
                    _test_results[:] = [r for r in _test_results if r["nodeid"] != report.nodeid]
                _test_results.append(log)

# Compiles summary metrics and saves a Markdown dashboard report on suite finish
def pytest_sessionfinish(session, exitstatus):
    reports_dir = os.path.join(os.path.dirname(__file__), "reports")
    os.makedirs(reports_dir, exist_ok=True)
    summary_path = os.path.join(reports_dir, "LATEST_RUN_SUMMARY.md")
    
    total = len(_test_results)
    passed = sum(1 for r in _test_results if r["status"] == "PASSED")
    failed = sum(1 for r in _test_results if r["status"] == "FAILED")
    skipped = sum(1 for r in _test_results if r["status"] == "SKIPPED")
    duration = sum(r["duration"] for r in _test_results)
    
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    status_msg = "🟢 SUCCESS" if exitstatus == 0 else "🔴 FAILURES DETECTED"
    
    md_content = f"""# 📊 DeDup Automation Test Execution Dashboard

**Generated at**: `{timestamp}`  
**Target App Package**: `com.rp.dedup`  
**Execution Status**: {status_msg}

---

## 📈 Summary Metrics

| Metric | Value |
| :--- | :--- |
| **Total Tests Executed** | `{total}` |
| **Passed** | `✅ {passed}` |
| **Failed** | `❌ {failed}` |
| **Skipped** | `⚠️ {skipped}` |
| **Total Test Suite Duration** | `{duration:.2f} seconds` |

---

## 📋 Test Execution Details

| Test Case Name | Status | Duration | Screenshot / Details |
| :--- | :---: | :---: | :--- |
"""
    for r in _test_results:
        status_icon = "✅ PASSED" if r["status"] == "PASSED" else "❌ FAILED" if r["status"] == "FAILED" else "⚠️ SKIPPED"
        screenshot_link = ""
        if r["status"] == "FAILED":
            screenshot_link = f"[Screenshot](screenshots/{r['name']}.png)"
        md_content += f"| `{r['name']}` | {status_icon} | `{r['duration']}s` | {screenshot_link} |\n"
        
    if failed > 0:
        md_content += "\n---\n\n## 🔍 Failure Analysis Details\n\n"
        for r in _test_results:
            if r["status"] == "FAILED":
                md_content += f"### ❌ `{r['name']}`\n```python\n{r['error']}\n```\n\n"
                
    with open(summary_path, "w") as f:
        f.write(md_content)
        
    print(f"\n[Report Generated] Saved Markdown execution dashboard to: {summary_path}")
