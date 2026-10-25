package app.omaapp;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Test-only service that activates Qt's Android accessibility bridge in emulator CI. */
public final class CiAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // The shell-driven UI smoke reads the hierarchy through UIAutomator.
    }

    @Override
    public void onInterrupt() {
        // No ongoing interaction is owned by this service.
    }
}
