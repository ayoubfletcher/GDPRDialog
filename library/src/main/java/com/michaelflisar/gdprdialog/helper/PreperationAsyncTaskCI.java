package com.michaelflisar.gdprdialog.helper;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import com.michaelflisar.gdprdialog.GDPR;
import com.michaelflisar.gdprdialog.GDPRConsent;
import com.michaelflisar.gdprdialog.GDPRConsentState;
import com.michaelflisar.gdprdialog.GDPRLocation;
import com.michaelflisar.gdprdialog.GDPRLocationCheck;
import com.michaelflisar.gdprdialog.GDPRSetup;

import java.lang.ref.WeakReference;

public class PreperationAsyncTaskCI extends AsyncTask<Object, Void, GDPRPreperationData> {

    private WeakReference<Activity> mActivity;
    private WeakReference<GDPR.IGDPRCallback> migdprCallback;
    private GDPRSetup mSetup;

    public PreperationAsyncTaskCI(Activity activity, GDPRSetup setup, GDPR.IGDPRCallback igdprCallback) {
        mActivity = new WeakReference<>(activity);
        this.migdprCallback = new WeakReference<>(igdprCallback);
        mSetup = setup;
    }

    protected GDPRPreperationData doInBackground(Object... ignored) {
        GDPRPreperationData result = new GDPRPreperationData();
        GDPRLocationCheck[] checks = mSetup.requestLocationChecks();
        Activity activity = mActivity.get();

        // 1) preperation
        boolean loadAdNetworks = mSetup.getPublisherIds().size() > 0;
        boolean checkLocationViaInternet = false;
        for (int i = 0; i < checks.length; i++) {
            if (checks[i] == GDPRLocationCheck.INTERNET) {
                checkLocationViaInternet = true;
                break;
            }
        }

        // 2) checks
        if (activity != null) {

            // load networks and location from internet
            GDPRPreperationData data = new GDPRPreperationData();
            if (loadAdNetworks || checkLocationViaInternet) {
                data.load(activity, mSetup.getPublisherIds(), mSetup.connectionReadTimeout(), mSetup.connectionConnectTimeout());
                if (!checkLocationViaInternet) {
                    data.updateLocation(GDPRLocation.UNDEFINED);
                }
                result.getSubNetworks().clear();
                result.getSubNetworks().addAll(data.getSubNetworks());
            }

            // check location until a method succeeds
            for (int i = 0; i < checks.length; i++) {
                switch (checks[i]) {
                    case INTERNET:
                        // already done above => so use location from above if it was successful
                        if (!data.hasError()) {
                            result.updateLocation(data.getLocation());
                        } else {
                            result.setManually(null);
                        }
                        break;
                    case TELEPHONY_MANAGER:
                        result.setManually(GDPRUtils.isRequestInEAAOrUnknownViaTelephonyManagerCheck(activity));
                        break;
                    case TIMEZONE:
                        result.setManually(GDPRUtils.isRequestInEAAOrUnknownViaTimezoneCheck());
                        break;
                    case LOCALE:
                        result.setManually(GDPRUtils.isRequestInEAAOrUnknownViaLocaleCheck());
                        break;
                }

                // stop as soon as we have found a valid location
                if (result.getLocation() != GDPRLocation.UNDEFINED && !result.hasError()) {
                    break;
                }
            }
        }

        GDPR.getInstance().getLogger().debug("PreperationAsyncTaskCI", String.format("GDPRPreperationData: %s", result.logString()));

        return result;
    }

    protected void onPostExecute(GDPRPreperationData result) {
        if (isCancelled()) {
            return;
        }
        Activity activity = mActivity.get();
        GDPR.IGDPRCallback igdprCallback = migdprCallback.get();
        Log.d("MurdieX", "Prepare task!");
        if (activity != null && !activity.isFinishing()) {
            if (mSetup.requestLocationChecks().length > 0 && result.getLocation() == GDPRLocation.NOT_IN_EAA) {
                // user does want to not request consent and consider this as consent given, so we save this here
                GDPRConsentState consentState = new GDPRConsentState(activity, GDPRConsent.AUTOMATIC_PERSONAL_CONSENT, result.getLocation());
                GDPR.getInstance().setConsent(consentState);
                Log.d("MurdieX", "Trigger new state!");
                igdprCallback.onConsentInfoUpdate(consentState, true);
            } else {
                igdprCallback.onConsentNeedsToBeRequested(result);
            }
        } else {
            Log.d("MurdieX", "Activity is finished!");
        }
    }
}
