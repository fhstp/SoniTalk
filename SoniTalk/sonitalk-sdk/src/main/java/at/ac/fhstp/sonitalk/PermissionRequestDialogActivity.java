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
import android.content.DialogInterface;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

/**
 * Activity for the decision on one permission level. Consists of a fragment with
 * the alert dialog and a listener.
 */
public class PermissionRequestDialogActivity extends AppCompatActivity {
    private static final String TAG_PermissionRequestDialogFragment = "TAG_PermissionRequestDialogFragment";
    /*package-private*/static final String EXTRA_PERMISSION_REQUEST_LISTENER = "at.ac.fhstp.sonitalk.EXTRA_PERMISSION_REQUEST_LISTENER";
    /*package-private*/static final String EXTRA_PERMISSION_LEVEL_CODE = "at.ac.fhstp.sonitalk.EXTRA_PERMISSION_LEVEL_CODE";

    private int permission_level_code;

    /* The Intent that calls this activity must contain a EXTRA_PERMISSION_REQUEST_LISTENER extra that
     * implements this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    /*package-private*/ interface PermissionRequestDialogListener extends Parcelable {
        void onGrantClick(DialogFragment dialog);
        void onDenyClick(DialogFragment dialog);
        void onChangeSettingsClick(DialogFragment dialog);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieves the listener and transmit it to the dialog fragment for handling user input
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_PERMISSION_REQUEST_LISTENER, getIntent().getParcelableExtra(EXTRA_PERMISSION_REQUEST_LISTENER));
        bundle.putInt(EXTRA_PERMISSION_LEVEL_CODE, getIntent().getIntExtra(EXTRA_PERMISSION_LEVEL_CODE, SoniTalkPermissionManager.PERMISSION_LEVEL_1_CODE));
        PermissionRequestDialogActivity.PermissionRequestDialogFragment dialog = PermissionRequestDialogActivity.PermissionRequestDialogFragment.newInstance(bundle);
        dialog.setCancelable(false); // Must be done on the DialogFragment, not on the Dialog.
        dialog.show(getSupportFragmentManager(), TAG_PermissionRequestDialogFragment);
    }

    /**
     * Contains the alert dialog for the permission levels.
     */
    public static class PermissionRequestDialogFragment extends DialogFragment {
        // Deliver action events to the listener
        PermissionRequestDialogListener listener;
        int permission_level_code;

        public static PermissionRequestDialogFragment newInstance(Bundle bundle) {
            PermissionRequestDialogFragment f = new PermissionRequestDialogFragment();
            f.setArguments(bundle);
            return f;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            this.listener = (PermissionRequestDialogListener) getArguments().getParcelable(EXTRA_PERMISSION_REQUEST_LISTENER);
            if (listener == null) {
                throw new IllegalStateException("No PermissionRequestDialogListener passed to the PermissionRequestDialogFragment, please use the EXTRA_PERMISSION_REQUEST_LISTENER.");
            }
            this.permission_level_code = getArguments().getInt(EXTRA_PERMISSION_LEVEL_CODE);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            FragmentActivity currentActivity = getActivity();
            String app_name = currentActivity.getApplicationContext().getApplicationInfo().loadLabel(currentActivity.getPackageManager()).toString();

            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(currentActivity);
            builder.setTitle(currentActivity.getString(R.string.permission_request_title));
            String permissionRequestQuestion = null;
            if(permission_level_code==SoniTalkPermissionManager.PERMISSION_LEVEL_1_CODE) {
                permissionRequestQuestion = String.format(currentActivity.getString(R.string.permission_request_l1_question), app_name);
            }else if(permission_level_code==SoniTalkPermissionManager.PERMISSION_LEVEL_2_CODE){
                permissionRequestQuestion = String.format(currentActivity.getString(R.string.permission_request_l2_question), app_name);

            }
            builder.setMessage(permissionRequestQuestion) // Retrieve sending / receiving action type via Intent extra ?
                    .setPositiveButton(currentActivity.getString(R.string.permission_request_positive_text), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //Log.d("permission request", "Positive");
                        listener.onGrantClick(PermissionRequestDialogFragment.this);
                        }
                    })
                    .setNegativeButton(currentActivity.getString(R.string.permission_request_negative_text), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //Log.d("permission request", "Negative");
                            dialog.cancel(); // Will call the deny callback
                        }
                    })
                    .setNeutralButton(currentActivity.getString(R.string.permission_request_change_text), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //Log.d("permission request", "Neutral (change settings)");
                            listener.onChangeSettingsClick(PermissionRequestDialogFragment.this);
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            FragmentActivity activity = getActivity();
            if (activity != null)
                activity.finish(); //We do not ask an Android permission, should be fine to finish the activity.
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            listener.onDenyClick(PermissionRequestDialogFragment.this);
            //Example exception to throw: throw new SecurityException("SoniTalkDecoder requires a permission from SoniTalkContext. Use SoniTalkContext.checkSelfPermission() to make sure that you have the right permission.");
        }
    }
}
