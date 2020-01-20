package tk.burdukowsky.caller;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private Button mStartServerButton;
    private Button mStopServerButton;
    private TextView mLog;
    private BroadcastReceiver mBroadcastReceiver;

    private final static int REQUEST_PERMISSION_READ_CALL_LOG = 1;
    public static boolean sIsOldAndroidVersion = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            requestReadCallLogPermission();
        } else {
            sIsOldAndroidVersion = true;
        }

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mLog.append(intent.getStringExtra(SimpleWebServer.MESSAGE).concat("\n"));
            }
        };

        mLog = (TextView) findViewById(R.id.log);
        mStartServerButton = (Button) findViewById(R.id.startServerButton);
        mStopServerButton = (Button) findViewById(R.id.stopServerButton);

        if (isServiceRunning(SimpleService.class)) {
            mStartServerButton.setEnabled(false);
            mStopServerButton.setEnabled(true);
        } else {
            mStartServerButton.setEnabled(true);
            mStopServerButton.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver((mBroadcastReceiver),
                new IntentFilter(SimpleWebServer.REQUEST)
        );
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onStop();
    }

    public void onStartSeverButtonClick(View view) {
        startService(new Intent(this, SimpleService.class));
        mStartServerButton.setEnabled(false);
        mStopServerButton.setEnabled(true);
    }

    public void onStopSeverButtonClick(View view) {
        stopService(new Intent(this, SimpleService.class));
        mStartServerButton.setEnabled(true);
        mStopServerButton.setEnabled(false);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service
                : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String permissions[],
            @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_READ_CALL_LOG:
                if (!(grantResults.length > 0 && grantResults[0]
                        == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.permission_needed),
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }
    /*
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            permissionCheck = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_CALL_LOG);
        }
     */

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void requestReadCallLogPermission() {
        int permissionCheck = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_CALL_LOG);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_CALL_LOG)) {
                showExplanation(getString(R.string.permission_needed),
                        getString(R.string.permission_rationale),
                        Manifest.permission.READ_CALL_LOG,
                        REQUEST_PERMISSION_READ_CALL_LOG);
            } else {
                requestPermission(Manifest.permission.READ_CALL_LOG,
                        REQUEST_PERMISSION_READ_CALL_LOG);
            }
        }
    }

    private void showExplanation(String title,
                                 String message,
                                 final String permission,
                                 final int permissionRequestCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        requestPermission(permission, permissionRequestCode);
                    }
                });
        builder.create().show();
    }

    private void requestPermission(String permissionName, int permissionRequestCode) {
        ActivityCompat.requestPermissions(this,
                new String[]{permissionName}, permissionRequestCode);
    }
}
