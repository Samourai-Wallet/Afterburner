package com.samourai.wallet;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import com.samourai.afterburner.R;

import org.bitcoinj.crypto.MnemonicException;

import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.api.APIFactory;
import com.samourai.wallet.api.Tx;
import com.samourai.wallet.crypto.DecryptionException;
import com.samourai.wallet.hd.HD_WalletFactory;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.send.FeeUtil;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.samourai.wallet.send.SendFactory;
import com.samourai.wallet.send.SuggestedFee;
import com.samourai.wallet.send.UTXO;
import com.samourai.wallet.send.PushTx;
import com.samourai.wallet.service.WebSocketService;
import com.samourai.wallet.util.AddressFactory;
import com.samourai.wallet.util.AppUtil;
import com.samourai.wallet.util.BlockExplorerUtil;
import com.samourai.wallet.util.CharSequenceX;
import com.samourai.wallet.util.DateUtil;
import com.samourai.wallet.util.ExchangeRateFactory;
import com.samourai.wallet.util.MonetaryUtil;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.ReceiveLookAtUtil;
import com.samourai.wallet.util.TypefaceUtil;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;
import org.spongycastle.util.encoders.DecoderException;

import java.io.IOException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class BalanceActivity extends Activity {

    private final static String strWalletPackage = "com.samourai.wallet";

    private LinearLayout tvBalanceBar = null;
    private TextView tvBalanceAmount = null;
    private TextView tvBalanceUnits = null;

    private ListView txList = null;
    private List<Tx> txs = null;
    private HashMap<String, Boolean> txStates = null;
    private TransactionAdapter txAdapter = null;
    private SwipeRefreshLayout swipeRefreshLayout = null;

    private boolean isBTC = true;

    public static final String ACTION_INTENT = "com.samourai.wallet.BalanceFragment.REFRESH";
    protected BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {

            if(ACTION_INTENT.equals(intent.getAction())) {

                final boolean notifTx = intent.getBooleanExtra("notifTx", false);
                final boolean fetch = intent.getBooleanExtra("fetch", false);

                final String blkHash;
                if(intent.hasExtra("hash"))    {
                    blkHash = intent.getStringExtra("hash");
                }
                else    {
                    blkHash = null;
                }
                final String addr;
                if(intent.hasExtra("received_on"))    {
                    addr = intent.getStringExtra("received_on");
                }
                else    {
                    addr = null;
                }

                if(addr != null && addr.length() > 0)    {
                    new AlertDialog.Builder(BalanceActivity.this)
                            .setTitle(R.string.app_name)
                            .setMessage(BalanceActivity.this.getString(R.string.boost_confirmed) + " " + addr)
                            .setCancelable(true)
                            .setIcon(R.drawable.ic_launcher)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {

                                    doAddressView(addr);

                                }
                            })
                            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    ;
                                }
                            }).show();

                }

                BalanceActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvBalanceAmount.setText("");
                        tvBalanceUnits.setText("");
                        refreshTx(notifTx, fetch, false);

                        if(BalanceActivity.this != null)    {

                            try {
                                PayloadUtil.getInstance(BalanceActivity.this).saveWalletToJSON(new CharSequenceX(AccessFactory.getInstance(BalanceActivity.this).getGUID() + AccessFactory.getInstance(BalanceActivity.this).getPIN()));
                            }
                            catch(MnemonicException.MnemonicLengthException mle) {
                                ;
                            }
                            catch(JSONException je) {
                                ;
                            }
                            catch(IOException ioe) {
                                ;
                            }
                            catch(DecryptionException de) {
                                ;
                            }

                        }

                    }
                });

            }

        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balance);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        LayoutInflater inflator = BalanceActivity.this.getLayoutInflater();
        tvBalanceBar = (LinearLayout)inflator.inflate(R.layout.balance_layout, null);
        tvBalanceBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(isBTC)    {
                    isBTC = false;
                }
                else    {
                    isBTC = true;
                }
                displayBalance();
                txAdapter.notifyDataSetChanged();
                return false;
            }
        });
        tvBalanceAmount = (TextView)tvBalanceBar.findViewById(R.id.BalanceAmount);
        tvBalanceUnits = (TextView)tvBalanceBar.findViewById(R.id.BalanceUnits);

        txs = new ArrayList<Tx>();
        txStates = new HashMap<String, Boolean>();
        txList = (ListView)findViewById(R.id.txList);
        txAdapter = new TransactionAdapter();
        txList.setAdapter(txAdapter);
        txList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {

                if(position == 0) {
                    return;
                }

                long viewId = view.getId();
                View v = (View)view.getParent();
                final Tx tx = txs.get(position - 1);
                ImageView ivTxStatus = (ImageView)v.findViewById(R.id.TransactionStatus);
                TextView tvConfirmationCount = (TextView)v.findViewById(R.id.ConfirmationCount);

                if(viewId == R.id.ConfirmationCount || viewId == R.id.TransactionStatus) {

                    if(txStates.containsKey(tx.getHash()) && txStates.get(tx.getHash()) == true) {
                        txStates.put(tx.getHash(), false);
                        displayTxStatus(false, tx.getConfirmations(), tvConfirmationCount, ivTxStatus);
                    }
                    else {
                        txStates.put(tx.getHash(), true);
                        displayTxStatus(true, tx.getConfirmations(), tvConfirmationCount, ivTxStatus);
                    }

                }
                else {

                    String message = getString(R.string.options_unconfirmed_tx);

                    // CPFP
                    if(tx.getConfirmations() < 1)   {
                        AlertDialog.Builder builder = new AlertDialog.Builder(BalanceActivity.this);
                        builder.setTitle(R.string.app_name);
                        builder.setMessage(message);
                        builder.setCancelable(true);
                        builder.setIcon(R.drawable.ic_launcher);
                        builder.setPositiveButton(R.string.options_bump_fee, new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, int whichButton) {

                                CPFPTask CPFPTask = new CPFPTask();
                                CPFPTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tx.getHash());

                            }
                        });
                        builder.setNegativeButton(R.string.options_block_explorer, new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, int whichButton) {
                                doExplorerView(tx.getHash());
                            }
                        });

                        AlertDialog alert = builder.create();
                        alert.show();
                    }
                    else    {
                        doExplorerView(tx.getHash());
                        return;
                    }

                }

            }
        });

        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {

                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        refreshTx(false, true, true);
                    }
                });

            }
        });
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        IntentFilter filter = new IntentFilter(ACTION_INTENT);
        LocalBroadcastManager.getInstance(BalanceActivity.this).registerReceiver(receiver, filter);

        PrefsUtil.getInstance(BalanceActivity.this).setValue(PrefsUtil.CURRENT_EXCHANGE_SEL, 2);

        refreshTx(false, true, false);

    }

    @Override
    public void onResume() {
        super.onResume();

//        IntentFilter filter = new IntentFilter(ACTION_INTENT);
//        LocalBroadcastManager.getInstance(BalanceActivity.this).registerReceiver(receiver, filter);

        if(!AppUtil.getInstance(BalanceActivity.this.getApplicationContext()).isServiceRunning(WebSocketService.class)) {
            startService(new Intent(BalanceActivity.this.getApplicationContext(), WebSocketService.class));
        }

    }

    @Override
    public void onPause() {
        super.onPause();

//        LocalBroadcastManager.getInstance(BalanceActivity.this).unregisterReceiver(receiver);

    }

    @Override
    public void onDestroy() {

        LocalBroadcastManager.getInstance(BalanceActivity.this).unregisterReceiver(receiver);

        if(AppUtil.getInstance(BalanceActivity.this.getApplicationContext()).isServiceRunning(WebSocketService.class)) {
            stopService(new Intent(BalanceActivity.this.getApplicationContext(), WebSocketService.class));
        }

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_about) {
            doAbout();
        }
        else if (id == R.id.action_download) {
            downloadSamourai();
        }
        else if (id == R.id.action_wipe) {

            try {
                PayloadUtil.getInstance(BalanceActivity.this).delete();
            }
            catch(IOException ioe) {
                ;
            }

            PrefsUtil.getInstance(BalanceActivity.this).setValue(PrefsUtil.PIN, "");
            PrefsUtil.getInstance(BalanceActivity.this).setValue(PrefsUtil.GUID, "");
            PrefsUtil.getInstance(BalanceActivity.this).setValue(PrefsUtil.ACCESS_HASH, "");
            PrefsUtil.getInstance(BalanceActivity.this).setValue(PrefsUtil.ACCESS_HASH2, "");

            AppUtil.getInstance(BalanceActivity.this).restartApp();

        }
        else    {
            ;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if(keyCode == KeyEvent.KEYCODE_BACK) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(R.drawable.ic_launcher);
            builder.setMessage(R.string.ask_you_sure_exit).setCancelable(false);
            AlertDialog alert = builder.create();

            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.yes), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {

                    AccessFactory.getInstance(BalanceActivity.this).setIsLoggedIn(false);

                    try {
                        PayloadUtil.getInstance(BalanceActivity.this).saveWalletToJSON(new CharSequenceX(AccessFactory.getInstance(BalanceActivity.this).getGUID() + AccessFactory.getInstance(BalanceActivity.this).getPIN()));
                    }
                    catch(MnemonicException.MnemonicLengthException mle) {
                        ;
                    }
                    catch(JSONException je) {
                        ;
                    }
                    catch(IOException ioe) {
                        ;
                    }
                    catch(DecryptionException de) {
                        ;
                    }

                    Intent intent = new Intent(BalanceActivity.this, ExodusActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    BalanceActivity.this.startActivity(intent);

                }});

            alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }
            });

            if(!isFinishing())    {
                alert.show();
            }

            return true;
        }
        else	{
            ;
        }

        return false;
    }

    private class TransactionAdapter extends BaseAdapter {

        private LayoutInflater inflater = null;
        private static final int TYPE_ITEM = 0;
        private static final int TYPE_BALANCE = 1;

        TransactionAdapter() {
            inflater = (LayoutInflater)BalanceActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            if(txs == null) {
                txs = new ArrayList<Tx>();
                txStates = new HashMap<String, Boolean>();
            }
            return txs.size() + 1;
        }

        @Override
        public String getItem(int position) {
            if(txs == null) {
                txs = new ArrayList<Tx>();
                txStates = new HashMap<String, Boolean>();
            }
            if(position == 0) {
                return "";
            }
            return txs.get(position - 1).toString();
        }

        @Override
        public long getItemId(int position) {
            return position - 1;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_BALANCE : TYPE_ITEM;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {

            View view = null;

            int type = getItemViewType(position);
            if(convertView == null) {
                if(type == TYPE_BALANCE) {
                    view = tvBalanceBar;
                }
                else {
                    view = inflater.inflate(R.layout.tx_layout_simple, parent, false);
                }
            }
            else {
                view = convertView;
            }

            if(type == TYPE_BALANCE) {
                ;
            }
            else {
                view.findViewById(R.id.TransactionStatus).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((ListView)parent).performItemClick(v, position, 0);
                    }
                });

                view.findViewById(R.id.ConfirmationCount).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((ListView)parent).performItemClick(v, position, 0);
                    }
                });

                Tx tx = txs.get(position - 1);

                TextView tvTodayLabel = (TextView)view.findViewById(R.id.TodayLabel);
                String strDateGroup = DateUtil.getInstance(BalanceActivity.this).group(tx.getTS());
                if(position == 1) {
                    tvTodayLabel.setText(strDateGroup);
                    tvTodayLabel.setVisibility(View.VISIBLE);
                }
                else {
                    Tx prevTx = txs.get(position - 2);
                    String strPrevDateGroup = DateUtil.getInstance(BalanceActivity.this).group(prevTx.getTS());

                    if(strPrevDateGroup.equals(strDateGroup)) {
                        tvTodayLabel.setVisibility(View.GONE);
                    }
                    else {
                        tvTodayLabel.setText(strDateGroup);
                        tvTodayLabel.setVisibility(View.VISIBLE);
                    }
                }

                String strDetails = null;
                String strTS = DateUtil.getInstance(BalanceActivity.this).formatted(tx.getTS());
                long _amount = 0L;
                if(tx.getAmount() < 0.0) {
                    _amount = Math.abs((long)tx.getAmount());
                    strDetails = BalanceActivity.this.getString(R.string.you_sent);
                }
                else {
                    _amount = (long)tx.getAmount();
                    strDetails = BalanceActivity.this.getString(R.string.you_received);
                }
                String strAmount = null;
                String strUnits = null;
                if(isBTC)    {
                    strAmount = getBTCDisplayAmount(_amount);
                    strUnits = getBTCDisplayUnits();
                }
                else    {
                    strAmount = getFiatDisplayAmount(_amount);
                    strUnits = getFiatDisplayUnits();
                }

                TextView tvDirection = (TextView)view.findViewById(R.id.TransactionDirection);
                TextView tvDirection2 = (TextView)view.findViewById(R.id.TransactionDirection2);
                TextView tvDetails = (TextView)view.findViewById(R.id.TransactionDetails);
                ImageView ivTxStatus = (ImageView)view.findViewById(R.id.TransactionStatus);
                TextView tvConfirmationCount = (TextView)view.findViewById(R.id.ConfirmationCount);

                tvDirection.setTypeface(TypefaceUtil.getInstance(BalanceActivity.this).getAwesomeTypeface());
                if(tx.getAmount() < 0.0) {
                    tvDirection.setTextColor(Color.RED);
                    tvDirection.setText(Character.toString((char) TypefaceUtil.awesome_arrow_up));
                }
                else {
                    tvDirection.setTextColor(Color.GREEN);
                    tvDirection.setText(Character.toString((char) TypefaceUtil.awesome_arrow_down));
                }

                if(txStates.containsKey(tx.getHash()) && txStates.get(tx.getHash()) == false) {
                    txStates.put(tx.getHash(), false);
                    displayTxStatus(false, tx.getConfirmations(), tvConfirmationCount, ivTxStatus);
                }
                else {
                    txStates.put(tx.getHash(), true);
                    displayTxStatus(true, tx.getConfirmations(), tvConfirmationCount, ivTxStatus);
                }

                tvDirection2.setText(strDetails + " " + strAmount + " " + strUnits);
                tvDetails.setText(strTS);
            }

            return view;
        }

    }

    private void refreshTx(final boolean notifTx, final boolean fetch, final boolean dragged) {

        RefreshTask refreshTask = new RefreshTask(dragged);
        refreshTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, notifTx ? "1" : "0", fetch ? "1" : "0");

    }

    private void displayBalance() {
        String strFiat = PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.CURRENT_FIAT, "USD");
        double btc_fx = ExchangeRateFactory.getInstance(BalanceActivity.this).getAvgPrice(strFiat);

        long balance = 0L;
        if(SamouraiWallet.getInstance().getShowTotalBalance())    {
            if(SamouraiWallet.getInstance().getCurrentSelectedAccount() == 0)    {
                balance = APIFactory.getInstance(BalanceActivity.this).getXpubBalance();
            }
            else    {
                if(APIFactory.getInstance(BalanceActivity.this).getXpubAmounts().size() > 0)    {
                    try    {
                        if(APIFactory.getInstance(BalanceActivity.this).getXpubAmounts().get(HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(SamouraiWallet.getInstance().getCurrentSelectedAccount() - 1).xpubstr()) != null)    {
                            balance = APIFactory.getInstance(BalanceActivity.this).getXpubAmounts().get(HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(SamouraiWallet.getInstance().getCurrentSelectedAccount() - 1).xpubstr());
                        }
                    }
                    catch(IOException ioe)    {
                        ;
                    }
                    catch(MnemonicException.MnemonicLengthException mle)    {
                        ;
                    }
                }
            }
        }
        else    {
            if(APIFactory.getInstance(BalanceActivity.this).getXpubAmounts().size() > 0)    {
                try    {
                    if(APIFactory.getInstance(BalanceActivity.this).getXpubAmounts().get(HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(SamouraiWallet.getInstance().getCurrentSelectedAccount()).xpubstr()) != null)    {
                        balance = APIFactory.getInstance(BalanceActivity.this).getXpubAmounts().get(HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(SamouraiWallet.SAMOURAI_ACCOUNT).xpubstr());
                    }
                }
                catch(IOException ioe)    {
                    ;
                }
                catch(MnemonicException.MnemonicLengthException mle)    {
                    ;
                }
            }
        }
        double btc_balance = (((double)balance) / 1e8);
        double fiat_balance = btc_fx * btc_balance;

        if(isBTC) {
            tvBalanceAmount.setText(getBTCDisplayAmount(balance));
            tvBalanceUnits.setText(getBTCDisplayUnits());
        }
        else {
            tvBalanceAmount.setText(MonetaryUtil.getInstance().getFiatFormat(strFiat).format(fiat_balance));
            tvBalanceUnits.setText(strFiat);
        }

    }

    private String getBTCDisplayAmount(long value) {

        String strAmount = null;
        DecimalFormat df = new DecimalFormat("#");
        df.setMinimumIntegerDigits(1);
        df.setMinimumFractionDigits(1);
        df.setMaximumFractionDigits(8);

        int unit = PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.BTC_UNITS, MonetaryUtil.UNIT_BTC);
        switch(unit) {
            case MonetaryUtil.MICRO_BTC:
                strAmount = df.format(((double)(value * 1000000L)) / 1e8);
                break;
            case MonetaryUtil.MILLI_BTC:
                strAmount = df.format(((double)(value * 1000L)) / 1e8);
                break;
            default:
                strAmount = Coin.valueOf(value).toPlainString();
                break;
        }

        return strAmount;
    }

    private String getBTCDisplayUnits() {

        return (String) MonetaryUtil.getInstance().getBTCUnits()[PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.BTC_UNITS, MonetaryUtil.UNIT_BTC)];

    }

    private String getFiatDisplayAmount(long value) {

        String strFiat = PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.CURRENT_FIAT, "USD");
        double btc_fx = ExchangeRateFactory.getInstance(BalanceActivity.this).getAvgPrice(strFiat);
        String strAmount = MonetaryUtil.getInstance().getFiatFormat(strFiat).format(btc_fx * (((double)value) / 1e8));

        return strAmount;
    }

    private String getFiatDisplayUnits() {

        return PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.CURRENT_FIAT, "USD");

    }

    private void displayTxStatus(boolean heads, long confirmations, TextView tvConfirmationCount, ImageView ivTxStatus)	{

        if(heads)	{
            if(confirmations == 0) {
                rotateTxStatus(tvConfirmationCount, true);
                ivTxStatus.setVisibility(View.VISIBLE);
                ivTxStatus.setImageResource(R.drawable.ic_query_builder_white);
                tvConfirmationCount.setVisibility(View.GONE);
            }
            else if(confirmations > 3) {
                rotateTxStatus(tvConfirmationCount, true);
                ivTxStatus.setVisibility(View.VISIBLE);
                ivTxStatus.setImageResource(R.drawable.ic_done_white);
                tvConfirmationCount.setVisibility(View.GONE);
            }
            else {
                rotateTxStatus(ivTxStatus, false);
                tvConfirmationCount.setVisibility(View.VISIBLE);
                tvConfirmationCount.setText(Long.toString(confirmations));
                ivTxStatus.setVisibility(View.GONE);
            }
        }
        else	{
            if(confirmations < 100) {
                rotateTxStatus(ivTxStatus, false);
                tvConfirmationCount.setVisibility(View.VISIBLE);
                tvConfirmationCount.setText(Long.toString(confirmations));
                ivTxStatus.setVisibility(View.GONE);
            }
            else    {
                rotateTxStatus(ivTxStatus, false);
                tvConfirmationCount.setVisibility(View.VISIBLE);
                tvConfirmationCount.setText("\u221e");
                ivTxStatus.setVisibility(View.GONE);
            }
        }

    }

    private void rotateTxStatus(View view, boolean clockwise)	{

        float degrees = 360f;
        if(!clockwise)	{
            degrees = -360f;
        }

        ObjectAnimator animation = ObjectAnimator.ofFloat(view, "rotationY", 0.0f, degrees);
        animation.setDuration(1000);
        animation.setRepeatCount(0);
        animation.setInterpolator(new AnticipateInterpolator());
        animation.start();
    }

    private void doExplorerView(String strHash)   {

        if(strHash != null) {
            int sel = PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.BLOCK_EXPLORER, 0);
            CharSequence url = BlockExplorerUtil.getInstance().getBlockExplorerTxUrls()[sel];

            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url + strHash));
            startActivity(browserIntent);
        }

    }

    private void doAddressView(String strAddress)   {

        if(strAddress != null) {
            int sel = PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.BLOCK_EXPLORER, 0);
            CharSequence url = BlockExplorerUtil.getInstance().getBlockExplorerAddressUrls()[sel];

            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url + strAddress));
            startActivity(browserIntent);
        }

    }

    private class RefreshTask extends AsyncTask<String, Void, String> {

        private String strProgressTitle = null;
        private String strProgressMessage = null;

        private ProgressDialog progress = null;
        private Handler handler = null;
        private boolean dragged = false;

        public RefreshTask(boolean dragged) {
            super();
            Log.d("BalanceActivity", "RefreshTask, dragged==" + dragged);
            handler = new Handler();
            this.dragged = dragged;
        }

        @Override
        protected void onPreExecute() {

            Log.d("BalanceActivity", "RefreshTask.preExecute()");

            if(!dragged)    {
                strProgressTitle = BalanceActivity.this.getText(R.string.app_name).toString();
                strProgressMessage = BalanceActivity.this.getText(R.string.refresh_tx_pre).toString();

                progress = new ProgressDialog(BalanceActivity.this);
                progress.setCancelable(true);
                progress.setTitle(strProgressTitle);
                progress.setMessage(strProgressMessage);
                progress.show();
            }

        }

        @Override
        protected String doInBackground(String... params) {

            Log.d("BalanceActivity", "doInBackground()");

            final boolean notifTx = params[0].equals("1") ? true : false;
            final boolean fetch = params[1].equals("1") ? true : false;

            //
            // TBD: check on lookahead/lookbehind for all incoming payment codes
            //
            if(fetch || txs.size() == 0)    {
                Log.d("BalanceActivity", "initWallet()");
                APIFactory.getInstance(BalanceActivity.this).initWallet();
            }

            try {
                int acc = 0;
                if(SamouraiWallet.getInstance().getShowTotalBalance())    {
                    if(SamouraiWallet.getInstance().getCurrentSelectedAccount() == 0)    {
                        txs = APIFactory.getInstance(BalanceActivity.this).getAllXpubTxs();
                    }
                    else    {
                        acc = SamouraiWallet.getInstance().getCurrentSelectedAccount() - 1;
                        txs = APIFactory.getInstance(BalanceActivity.this).getXpubTxs().get(HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(acc).xpubstr());
                    }
                }
                else    {
                    txs = APIFactory.getInstance(BalanceActivity.this).getXpubTxs().get(HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(acc).xpubstr());
                }
                if(txs != null)    {
                    Collections.sort(txs, new APIFactory.TxMostRecentDateComparator());
                }

                if(AddressFactory.getInstance().getHighestTxReceiveIdx(acc) > HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(acc).getReceive().getAddrIdx()) {
                    HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(acc).getReceive().setAddrIdx(AddressFactory.getInstance().getHighestTxReceiveIdx(acc));
                }
                if(AddressFactory.getInstance().getHighestTxChangeIdx(acc) > HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(acc).getChange().getAddrIdx()) {
                    HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(acc).getChange().setAddrIdx(AddressFactory.getInstance().getHighestTxChangeIdx(acc));
                }
            }
            catch(IOException ioe) {
                ioe.printStackTrace();
            }
            catch(MnemonicException.MnemonicLengthException mle) {
                mle.printStackTrace();
            }
            finally {
                ;
            }

            List<Tx> _txs = new ArrayList<Tx>();
            for(Tx tx : txs)   {
                if(tx.getConfirmations() < 1)   {
                    _txs.add(tx);
                }
            }
            txs = _txs;

            if(!dragged)    {
                strProgressMessage = BalanceActivity.this.getText(R.string.refresh_tx).toString();
                publishProgress();
            }

            handler.post(new Runnable() {
                public void run() {
                    if(dragged)    {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    tvBalanceAmount.setText("");
                    tvBalanceUnits.setText("");
                    displayBalance();
                    txAdapter.notifyDataSetChanged();
                }
            });

            PrefsUtil.getInstance(BalanceActivity.this).setValue(PrefsUtil.FIRST_RUN, false);

            return "OK";
        }

        @Override
        protected void onPostExecute(String result) {

            if(!dragged)    {
                if(progress != null && progress.isShowing())    {
                    progress.dismiss();
                }
            }

            if(txs.size() == 0)    {

                AlertDialog.Builder builder = new AlertDialog.Builder(BalanceActivity.this);
                builder.setTitle(R.string.app_name);
                builder.setIcon(R.drawable.ic_launcher);
                builder.setCancelable(false);

                if(!hasSamourai())    {
                    builder.setMessage(BalanceActivity.this.getText(R.string.no_unconfirmed_tx) + "\n\n" + BalanceActivity.this.getText(R.string.download_samourai));
                    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, int whichButton) {

                            downloadSamourai();

                        }
                    });
                    builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, int whichButton) {
                            ;
                        }
                    });
                }
                else    {
                    builder.setMessage(BalanceActivity.this.getText(R.string.no_unconfirmed_tx));
                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, int whichButton) {
                            dialog.dismiss();
                        }
                    });
                }

                AlertDialog alert = builder.create();
                alert.show();

            }

        }

        @Override
        protected void onProgressUpdate(Void... values) {

            if(!dragged)    {
                progress.setTitle(strProgressTitle);
                progress.setMessage(strProgressMessage);
            }

        }
    }

    private class CPFPTask extends AsyncTask<String, Void, String> {

        private List<UTXO> utxos = null;
        private Handler handler = null;
        private BigInteger biAfterburnerFee = BigInteger.valueOf(220000L);
        private String strAfterburnerFeeAddress = "3KTMgRBkPjynpGq6SobdqGYjsALTSp6Ygz";
        private double dAfterburnerPrice = 5.99;
        private double btc_fx = 0.0;

        @Override
        protected void onPreExecute() {
            handler = new Handler();
            utxos = APIFactory.getInstance(BalanceActivity.this).getUtxos();
            btc_fx = ExchangeRateFactory.getInstance(BalanceActivity.this).getAvgPrice("USD");
            Log.d("BalanceActivity", "fx rate:" + btc_fx);
            if(btc_fx <= 0.0)    {
                btc_fx = 0.0;
            }
            else    {
                double currentPrice = (dAfterburnerPrice / btc_fx) * 1e8;
                biAfterburnerFee = BigInteger.valueOf((long)currentPrice);
            }
        }

        @Override
        protected String doInBackground(String... params) {

            Looper.prepare();

            Log.d("BalanceActivity", "hash:" + params[0]);

            JSONObject txObj = APIFactory.getInstance(BalanceActivity.this).getTxInfo(params[0]);
            if(txObj.has("inputs") && txObj.has("out"))    {

                final SuggestedFee suggestedFee = FeeUtil.getInstance().getSuggestedFee();
                Log.d("BalanceActivity", "overrriding suggested fee:" + suggestedFee.getDefaultPerKB().longValue());

                try {
                    JSONArray inputs = txObj.getJSONArray("inputs");
                    JSONArray outputs = txObj.getJSONArray("out");

                    FeeUtil.getInstance().setSuggestedFee(FeeUtil.getInstance().getHighFee());
                    Log.d("BalanceActivity", "using high fee:" + FeeUtil.getInstance().getSuggestedFee().getDefaultPerKB().longValue());
                    BigInteger estimatedFee = FeeUtil.getInstance().estimatedFee(inputs.length(), outputs.length());

                    long total_inputs = 0L;
                    long total_outputs = 0L;
                    long fee = 0L;

                    UTXO utxo = null;

                    for(int i = 0; i < inputs.length(); i++)   {
                        JSONObject obj = inputs.getJSONObject(i);
                        if(obj.has("prev_out"))    {
                            JSONObject objPrev = obj.getJSONObject("prev_out");
                            if(objPrev.has("value"))    {
                                total_inputs += objPrev.getLong("value");
                            }
                        }
                    }

                    for(int i = 0; i < outputs.length(); i++)   {
                        JSONObject obj = outputs.getJSONObject(i);
                        if(obj.has("value"))    {
                            total_outputs += obj.getLong("value");

                            String addr = obj.getString("addr");
                            Log.d("BalanceActivity", "checking address:" + addr);
                            if(utxo == null)    {
                                utxo = getUTXO(addr);
                            }
                        }
                    }

                    boolean feeWarning = false;
                    fee = total_inputs - total_outputs;
                    if(fee > estimatedFee.longValue())    {
                        feeWarning = true;
                    }

                    Log.d("BalanceActivity", "total inputs:" + total_inputs);
                    Log.d("BalanceActivity", "total outputs:" + total_outputs);
                    Log.d("BalanceActivity", "actual fee:" + fee);
                    Log.d("BalanceActivity", "estimated fee:" + estimatedFee.longValue());
                    Log.d("BalanceActivity", "fee warning:" + feeWarning);
                    if(utxo != null)    {
                        Log.d("BalanceActivity", "utxo found");

                        List<UTXO> selectedUTXO = new ArrayList<UTXO>();
                        selectedUTXO.add(utxo);
                        int selected = utxo.getOutpoints().size();

                        long remainingFee = (estimatedFee.longValue() > fee) ? estimatedFee.longValue() - fee : 0L;
                        Log.d("BalanceActivity", "remaining fee:" + remainingFee);
                        int receiveIdx = AddressFactory.getInstance(BalanceActivity.this).getHighestTxReceiveIdx(0);
                        Log.d("BalanceActivity", "receive index:" + receiveIdx);
                        final String ownReceiveAddr = AddressFactory.getInstance(BalanceActivity.this).get(AddressFactory.RECEIVE_CHAIN).getAddressString();
                        Log.d("BalanceActivity", "receive address:" + ownReceiveAddr);

                        long totalAmount = utxo.getValue();
                        Log.d("BalanceActivity", "amount before fee:" + totalAmount);
                        BigInteger cpfpFee = FeeUtil.getInstance().estimatedFee(selected, 1);
                        Log.d("BalanceActivity", "cpfp fee (1):" + cpfpFee.longValue());

                        if(totalAmount < (cpfpFee.longValue() + remainingFee + biAfterburnerFee.longValue() + SamouraiWallet.bDust.longValue())) {
                            Log.d("BalanceActivity", "selecting additional utxo");
                            Collections.sort(utxos, new UTXO.UTXOComparator());
                            for(UTXO _utxo : utxos)   {
                                totalAmount += _utxo.getValue();
                                selectedUTXO.add(_utxo);
                                selected += _utxo.getOutpoints().size();
                                cpfpFee = FeeUtil.getInstance().estimatedFee(selected, 1);
                                if(totalAmount > (cpfpFee.longValue() + remainingFee + biAfterburnerFee.longValue() + SamouraiWallet.bDust.longValue())) {
                                    break;
                                }
                            }
                            if(totalAmount < (cpfpFee.longValue() + remainingFee)) {
                                handler.post(new Runnable() {
                                    public void run() {
                                        Toast.makeText(BalanceActivity.this, R.string.insufficient_funds, Toast.LENGTH_SHORT).show();
                                    }
                                });
                                FeeUtil.getInstance().setSuggestedFee(suggestedFee);
                                return "KO";
                            }
                        }

                        cpfpFee = cpfpFee.add(BigInteger.valueOf(remainingFee));
                        Log.d("BalanceActivity", "cpfp fee (2):" + cpfpFee.longValue());

                        final List<MyTransactionOutPoint> outPoints = new ArrayList<MyTransactionOutPoint>();
                        for(UTXO u : selectedUTXO)   {
                            outPoints.addAll(u.getOutpoints());
                        }

                        long _totalAmount = 0L;
                        for(MyTransactionOutPoint outpoint : outPoints)   {
                            _totalAmount += outpoint.getValue().longValue();
                        }
                        Log.d("BalanceActivity", "checked total amount:" + _totalAmount);
                        assert(_totalAmount == totalAmount);

                        Log.d("BalanceActivity", "Afterburner fee:" + biAfterburnerFee.longValue());
                        long amount = totalAmount - (cpfpFee.longValue() + biAfterburnerFee.longValue());
                        Log.d("BalanceActivity", "amount after all fees:" + amount);

                        if(amount < SamouraiWallet.bDust.longValue())    {
                            Log.d("BalanceActivity", "dust output");
                            Toast.makeText(BalanceActivity.this, R.string.cannot_output_dust, Toast.LENGTH_SHORT).show();
                        }

                        final HashMap<String, BigInteger> receivers = new HashMap<String, BigInteger>();
                        receivers.put(ownReceiveAddr, BigInteger.valueOf(amount));
                        receivers.put(strAfterburnerFeeAddress, biAfterburnerFee);

                        String message = "";
                        if(feeWarning)  {
                            message += BalanceActivity.this.getString(R.string.fee_bump_not_necessary);
                            message += "\n\n";
                        }
                        message += BalanceActivity.this.getString(R.string.bump_fee) + " " + Coin.valueOf(cpfpFee.longValue()).toPlainString() + " BTC";
                        message += "\n\n" + BalanceActivity.this.getString(R.string.bump_fee2) + " " + Coin.valueOf(biAfterburnerFee.longValue()).toPlainString() + " BTC";

                        AlertDialog.Builder dlg = new AlertDialog.Builder(BalanceActivity.this)
                                .setTitle(R.string.app_name)
                                .setMessage(message)
                                .setCancelable(false)
                                .setIcon(R.drawable.ic_launcher)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {

                                        Transaction tx = SendFactory.getInstance(BalanceActivity.this).makeTransaction(0, outPoints, receivers);
                                        if(tx != null)    {
                                            tx = SendFactory.getInstance(BalanceActivity.this).signTransaction(tx);
                                            final String hexTx = new String(Hex.encode(tx.bitcoinSerialize()));
                                            Log.d("BalanceActivity", hexTx);

                                            final String strTxHash = tx.getHashAsString();
                                            Log.d("BalanceActivity", strTxHash);

                                            boolean isOK = false;
                                            try {

                                                isOK = PushTx.getInstance(BalanceActivity.this).pushTx(hexTx);

                                                if(isOK)    {

                                                    handler.post(new Runnable() {
                                                        public void run() {
                                                            Toast.makeText(BalanceActivity.this, R.string.cpfp_spent, Toast.LENGTH_SHORT).show();

                                                            ReceiveLookAtUtil.getInstance().add(ownReceiveAddr);

                                                            if(AppUtil.getInstance(BalanceActivity.this.getApplicationContext()).isServiceRunning(WebSocketService.class)) {
                                                                stopService(new Intent(BalanceActivity.this.getApplicationContext(), WebSocketService.class));
                                                            }
                                                            startService(new Intent(BalanceActivity.this.getApplicationContext(), WebSocketService.class));

                                                            FeeUtil.getInstance().setSuggestedFee(suggestedFee);

                                                            Intent _intent = new Intent(BalanceActivity.this, com.samourai.afterburner.MainActivity.class);
                                                            _intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                                            startActivity(_intent);
                                                        }
                                                    });

                                                }
                                                else    {
                                                    handler.post(new Runnable() {
                                                        public void run() {
                                                            Toast.makeText(BalanceActivity.this, R.string.tx_failed, Toast.LENGTH_SHORT).show();
                                                        }
                                                    });

                                                    // reset receive index upon tx fail
                                                    int prevIdx = HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(0).getReceive().getAddrIdx() - 1;
                                                    HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(0).getReceive().setAddrIdx(prevIdx);
                                                }
                                            }
                                            catch(MnemonicException.MnemonicLengthException | DecoderException | IOException e) {
                                                handler.post(new Runnable() {
                                                    public void run() {
                                                        Toast.makeText(BalanceActivity.this, "pushTx:" + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            }
                                            finally {
                                                ;
                                            }

                                        }

                                    }
                                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {

                                        try {
                                            // reset receive index upon tx fail
                                            int prevIdx = HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(0).getReceive().getAddrIdx() - 1;
                                            HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(0).getReceive().setAddrIdx(prevIdx);
                                        }
                                        catch(MnemonicException.MnemonicLengthException | DecoderException | IOException e) {
                                            handler.post(new Runnable() {
                                                public void run() {
                                                    Toast.makeText(BalanceActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                        }
                                        finally {
                                            dialog.dismiss();
                                        }

                                    }
                                });
                        if(!isFinishing())    {
                            dlg.show();
                        }

                    }
                    else    {
                        handler.post(new Runnable() {
                            public void run() {
                                Toast.makeText(BalanceActivity.this, R.string.cannot_create_cpfp, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                }
                catch(final JSONException je) {
                    handler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(BalanceActivity.this, "cpfp:" + je.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                FeeUtil.getInstance().setSuggestedFee(suggestedFee);

            }
            else    {
                handler.post(new Runnable() {
                    public void run() {
                        Toast.makeText(BalanceActivity.this, R.string.cpfp_cannot_retrieve_tx, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            Looper.loop();

            return "OK";
        }

        @Override
        protected void onPostExecute(String result) {
            ;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            ;
        }

        private UTXO getUTXO(String address)    {

            UTXO ret = null;
            int idx = -1;

            for(int i = 0; i < utxos.size(); i++)  {
                UTXO utxo = utxos.get(i);
                Log.d("BalanceActivity", "utxo address:" + utxo.getOutpoints().get(0).getAddress());
                if(utxo.getOutpoints().get(0).getAddress().equals(address))    {
                    ret = utxo;
                    idx = i;
                    break;
                }
            }

            if(ret != null)    {
                utxos.remove(idx);
                return ret;
            }

            return null;
        }

    }

    private void downloadSamourai()   {

        /*
        Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + strWalletPackage));
        startActivity(marketIntent);
        */

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://samouraiwallet.com/alpha.html"));
        startActivity(browserIntent);
    }

    private boolean hasSamourai()	{
        PackageManager pm = this.getPackageManager();
        try	{
            pm.getPackageInfo(strWalletPackage, 0);
            return true;
        }
        catch(PackageManager.NameNotFoundException nnfe)	{
            return false;
        }
    }

    private void doAbout()  {

        AlertDialog.Builder builder = new AlertDialog.Builder(BalanceActivity.this);
        builder.setTitle(R.string.action_about);
        builder.setMessage("v. " + BalanceActivity.this.getText(R.string.version_name) + "\n\n" + "by Samourai");
        builder.setIcon(R.drawable.ic_launcher);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, int whichButton) {

                dialog.dismiss();

            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

}
