/*
 * Copyright (c) 2019. Alexis Ringot, Florian Taurer, Matthias Zeppelzauer.
 *
 * This file is part of SoniTalk Android SDK.
 *
 * SoniTalk Android SDK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SoniTalk Android SDK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SoniTalk Android SDK.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.ac.fhstp.sonitalk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The SoniTalkPermissionManager ensures that SoniTalkDecoder and SoniTalkSender objects have the
 * right permission before they start communicating.
 */
/*package-private*/ class SoniTalkPermissionManager implements PermissionLevelDialogActivity.PermissionLevelDialogListener, PermissionRequestDialogActivity.PermissionRequestDialogListener{
    private final String TAG = this.getClass().getSimpleName();
    private ResultReceiver sdkListener;
    private AtomicInteger permissionLevel = new AtomicInteger(-1);
    /*package-private*/ boolean currentRequestGranted;
    /*package-private*/ boolean currentRequestAnswered;
    /*package-private*/ boolean sessionPermissionGranted;

    /*package-private*/ static final String PERMISSION_SONITALK_L0 = "at.ac.fhstp.permission_all_ultrasonic_communication";
    /*package-private*/ static final int PERMISSION_SONITALK_L0_REQUEST_CODE = 1;
    /*package-private*/ static final int PERMISSION_LEVEL_1_CODE = 101;
    /*package-private*/ static final int PERMISSION_LEVEL_2_CODE = 102;

    public static final Parcelable.Creator<SoniTalkPermissionManager> CREATOR
            = new Parcelable.Creator<SoniTalkPermissionManager>() {
        public SoniTalkPermissionManager createFromParcel(Parcel in) {
            return new SoniTalkPermissionManager(in);
        }

        public SoniTalkPermissionManager[] newArray(int size) {
            return new SoniTalkPermissionManager[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(permissionLevel.get());
        out.writeInt(currentRequestGranted ? 1 : 0);
        out.writeInt(currentRequestAnswered ? 1 : 0);
        out.writeInt(sessionPermissionGranted ? 1 : 0);
        out.writeParcelable(sdkListener, 0);
    }

    /**
     * Called internally by SoniTalkContext constructor.
     * @param sdkListener Listener for the SoniTalk callbacks.
     */
    public SoniTalkPermissionManager(ResultReceiver sdkListener) {
        //Log.d(TAG, "SoniTalkPermissionManager listener constructor");
        currentRequestGranted = false;
        currentRequestAnswered = false;
        sessionPermissionGranted = false;
        this.sdkListener = sdkListener;
    }

    private SoniTalkPermissionManager(Parcel in) {
        //Log.d(TAG, "SoniTalkPermissionManager Parcel constructor");
        permissionLevel.set(in.readInt());
        currentRequestGranted = in.readInt() == 1;
        currentRequestAnswered = in.readInt() == 1;
        sessionPermissionGranted = in.readInt() == 1;
        sdkListener = in.readParcelable(SoniTalkPermissionsResultReceiver.class.getClassLoader());
        // Check if it is needed to call this again from here (Activity destroyed recreated, e.g. after an orientation change)
        //permissionManager.checkPermissionLevelIsSet(context);
    }

    /**
     * Callback from the PermissionRequestDialogActivity. Sets currentRequestGranted to true which
     * will result in ON_REQUEST_GRANTED callback to be executed.
     * @param dialog Reference to the dialog in which the permission was asked.
     */
    @Override
    public void onGrantClick(DialogFragment dialog) {
        //Log.d(TAG, "onGrantClick");
        sdkListener.send(SoniTalkContext.ON_REQUEST_GRANTED, getRequestCodeBundle(dialog.requireContext()));
        FragmentActivity activity = dialog.requireActivity();
        SharedPreferences sp = activity.getSharedPreferences(activity.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
        sp.edit().remove(dialog.requireContext().getString(R.string.bundleRequestCode_key)).apply();
        synchronized (SoniTalkPermissionManager.class) {
            // Note: This object is different from the one being awaken. Hence Shared Preferences
            currentRequestGranted = true;
            currentRequestAnswered = true;

            SharedPreferences.Editor ed = sp.edit();
            ed.putBoolean(activity.getString(R.string.currentRequestGranted_key), currentRequestGranted);
            ed.putBoolean(activity.getString(R.string.currentRequestAnswered_key), currentRequestAnswered);
            ed.apply();

            //Log.d(TAG, "onGrantClick notify to wake up");
            // Will wake up in the checkPermissionGranted method.
            SoniTalkPermissionManager.class.notifyAll();
        }
    }

    /**
     * Callback from the PermissionRequestDialogActivity. Sets currentRequestGranted to false which
     * will result in ON_REQUEST_DENIED callback to be executed.
     * @param dialog Reference to the dialog in which the permission was asked.
     */
    @Override
    public void onDenyClick(DialogFragment dialog) {
        //Log.d(TAG, "onDenyClick");
        sdkListener.send(SoniTalkContext.ON_REQUEST_DENIED, getRequestCodeBundle(dialog.requireContext()));

        FragmentActivity activity = dialog.requireActivity();
        SharedPreferences sp = activity.getSharedPreferences(activity.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
        sp.edit().remove(dialog.requireContext().getString(R.string.bundleRequestCode_key)).apply();
        synchronized (SoniTalkPermissionManager.class) {
            currentRequestGranted = false;
            currentRequestAnswered = true;

            SharedPreferences.Editor ed = sp.edit();
            ed.putBoolean(activity.getString(R.string.currentRequestGranted_key), currentRequestGranted);
            ed.putBoolean(activity.getString(R.string.currentRequestAnswered_key), currentRequestAnswered);
            ed.apply();

            //Log.d(TAG, "onDenyClick notify to wake up");
            // Will wake up in the checkPermissionGranted method.
            SoniTalkPermissionManager.class.notifyAll();
        }
    }

    /**
     * Callback from the PermissionRequestDialogActivity. Resets permission level, so the user will
     * be asked again to select a privacy level.
     * @param dialog Reference to the dialog in which the permission was asked.
     */
    @Override
    public void onChangeSettingsClick(DialogFragment dialog) {
        // Resets permission level, the user will be asked again
        //Log.d(TAG, "onChangeSettingsClick");
        synchronized (SoniTalkPermissionManager.class) {
            currentRequestGranted = false;
            currentRequestAnswered = true;

            FragmentActivity activity = dialog.requireActivity();
            SharedPreferences sp = activity.getSharedPreferences(activity.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            ed.putBoolean(activity.getString(R.string.currentRequestGranted_key), currentRequestGranted);
            ed.putBoolean(activity.getString(R.string.currentRequestAnswered_key), currentRequestAnswered);
            ed.putInt(activity.getString(R.string.permission_level_key), -1);
            ed.apply();

            //Log.d(TAG, "onChangeSettingsClick notify to wake up");
            // Will wake up in the checkPermissionGranted method.
            SoniTalkPermissionManager.class.notifyAll();
        }
    }

    /**
     * Callback from the PermissionLevelDialogActivity. Sets permissionLevel to L0 which
     * will result in the Android permission dialog to be called.
     * @param dialog Reference to the dialog in which the permission was asked.
     */
    @Override
    public void onL0Click(DialogFragment dialog) {
        //Log.d(TAG, "Entering onL0Click");
        FragmentActivity activity = dialog.requireActivity();
        permissionLevel.set(-1);
        SharedPreferences sp = activity.getSharedPreferences(activity.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
        sp.edit().putInt(activity.getString(R.string.permission_level_key), permissionLevel.get()).apply();

        sessionPermissionGranted = false;
        if (hasPermissions(activity, PERMISSION_SONITALK_L0)) {
            // Proceed, we have the permission already
            //sdkListener.send(SoniTalkContext.ON_PERMISSION_LEVEL_WAS_SET, null);
            //Log.d(TAG, "onL0Click ON_REQUEST_GRANTED_REQUEST");


            synchronized (PermissionLevelDialogActivity.PermissionLevelDialogFragment.class) {
                SharedPreferences.Editor ed = sp.edit();
                ed.putBoolean(activity.getString(R.string.permissionLevelWasSet_key), true);
                ed.putBoolean(activity.getString(R.string.permissionLevelWasAnswered_key), true);
                ed.apply();

                //Log.d(TAG, "onL0Click and hasPermission already, notify to wake up");
                // Will wake up in the checkPermissionLevelIsSet method.
                PermissionLevelDialogActivity.PermissionLevelDialogFragment.class.notifyAll();
            }
            activity.finish(); // There should not be more dialogs, we can close the permission level activity
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //Log.d(TAG, "onL0Click request Android permission");
            showAndroidPermissionDialog(activity);
        }
        else {
            // Should not happen, (if Android < M) permission is granted already.
        }
    }

    /**
     * Asks Android framework to request the permission for L0 (Allow Always communication via sound).
     * If showing a rationale is needed, will result in ON_SHOULD_SHOW_RATIONALE_FOR_ALLOW_ALWAYS
     * callback to be executed.
     * @param activity Activity in which the Android permission will be asked.
     */
    /*package-private*/ void showAndroidPermissionDialog(Activity activity) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                PERMISSION_SONITALK_L0)) {
            // Should the developer show the rationale in onRequestDenied ? (or should they provide this rationale text ?!)

            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.

            sdkListener.send(SoniTalkContext.ON_SHOULD_SHOW_RATIONALE_FOR_ALLOW_ALWAYS, null);

            ActivityCompat.requestPermissions(activity,
                    new String[]{PERMISSION_SONITALK_L0}, PERMISSION_SONITALK_L0_REQUEST_CODE);
        } else {
            // No explanation needed; request the permission
            ActivityCompat.requestPermissions(activity,
                    new String[]{PERMISSION_SONITALK_L0}, PERMISSION_SONITALK_L0_REQUEST_CODE);
        }
    }

    /**
     * Callback from the PermissionLevelDialogActivity. Sets permissionLevel to L1 which
     * will result in the L1 permission to be asked via PermissionRequestDialogActivity.
     * @param dialog Reference to the dialog in which the permission was asked.
     */
    @Override
    public void onL1Click(DialogFragment dialog) {
        //Log.d(TAG, "Entering onL1Click");
        permissionLevel.set(1);
        FragmentActivity activity = dialog.requireActivity();
        SharedPreferences sp = activity.getSharedPreferences(activity.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
        sp.edit().putInt(activity.getString(R.string.permission_level_key), permissionLevel.get()).apply();

        sessionPermissionGranted = true;

        synchronized (PermissionLevelDialogActivity.PermissionLevelDialogFragment.class) {
            SharedPreferences.Editor ed = sp.edit();
            ed.putBoolean(activity.getString(R.string.permissionLevelWasSet_key), true);
            ed.putBoolean(activity.getString(R.string.permissionLevelWasAnswered_key), true);
            ed.apply();

            //Log.d(TAG, "onL1Click, notify to wake up");
            // Will wake up in the checkPermissionLevelIsSet method.
            PermissionLevelDialogActivity.PermissionLevelDialogFragment.class.notifyAll();
        }
        activity.finish(); // There should not be more dialogs, we can close the permission level activity
    }

    /**
     * Callback from the PermissionLevelDialogActivity. Sets permissionLevel to L2 which
     * will result in the L2 permission to be asked via PermissionRequestDialogActivity.
     * @param dialog Reference to the dialog in which the permission was asked.
     */
    @Override
    public void onL2Click(DialogFragment dialog) {
        //Log.d(TAG, "Entering onL2Click");
        permissionLevel.set(2);
        FragmentActivity activity = dialog.requireActivity();
        SharedPreferences sp = activity.getSharedPreferences(activity.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
        sp.edit().putInt(activity.getString(R.string.permission_level_key), permissionLevel.get()).apply();

        sessionPermissionGranted = false;

        synchronized (PermissionLevelDialogActivity.PermissionLevelDialogFragment.class) {
            SharedPreferences.Editor ed = sp.edit();
            ed.putBoolean(activity.getString(R.string.permissionLevelWasSet_key), true);
            ed.putBoolean(activity.getString(R.string.permissionLevelWasAnswered_key), true);
            ed.apply();

            //Log.d(TAG, "onL2Click, notify to wake up");
            // Will wake up in the checkPermissionLevelIsSet method.
            PermissionLevelDialogActivity.PermissionLevelDialogFragment.class.notifyAll();
        }
        activity.finish(); // There should not be more dialogs, we can close the permission level activity
    }

    /**
     * Callback from the PermissionLevelDialogActivity. Resets permissionLevel and executes the
     * ON_PERMISSION_LEVEL_DECLINED callback.
     * @param dialog Reference to the dialog in which the permission was asked.
     */
    @Override
    public void onDeclineClick(DialogFragment dialog) {
        //Log.d(TAG, "Entering onDeclineClick");
        permissionLevel.set(-1);
        FragmentActivity activity = dialog.requireActivity();
        SharedPreferences sp = activity.getSharedPreferences(activity.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
        sp.edit().putInt(activity.getString(R.string.permission_level_key), permissionLevel.get()).apply();

        sdkListener.send(SoniTalkContext.ON_PERMISSION_LEVEL_DECLINED, getRequestCodeBundle(activity));
        sp.edit().remove(activity.getString(R.string.bundleRequestCode_key)).apply();

        synchronized (PermissionLevelDialogActivity.PermissionLevelDialogFragment.class) {
            SharedPreferences.Editor ed = sp.edit();
            ed.putBoolean(activity.getString(R.string.permissionLevelWasSet_key), false);
            ed.putBoolean(activity.getString(R.string.permissionLevelWasAnswered_key), true);
            ed.apply();

            //Log.d(TAG, "onDeclineClick, notify to wake up");
            // Will wake up in the checkPermissionLevelIsSet method.
            PermissionLevelDialogActivity.PermissionLevelDialogFragment.class.notifyAll();
        }
        activity.finish(); // There should not be more dialogs, we can close the permission level activity
    }

    /**
     * Checks if the permission level was set and, if it is not, ask for it synchronously via
     * PermissionLevelDialogActivity. After the user answered the dialog, this method can return true
     * if the permission level was set, or false if it was declined.
     * @param context Context used to access app SharedPreferences and start the dialog activities.
     * @return true if the permission level is set
     */
    /*package-private*/boolean checkPermissionLevelIsSet(Context context) {
        //Log.d(TAG, "Entering checkPermissionLevelIsSet");
        // The L0 permission can be changed only if it is handled by the Android permission system
        SharedPreferences sp = context.getSharedPreferences(context.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
        if (hasPermissions(context, PERMISSION_SONITALK_L0)) {
            //Log.d(TAG, "Android permission already granted, should callback developer code");
            //permissionLevel = 0; DO NOT STORE THE PERMISSION LEVEL FOR L0 !!!
            permissionLevel.set(-1);
            SharedPreferences.Editor ed = sp.edit();
            ed.putInt(context.getString(R.string.permission_level_key), permissionLevel.get());
            ed.apply();
            //sdkListener.send(SoniTalkContext.ON_PERMISSION_LEVEL_WAS_SET, null); // remove this callback ?
            return true;
        }
        else {
            permissionLevel.set(sp.getInt(context.getString(R.string.permission_level_key), -1));

            // if level 0 or not set yet, we ask the permission level (allows users to change from level 0)
            if (permissionLevel.get() == -1 || permissionLevel.get() == 0) {
                permissionLevel.set(-1); //DO NOT STORE THE PERMISSION LEVEL FOR L0 !!!
                // First time this app uses SoniTalk (or the user removed the permission because they want to change the permission level ?)
                //Log.d(TAG, "Asking permission level (calling dialog activity), will block and wait for answer from dialog activity");

                boolean permissionLevelWasSet = false;
                boolean permissionLevelWasAnswered = false;
                SharedPreferences.Editor ed = sp.edit();
                ed.putBoolean(context.getString(R.string.permissionLevelWasSet_key), permissionLevelWasSet);
                ed.putBoolean(context.getString(R.string.permissionLevelWasAnswered_key), permissionLevelWasAnswered);
                ed.apply();

                // Blocking system needed because of the "change settings" option (and request dialog being synchronous)
                synchronized (PermissionLevelDialogActivity.PermissionLevelDialogFragment.class) {
                    context.startActivity(getPermissionLevelPopupIntent(context));
                    try {
                        while (!permissionLevelWasAnswered) {
                            //Log.d(TAG, "checkPermissionLevelIsSet wait, permissionLevelWasAnswered: " + permissionLevelWasAnswered);
                            // Will be awaken after the user chose a privacy level.
                            PermissionLevelDialogActivity.PermissionLevelDialogFragment.class.wait();

                            // We got awaken, let's check if the boolean were changed in the dialog:
                            permissionLevelWasSet = sp.getBoolean(context.getString(R.string.permissionLevelWasSet_key), permissionLevelWasSet);
                            permissionLevelWasAnswered = sp.getBoolean(context.getString(R.string.permissionLevelWasAnswered_key), permissionLevelWasAnswered);
                            permissionLevel.set(sp.getInt(context.getString(R.string.permission_level_key), -1));
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        // decline callback ?
                    }
                    //Log.d(TAG, "checkPermissionLevelIsSet after permission level dialog, returns " + permissionLevelWasSet);
                }

                return permissionLevelWasSet;
            }
            else if (permissionLevel.get() == 1) {
                //Log.d(TAG, "L1, should callback developer code");
                // proceed WITH further asking via internal permission system
                return true;
            }
            else if (permissionLevel.get() == 2) {
                //Log.d(TAG, "L2, should callback developer code");
                // proceed WITH further asking via internal permission system
                return true;
            }
            else {
                return false; // Wrong data saved ? (permissionLevel different from expected values)
            }
        }
    }

    /**
     * Synchronously checks if the user allows this request. If the permission is not yet granted,
     * dialog activities will potentially ask the user for the privacy level they want, and if they
     * do not decline(d), their consent to execute this request.
     * @param context Context used to access app SharedPreferences and start the dialog activities.
     * @param requestCode Integer passed by the developer to be able to identify which request was
     *                    granted/denied
     * @return true if the request was allowed by the user (potentially implicitly, if L0 or L1 was
     *         granted earlier).
     */
    /*package-private*/boolean checkSelfPermission(Context context, int requestCode) {//}, String permission) {
        SharedPreferences sp = context.getSharedPreferences(context.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putInt(context.getString(R.string.currentRequestAction_key), requestCode);
        ed.apply();

        //Log.d(TAG, "Entering checkSelfPermission");
        if (checkPermissionLevelIsSet(context)) {
            return checkPermissionGranted(context);

        }
        else { // Permission level request was declined
            // Nothing to do as the callback for level declined was already sent.
            return false;
        }
    }

    /**
     * Synchronously checks if the user allows this request. If the permission is not yet granted,
     * a dialog activity will ask the user for their consent.
     * @param context Context used to access app SharedPreferences and start the dialog activities.
     * @return true if the request was allowed by the user (potentially implicitly, if L0 or L1 was
     *         granted earlier).
     */
    private boolean checkPermissionGranted(Context context) {
        SharedPreferences sp = context.getSharedPreferences(context.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        if (permissionLevel.get() == 0 || permissionLevel.get() == -1) { // Note: We should not store 0
            if (hasPermissions(context, PERMISSION_SONITALK_L0)) {
                //Log.d(TAG, "selfPermission returns true: L0");
                //permissionLevel = 0; DO NOT STORE THE PERMISSION LEVEL FOR L0 !!!
                permissionLevel.set(-1);
                //sdkListener.send(SoniTalkContext.ON_PERMISSION_LEVEL_WAS_SET, null); // remove this callback ?
                sdkListener.send(SoniTalkContext.ON_REQUEST_GRANTED, getRequestCodeBundle(context));
                ed.remove(context.getString(R.string.bundleRequestCode_key)).apply();
                return true;
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ////Log.d(TAG, "selfPermission request Android permission");

                // Callback L0 denied
                sdkListener.send(SoniTalkContext.ON_REQUEST_L0_DENIED, getRequestCodeBundle(context));
                return false;
            }
            else {
                // Should not happen, if (Android < M) permission is granted already.
                return false;
            }
        }
        else if (permissionLevel.get() == 1) {
            if (sessionPermissionGranted) { //No need to get it from sharedpref as it is based on a value that was in Sharedpref.
                //Log.d(TAG, "selfPermission returns true: L1 and sessionPermissionGranted");
                sdkListener.send(SoniTalkContext.ON_REQUEST_GRANTED, getRequestCodeBundle(context));
                ed.remove(context.getString(R.string.bundleRequestCode_key)).apply();
                return true;
            }
            else { // Context was destroyed, it is a new session.

                //Log.d(TAG, "Entering checkPermission for L1, permission not granted yet, will block and wait for answer from dialog activity");
                synchronized (SoniTalkPermissionManager.class) {
                    currentRequestGranted = false;
                    currentRequestAnswered = false;
                    ed.putBoolean(context.getString(R.string.currentRequestGranted_key), currentRequestGranted);
                    ed.putBoolean(context.getString(R.string.currentRequestAnswered_key), currentRequestAnswered);
                    ed.apply();
                    context.startActivity(getPermissionRequestPopupIntent(context, PERMISSION_LEVEL_1_CODE));
                    try {
                        while (!currentRequestAnswered) {
                            //Log.d(TAG, "checkSelfPermission L1 wait, currentRequestAnswered: " + currentRequestAnswered);
                            // Will be awaken after the user answered the request (Grant, Deny, or Change settings).
                            SoniTalkPermissionManager.class.wait();

                            // We got awaken, let's check if the boolean were changed in the dialog:
                            currentRequestGranted = sp.getBoolean(context.getString(R.string.currentRequestGranted_key), currentRequestGranted);
                            currentRequestAnswered = sp.getBoolean(context.getString(R.string.currentRequestAnswered_key), currentRequestAnswered);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        // onRequestDeniedCallback ?
                    }

                    if (sp.getInt(context.getString(R.string.permission_level_key), -1) == -1) {
                        //Log.d(TAG, "selfPermission after L1 dialog, changeSettings, will call checkSelfPermission");
                        int requestCode = getCurrentRequestCode(context);
                        return checkSelfPermission(context, requestCode);
                    }
                    else {
                        sessionPermissionGranted = currentRequestGranted;
                        //Log.d(TAG, "selfPermission after L1 dialog, returns " + currentRequestGranted);
                        return currentRequestGranted;
                    }
                }
            }
        }
        else if (permissionLevel.get() == 2) {
            // L2, ask the user
            //Log.d(TAG, "Entering checkPermission for L2, will block and wait for answer from dialog activity");
            synchronized (SoniTalkPermissionManager.class) {
                currentRequestGranted = false;
                currentRequestAnswered = false;
                ed.putBoolean(context.getString(R.string.currentRequestGranted_key), currentRequestGranted);
                ed.putBoolean(context.getString(R.string.currentRequestAnswered_key), currentRequestAnswered);
                ed.apply();
                context.startActivity(getPermissionRequestPopupIntent(context, PERMISSION_LEVEL_2_CODE));
                try {
                    while (!currentRequestAnswered) {
                        //Log.d(TAG, "checkSelfPermission L2 wait, currentRequestAnswered: " + currentRequestAnswered);
                        SoniTalkPermissionManager.class.wait();

                        // We got awaken, let's check if the boolean were changed in the dialog:
                        currentRequestGranted = sp.getBoolean(context.getString(R.string.currentRequestGranted_key), currentRequestGranted);
                        currentRequestAnswered = sp.getBoolean(context.getString(R.string.currentRequestAnswered_key), currentRequestAnswered);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    // onRequestDeniedCallback ?
                }

                if (sp.getInt(context.getString(R.string.permission_level_key), -1) == -1) {
                    //Log.d(TAG, "selfPermission after L2 dialog, changeSettings, will call checkSelfPermission");
                    int requestCode = getCurrentRequestCode(context);
                    return checkSelfPermission(context, requestCode);
                }
                else {
                    //Log.d(TAG, "selfPermission after L2 dialog, returns " + currentRequestGranted);
                    return currentRequestGranted;
                }
            }
        }
        else  {
            return false; // Wrong data saved ? (permissionLevel different from expected values)
        }
    }

    private Intent getPermissionLevelPopupIntent(Context context) {
        Intent popupIntent = new Intent(context, PermissionLevelDialogActivity.class);
        popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle extras = new Bundle();
        extras.putParcelable(PermissionLevelDialogActivity.EXTRA_PERMISSION_LEVEL_LISTENER, this);
        popupIntent.putExtras(extras);
        return popupIntent;
    }

    private Intent getPermissionRequestPopupIntent(Context context, int permissionLevelCode) {
        Intent popupIntent = new Intent(context, PermissionRequestDialogActivity.class);
        popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle extras = new Bundle();
        extras.putInt(PermissionRequestDialogActivity.EXTRA_PERMISSION_LEVEL_CODE, permissionLevelCode);
        extras.putParcelable(PermissionRequestDialogActivity.EXTRA_PERMISSION_REQUEST_LISTENER, this);
        popupIntent.putExtras(extras);
        return popupIntent;
    }

    private static boolean hasPermissions(@NonNull Context context, @NonNull String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M/* Wasn't this a security issue ?! && context != null && permissions != null*/) {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Signals that a send job is finished. Will trigger the ON_SEND_JOB_FINISHED callback.
     * @param context Context used to access app SharedPreferences and start the dialog activities.
     * @param requestCode Integer passed by the developer to be able to identify a "send job".
     */
    /*package-private*/ void sendJobFinished(Context context, int requestCode){
        Bundle resultData = new Bundle(1);
        resultData.putInt(context.getString(R.string.bundleRequestCode_key), requestCode);
        sdkListener.send(SoniTalkContext.ON_SEND_JOB_FINISHED, resultData);
    }

    private int getCurrentRequestCode(Context context){
        SharedPreferences sp = context.getSharedPreferences(context.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
        int requestCode = sp.getInt(context.getString(R.string.currentRequestAction_key), 0);
        return requestCode;
    }

    private Bundle getRequestCodeBundle(Context context){
        int requestCode = getCurrentRequestCode(context);
        Bundle resultData = new Bundle(1);
        resultData.putInt(context.getString(R.string.bundleRequestCode_key),requestCode);
        return resultData;
    }

    /*package-private*/boolean hasSessionPermissionOrL0(Context context) {
        return (sessionPermissionGranted || hasPermissions(context, PERMISSION_SONITALK_L0));
    }
}
