package io.github.yvichyi.sciencegallery;

import android.net.Uri;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Device-level smoke tests: these execute in a real Android WebView
 * inside the emulator, not in a desktop mock or JVM-only test.
 */
@RunWith(AndroidJUnit4.class)
public class GallerySmokeTest {
    private static final String BASE = "https://appassets.androidplatform.net/assets/";

    @Rule
    public ActivityScenarioRule<MainActivity> activity =
            new ActivityScenarioRule<>(MainActivity.class);

    private String javascript(String script) throws Exception {
        final CountDownLatch ready = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        activity.getScenario().onActivity(a -> {
            ViewGroup content = a.findViewById(android.R.id.content);
            assertTrue("Activity has WebView child", content.getChildCount() > 0);
            WebView view = (WebView) content.getChildAt(0);
            view.evaluateJavascript(script, value -> {
                result.set(value);
                ready.countDown();
            });
        });
        assertTrue("JavaScript callback timed out", ready.await(12, TimeUnit.SECONDS));
        assertNotNull(result.get());
        return result.get();
    }

    private void awaitTitle(String fragment) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 30000;
        String title = "";
        while (SystemClock.uptimeMillis() < deadline) {
            title = javascript("document.title");
            if (title.contains(fragment)) {
                return;
            }
            SystemClock.sleep(600);
        }
        throw new AssertionError("Expected title containing " + fragment + ", got: " + title);
    }

    @Test
    public void offlineHomeLoadsWithJavaScriptAndExperimentLinks() throws Exception {
        awaitTitle("互动科学装置");
        int links = Integer.parseInt(
                javascript("document.querySelectorAll('a[href$=\".html\"]').length"));
        assertTrue("Gallery has clickable HTML experiments, found " + links, links >= 50);
        String ready = javascript("document.readyState");
        assertTrue("DOM ready", ready.contains("complete"));
    }

    @Test
    public void nonAsciiExperimentLoadsAsBundledAsset() throws Exception {
        awaitTitle("互动科学装置");
        String experiment = BASE + Uri.encode("拥堵之波.html");
        activity.getScenario().onActivity(a -> {
            WebView view = (WebView) ((ViewGroup) a.findViewById(
                    android.R.id.content)).getChildAt(0);
            view.loadUrl(experiment);
        });
        awaitTitle("拥堵");
        String length = javascript("document.body ? document.body.textContent.length : 0");
        assertTrue("Experiment body not empty, length=" + length,
                Integer.parseInt(length) >= 300);
    }
}
