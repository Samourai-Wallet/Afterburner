package com.samourai.wallet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.prng.PRNGFixes;
import com.samourai.wallet.service.WebSocketService;
import com.samourai.wallet.util.AppUtil;
import com.samourai.wallet.util.ConnectivityStatus;
import com.samourai.wallet.util.ExchangeRateFactory;
import com.samourai.wallet.util.WebUtil;

import com.samourai.afterburner.R;

public class MainActivity2 extends Activity {

    public static final String ACTION_RESTART = "com.samourai.wallet.MainActivity2.RESTART_SERVICE";

    protected BroadcastReceiver receiver_restart = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if(ACTION_RESTART.equals(intent.getAction())) {

                if(AppUtil.getInstance(MainActivity2.this.getApplicationContext()).isServiceRunning(WebSocketService.class)) {
                    stopService(new Intent(MainActivity2.this.getApplicationContext(), WebSocketService.class));
                }
                startService(new Intent(MainActivity2.this.getApplicationContext(), WebSocketService.class));

            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Apply PRNG fixes for Android 4.1
        if(!AppUtil.getInstance(MainActivity2.this).isPRNG_FIXED())    {
            PRNGFixes.apply();
            AppUtil.getInstance(MainActivity2.this).setPRNG_FIXED(true);
        }

        if(!ConnectivityStatus.hasConnectivity(MainActivity2.this))  {

            new AlertDialog.Builder(MainActivity2.this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.no_internet)
                    .setCancelable(false)
                    .setIcon(R.drawable.ic_launcher)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            AppUtil.getInstance(MainActivity2.this).restartApp();
                        }
                    }).show();

        }
        else  {

            exchangeRateThread();

            doAppInit();

        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        AppUtil.getInstance(MainActivity2.this).setIsInForeground(true);

        AppUtil.getInstance(MainActivity2.this).deleteQR();
        AppUtil.getInstance(MainActivity2.this).deleteBackup();

        IntentFilter filter_restart = new IntentFilter(ACTION_RESTART);
        LocalBroadcastManager.getInstance(MainActivity2.this).registerReceiver(receiver_restart, filter_restart);

    }

    @Override
    protected void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(MainActivity2.this).unregisterReceiver(receiver_restart);

        AppUtil.getInstance(MainActivity2.this).setIsInForeground(false);
    }

    @Override
    protected void onDestroy() {

        AppUtil.getInstance(MainActivity2.this).deleteQR();
        AppUtil.getInstance(MainActivity2.this).deleteBackup();

        super.onDestroy();
    }

    private void exchangeRateThread() {

        final Handler handler = new Handler();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                String response = null;
                try {
                    response = WebUtil.getInstance(null).getURL(WebUtil.LBC_EXCHANGE_URL);
                    ExchangeRateFactory.getInstance(MainActivity2.this).setDataLBC(response);
                    ExchangeRateFactory.getInstance(MainActivity2.this).parseLBC();
                }
                catch(Exception e) {
                    e.printStackTrace();
                }

                response = null;
                try {
                    response = WebUtil.getInstance(null).getURL(WebUtil.BTCe_EXCHANGE_URL + "btc_usd");
                    ExchangeRateFactory.getInstance(MainActivity2.this).setDataBTCe(response);
                    ExchangeRateFactory.getInstance(MainActivity2.this).parseBTCe();
                }
                catch(Exception e) {
                    e.printStackTrace();
                }

                response = null;
                try {
                    response = WebUtil.getInstance(null).getURL(WebUtil.BTCe_EXCHANGE_URL + "btc_eur");
                    ExchangeRateFactory.getInstance(MainActivity2.this).setDataBTCe(response);
                    ExchangeRateFactory.getInstance(MainActivity2.this).parseBTCe();
                }
                catch(Exception e) {
                    e.printStackTrace();
                }

                response = null;
                try {
                    response = WebUtil.getInstance(null).getURL(WebUtil.BTCe_EXCHANGE_URL + "btc_rur");
                    ExchangeRateFactory.getInstance(MainActivity2.this).setDataBTCe(response);
                    ExchangeRateFactory.getInstance(MainActivity2.this).parseBTCe();
                }
                catch(Exception e) {
                    e.printStackTrace();
                }

                response = null;
                try {
                    response = WebUtil.getInstance(null).getURL(WebUtil.AVG_EXCHANGE_URL);
                    ExchangeRateFactory.getInstance(MainActivity2.this).setDataBTCAvg(response);
                    ExchangeRateFactory.getInstance(MainActivity2.this).parseBTCAvg();
                }
                catch(Exception e) {
                    e.printStackTrace();
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        ;
                    }
                });

                Looper.loop();

            }
        }).start();
    }

    private void doAppInit() {

        if(AccessFactory.getInstance(MainActivity2.this).getGUID().length() < 1 || !PayloadUtil.getInstance(MainActivity2.this).walletFileExists()) {
            AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);

            Intent intent = new Intent(MainActivity2.this, InitActivity.class);
            startActivity(intent);
        }
        else if(AccessFactory.getInstance(MainActivity2.this).isLoggedIn()) {

            Intent intent = new Intent(MainActivity2.this, BalanceActivity.class);
            intent.putExtra("notifTx", true);
            intent.putExtra("fetch", true);
            startActivity(intent);
        }
        else {
            AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);
            validatePIN();
        }

    }

    private void validatePIN()	{

        if(AccessFactory.getInstance(MainActivity2.this).isLoggedIn())	{
            return;
        }

        AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);

        Intent intent = new Intent(MainActivity2.this, PinEntryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

    }

}
