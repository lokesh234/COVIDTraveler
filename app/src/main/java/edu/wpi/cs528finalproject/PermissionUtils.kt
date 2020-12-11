package edu.wpi.cs528finalproject

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment

// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

object PermissionRequestCodes {
    const val enableLocationUpdatesService = 0
    const val enableMapView = 1
    const val requestCurrentPlace = 2
    const val enableCamera = 3
}

object DeferredPermissions {
    var deferredMap = mutableMapOf<Int, Boolean>()
    init {
        deferredMap[PermissionRequestCodes.enableLocationUpdatesService] = false
        deferredMap[PermissionRequestCodes.enableMapView] = false
        deferredMap[PermissionRequestCodes.requestCurrentPlace] = false
    }
}

/**
 * Utility class for access to runtime permissions.
 */
object PermissionUtils {
    /**
     * Requests the fine location permission. If a rationale with an additional explanation should
     * be shown to the user, displays a dialog that triggers the request.
     */
    fun requestPermission(
        activity: AppCompatActivity, requestId: Int,
        permission: String?, finishActivity: Boolean,
        @StringRes permissionRequestedResID: Int,
        @StringRes rationaleResID: Int
    ) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission!!)) {
            // Display a dialog with rationale.
            RationaleDialog.newInstance(
                permission, requestId, finishActivity,
                permissionRequestedResID, rationaleResID
            )
                .show(activity.supportFragmentManager, "dialog")
        } else {
            // Location permission has not been granted yet, request it.
            ActivityCompat.requestPermissions(activity, arrayOf<String?>(permission), requestId)
        }
    }

    /**
     * Checks if the result contains a [PackageManager.PERMISSION_GRANTED] result for a
     * permission from a runtime permissions request.
     *
     * @see androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
     */
    fun isPermissionGranted(
        grantPermissions: Array<out String>, grantResults: IntArray,
        permission: String
    ): Boolean {
        for (i in grantPermissions.indices) {
            if (permission == grantPermissions[i]) {
                return grantResults[i] == PackageManager.PERMISSION_GRANTED
            }
        }
        return false
    }

    /**
     * A dialog that displays a permission denied message.
     */
    class PermissionDeniedDialog private constructor(@param:StringRes private var permissionDeniedResourceID: Int) :
        DialogFragment() {
        private var finishActivity = false
        private var permissionRequiredResourceID = 0
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            finishActivity = requireArguments().getBoolean(ARGUMENT_FINISH_ACTIVITY)
            permissionRequiredResourceID = requireArguments().getInt(
                ARGUMENT_PERMISSION_REQUIRED_RESOURCE_ID
            )
            permissionDeniedResourceID = requireArguments().getInt(ARGUMENT_PERMISSION_DENIED_RESOURCE_ID)
            return AlertDialog.Builder(activity)
                .setMessage(permissionDeniedResourceID)
                .setPositiveButton(android.R.string.ok, null)
                .create()
        }

        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            if (finishActivity) {
                Toast.makeText(
                    activity, permissionRequiredResourceID,
                    Toast.LENGTH_SHORT
                ).show()
                requireActivity().finish()
            }
        }

        companion object {
            private const val ARGUMENT_FINISH_ACTIVITY = "finish"
            private const val ARGUMENT_PERMISSION_REQUIRED_RESOURCE_ID =
                "permissionRequiredResourceID"
            private const val ARGUMENT_PERMISSION_DENIED_RESOURCE_ID = "permissionDeniedResourceID"

            /**
             * Creates a new instance of this dialog and optionally finishes the calling Activity
             * when the 'Ok' button is clicked.
             */
            fun newInstance(
                finishActivity: Boolean,
                @StringRes permissionDeniedResID: Int,
                @StringRes permissionRequiredResID: Int
            ): PermissionDeniedDialog {
                val arguments = Bundle()
                arguments.putBoolean(ARGUMENT_FINISH_ACTIVITY, finishActivity)
                arguments.putInt(ARGUMENT_PERMISSION_DENIED_RESOURCE_ID, permissionDeniedResID)
                arguments.putInt(ARGUMENT_PERMISSION_REQUIRED_RESOURCE_ID, permissionRequiredResID)
                val dialog = PermissionDeniedDialog(permissionRequiredResID)
                dialog.arguments = arguments
                return dialog
            }
        }
    }

    /**
     * A dialog that explains the use of the location permission and requests the necessary
     * permission.
     *
     *
     * The activity should implement
     * [androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback]
     * to handle permit or denial of this permission request.
     */
    class RationaleDialog private constructor(@param:StringRes private var rationaleResourceID: Int) :
        DialogFragment() {
        private var finishActivity = false
        private var permissionRequiredResourceID = 0
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val arguments = arguments
            val permission = arguments!!.getString(ARGUMENT_PERMISSION)
            val requestCode = arguments.getInt(ARGUMENT_PERMISSION_REQUEST_CODE)
            finishActivity = arguments.getBoolean(ARGUMENT_FINISH_ACTIVITY)
            permissionRequiredResourceID =
                arguments.getInt(ARGUMENT_PERMISSION_REQUIRED_RESOURCE_ID)
            rationaleResourceID = arguments.getInt(ARGUMENT_PERMISSION_RATIONALE_RESOURCE_ID)
            return AlertDialog.Builder(activity)
                .setMessage(rationaleResourceID)
                .setPositiveButton(
                    android.R.string.ok
                ) { _, _ -> // After click on Ok, request the permission.
                    ActivityCompat.requestPermissions(
                        requireActivity(), arrayOf(permission),
                        requestCode
                    )
                    // Do not finish the Activity while requesting permission.
                    finishActivity = false
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }

        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            if (finishActivity) {
                Toast.makeText(
                    activity,
                    permissionRequiredResourceID,
                    Toast.LENGTH_SHORT
                )
                    .show()
                requireActivity().finish()
            }
        }

        companion object {
            private const val ARGUMENT_PERMISSION = "permission"
            private const val ARGUMENT_PERMISSION_REQUEST_CODE = "requestCode"
            private const val ARGUMENT_FINISH_ACTIVITY = "finish"
            private const val ARGUMENT_PERMISSION_REQUIRED_RESOURCE_ID =
                "permissionRequiredResourceID"
            private const val ARGUMENT_PERMISSION_RATIONALE_RESOURCE_ID = "rationaleResourceID"

            /**
             * Creates a new instance of a dialog displaying the rationale for the use of the
             * permission.
             *
             *
             * The permission is requested after clicking 'ok'.
             *
             * @param requestCode    Id of the request that is used to request the permission. It is
             * returned to the
             * [androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback].
             * @param finishActivity Whether the calling Activity should be finished if the dialog is
             * cancelled.
             */
            fun newInstance(
                permission: String?, requestCode: Int,
                finishActivity: Boolean,
                @StringRes permissionRequiredResID: Int,
                @StringRes rationaleResID: Int
            ): RationaleDialog {
                val arguments = Bundle()
                arguments.putString(ARGUMENT_PERMISSION, permission)
                arguments.putInt(ARGUMENT_PERMISSION_REQUEST_CODE, requestCode)
                arguments.putBoolean(ARGUMENT_FINISH_ACTIVITY, finishActivity)
                arguments.putInt(ARGUMENT_PERMISSION_REQUIRED_RESOURCE_ID, permissionRequiredResID)
                arguments.putInt(ARGUMENT_PERMISSION_RATIONALE_RESOURCE_ID, rationaleResID)
                val dialog = RationaleDialog(rationaleResID)
                dialog.arguments = arguments
                return dialog
            }
        }
    }
}