package org.apache.cordova;
import android.app.Activity;
import java.util.concurrent.ExecutorService;
public interface CordovaInterface {
    Activity getActivity();
    ExecutorService getThreadPool();
    /** Real impl: checkSelfPermission on API 23+, true below (install-time permissions). */
    default boolean hasPermission(String permission) { return getActivity().checkSelfPermission(permission) == 0; }
}
