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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

/**
 * Activity for the different permission levels. Consists of a fragment with
 * the alert dialog and a listener.
 */
public class PermissionLevelDialogActivity extends AppCompatActivity {
    private static final String TAG_PermissionLevelDialogFragment = "TAG_PermissionLevelDialogFragment";
    /*package-private*/static final String EXTRA_PERMISSION_LEVEL_LISTENER = "at.ac.fhstp.sonitalk.EXTRA_PERMISSION_LEVEL_LISTENER";

    // Can we use only this listener ? Remove the one in Fragment ?
    PermissionLevelDialogListener internalListenerActivity;

    /* The Intent that calls this activity must contain a EXTRA_PERMISSION_LEVEL_LISTENER extra that
     * implements this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    /*package-private*/ interface PermissionLevelDialogListener extends Parcelable {
        void onL0Click(DialogFragment dialog);
        void onL1Click(DialogFragment dialog);
        void onL2Click(DialogFragment dialog);
        void onDeclineClick(DialogFragment dialog);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_permission_level_dialog);
        this.internalListenerActivity = (PermissionLevelDialogListener) getIntent().getParcelableExtra(EXTRA_PERMISSION_LEVEL_LISTENER);
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_PERMISSION_LEVEL_LISTENER, internalListenerActivity);
        PermissionLevelDialogActivity.PermissionLevelDialogFragment dialog = PermissionLevelDialogFragment.newInstance(bundle);
        dialog.setCancelable(false); // Must be done on the DialogFragment, not on the Dialog.
        dialog.show(getSupportFragmentManager(), TAG_PermissionLevelDialogFragment);
    }

    /**
     * Contains the alert dialog for the permission levels.
     */
    public static class PermissionLevelDialogFragment extends DialogFragment {
        // Use this instance of the interface to deliver action events
        PermissionLevelDialogListener internalListener;

        public static PermissionLevelDialogFragment newInstance(Bundle bundle) {
            PermissionLevelDialogFragment f = new PermissionLevelDialogFragment();
            f.setArguments(bundle);
            return f;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            this.internalListener = (PermissionLevelDialogListener) getArguments().getParcelable(EXTRA_PERMISSION_LEVEL_LISTENER);
            if (internalListener == null) {
                throw new IllegalStateException("No PermissionLevelDialogListener passed to the PermissionLevelDialogFragment, please use the EXTRA_PERMISSION_LEVEL_LISTENER.");
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final FragmentActivity currentActivity = getActivity();
            String app_name = currentActivity.getApplicationContext().getApplicationInfo().loadLabel(currentActivity.getPackageManager()).toString();

            String permissionLevelQuestion = String.format(currentActivity.getString(R.string.permission_level_question), app_name);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(permissionLevelQuestion)
                    .setItems(R.array.permission_level_texts,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if(which==0){
                                        //Log.d("permission level", "l0");
                                        internalListener.onL0Click(PermissionLevelDialogFragment.this);
                                    }else if(which==1){
                                        //Log.d("permission level", "l1");
                                        internalListener.onL1Click(PermissionLevelDialogFragment.this);
                                    }else if(which==2){
                                        //Log.d("permission level", "l2");
                                        internalListener.onL2Click(PermissionLevelDialogFragment.this);
                                    }else if(which==3){
                                        //Log.d("permission level", "decline");
                                        dialog.cancel();
                                    }
                                }
                            });

            return builder.create();
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            //getActivity().finish(); // Make sure this does not lead to problems with the activity being destroyed when we need it to request L0 ! We could call finish somewhere else.
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);

            internalListener.onDeclineClick(PermissionLevelDialogFragment.this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (internalListenerActivity != null) {
            //Log.d("onRequestPermResult", "internalListenerActivity is NOT null");
            if (requestCode == SoniTalkPermissionManager.PERMISSION_SONITALK_L0_REQUEST_CODE) {
                if (SoniTalkPermissionManager.PERMISSION_SONITALK_L0.equals(permissions[0])) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        //Log.d("onRequestPermResult", "Granted");

                        synchronized (PermissionLevelDialogActivity.PermissionLevelDialogFragment.class) {
                            SharedPreferences sp = this.getSharedPreferences(this.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
                            SharedPreferences.Editor ed = sp.edit();
                            ed.putBoolean(this.getString(R.string.permissionLevelWasSet_key), true);
                            ed.putBoolean(this.getString(R.string.permissionLevelWasAnswered_key), true);
                            ed.apply();

                            //Log.d("onRequestPermResult", "L0 granted, notify to wake up");
                            PermissionLevelDialogActivity.PermissionLevelDialogFragment.class.notifyAll();

                            finish(); // Permissions are requested in the SoniTalkPermissionManager class, on this Activity Context
                            return;
                        }
                    }
                    else {
                        //Log.d("onRequestPermResult", "Denied");

                        // Default case: false will be returned below
                    }
                }
            }
        }
        else {
            Log.e("onRequestPermResult", "internalListenerActivity is null");
        }

        synchronized (PermissionLevelDialogActivity.PermissionLevelDialogFragment.class) {
            SharedPreferences sp = this.getSharedPreferences(this.getString(R.string.context_preference_key), Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            ed.putBoolean(this.getString(R.string.permissionLevelWasSet_key), true);
            ed.putBoolean(this.getString(R.string.permissionLevelWasAnswered_key), true);
            ed.apply();

            //Log.d("onRequestPermResult", "L0 denied, notify to wake up");
            PermissionLevelDialogActivity.PermissionLevelDialogFragment.class.notifyAll();
        }

        finish(); // Permissions are requested in the SoniTalkPermissionManager class, on this Activity Context
    }
}
