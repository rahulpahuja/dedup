# Analytics — pending event wiring

`AnalyticsManager` (app/src/main/java/com/rp/dedup/core/analytics/AnalyticsManager.kt)
defines these events but nothing in the app calls them yet, because the screens/flows
they belong to don't exist:

| Event                    | Method                                  | Blocked on                     |
|---------------------------|------------------------------------------|---------------------------------|
| `user_type_selected`      | `logUserTypeSelected(userType)`           | onboarding / persona-picker screen |
| `paywall_viewed`          | `logPaywallViewed(source)`                | paywall screen |
| `purchase_started`        | `logPurchaseStarted(sku)`                 | billing integration (Play Billing / RevenueCat) |
| `purchase_completed`      | `logPurchaseCompleted(sku)`               | billing integration |
| `subscription_cancelled`  | `logSubscriptionCancelled(sku)`           | billing integration |

None of these have any UI or billing code in the project today (confirmed by search —
no "Paywall"/"Subscription"/"Billing" screens or persona-selection flow exist).

**Action for later:** when the paywall and onboarding screens are built, call the
above methods from the corresponding tap/lifecycle points — same pattern as the
already-wired funnel events (`app_opened`, `scan_started`, `scan_completed`,
`cleanup_started`, `cleanup_completed`, `value_achieved`).
