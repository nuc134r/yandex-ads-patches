package app.revanced.extension.yandex.ads;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Called from the patched Yandex Mobile Ads SDK loaders instead of loading an ad.
 * <p>
 * The SDK keeps the names of its public API classes, so the failure callbacks and
 * load results are resolved by name with reflection. This keeps the patch working
 * across SDK versions whose signatures differ.
 */
@SuppressWarnings("unused")
public final class AdsBlocker {
    private static final String TAG = "revanced: AdsBlocker";
    private static final String ERROR_DESCRIPTION = "Ads are blocked";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AdsBlocker() {
    }

    /**
     * Called instead of a load method that has no listener parameter, e.g. {@code BannerAdView.loadAd}.
     * Collapses the ad view so no empty space is left behind.
     */
    public static void onLoadBlocked(Object loader) {
        if (loader instanceof View) {
            View view = (View) loader;
            mainHandler.post(() -> view.setVisibility(View.GONE));
        }
    }

    /**
     * Called instead of a load method that takes a load listener.
     * Reports a load failure to the listener so the app can hide the ad slot.
     */
    public static void notifyLoadFailed(Object listener) {
        if (listener == null) return;

        mainHandler.post(() -> {
            try {
                for (Method method : listener.getClass().getMethods()) {
                    if (!method.getName().contains("FailedToLoad")) continue;

                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length != 1) continue;

                    Object error = newInstance(parameterTypes[0]);
                    if (error == null) continue;

                    // The listener is usually an anonymous or private class of the app.
                    method.setAccessible(true);
                    method.invoke(listener, error);
                    return;
                }
            } catch (Throwable ex) {
                Log.e(TAG, "Failed to notify listener", ex);
            }
        });
    }

    /**
     * Called instead of a suspending load method.
     *
     * @return A {@code ...LoadResult.Failure} for the loader, or null to load the ad normally,
     * if the result could not be created.
     */
    public static Object createFailedLoadResult(Object loader) {
        try {
            String loaderName = loader.getClass().getName();
            if (!loaderName.endsWith("Loader")) return null;

            String resultName = loaderName.substring(0, loaderName.length() - "Loader".length())
                    + "LoadResult$Failure";
            Class<?> resultClass = Class.forName(resultName, true, loader.getClass().getClassLoader());

            return newInstance(resultClass);
        } catch (Throwable ex) {
            Log.e(TAG, "Failed to create load result", ex);
            return null;
        }
    }

    /**
     * Creates an instance of an SDK error or result class through any of its constructors.
     * Strings get {@link #ERROR_DESCRIPTION}, SDK types are created recursively.
     */
    private static Object newInstance(Class<?> type) {
        if (type == String.class) return ERROR_DESCRIPTION;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        if (type.isPrimitive() || type.isInterface()) return null;

        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] arguments = new Object[parameterTypes.length];

            boolean resolved = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                // Skip Kotlin synthetic default argument constructors.
                if (parameterType.getName().endsWith("DefaultConstructorMarker")) {
                    resolved = false;
                    break;
                }

                // Only SDK types, such as AdRequestError, are created. Anything else is passed as null.
                arguments[i] = parameterType.isPrimitive()
                        || parameterType == String.class
                        || parameterType.getName().startsWith("com.yandex.mobile.ads.")
                        ? newInstance(parameterType)
                        : null;
                if (arguments[i] == null && parameterType.isPrimitive()) {
                    resolved = false;
                    break;
                }
            }
            if (!resolved) continue;

            try {
                return constructor.newInstance(arguments);
            } catch (Throwable ignored) {
            }
        }

        return null;
    }
}
