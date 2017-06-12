package com.samourai.wallet;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
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
import android.text.InputType;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
//import android.util.Log;

import org.apache.commons.codec.DecoderException;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.json.JSONException;

import com.dm.zbar.android.scanner.ZBarConstants;
import com.dm.zbar.android.scanner.ZBarScannerActivity;
import com.samourai.afterburner.MainActivity;
import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.crypto.AESUtil;
import com.samourai.wallet.crypto.DecryptionException;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactory;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.prng.PRNGFixes;
import com.samourai.wallet.service.WebSocketService;
import com.samourai.wallet.util.AddressFactory;
import com.samourai.wallet.util.AppUtil;
import com.samourai.wallet.util.CharSequenceX;
import com.samourai.wallet.util.ConnectivityStatus;
import com.samourai.wallet.util.ExchangeRateFactory;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.TimeOutUtil;
import com.samourai.wallet.util.WebUtil;

import com.samourai.afterburner.R;

import net.sourceforge.zbar.Symbol;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

public class MainActivity2 extends Activity {

    private static final int SCAN_SEED = 2075;

    private static String[] account_selections = null;
    private static ArrayAdapter<String> adapter = null;

    private static boolean loadedBalanceFragment = false;

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

        loadedBalanceFragment = false;

//        doAccountSelection();

        getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        ActionBar.OnNavigationListener navigationListener = new ActionBar.OnNavigationListener() {
            @Override
            public boolean onNavigationItemSelected(int itemPosition, long itemId) {

                if(itemPosition == 2 && PrefsUtil.getInstance(MainActivity2.this).getValue(PrefsUtil.FIRST_USE_SHUFFLE, true) == true)    {

                    new AlertDialog.Builder(MainActivity2.this)
                            .setTitle(R.string.app_name)
                            .setMessage(R.string.first_use_shuffle)
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    PrefsUtil.getInstance(MainActivity2.this).setValue(PrefsUtil.FIRST_USE_SHUFFLE, false);
                                }
                            }).show();

                }

                SamouraiWallet.getInstance().setCurrentSelectedAccount(itemPosition);
                if(account_selections.length > 1)    {
                    SamouraiWallet.getInstance().setShowTotalBalance(true);
                }
                else    {
                    SamouraiWallet.getInstance().setShowTotalBalance(false);
                }
                if(loadedBalanceFragment)    {
                    Intent intent = new Intent(MainActivity2.this, BalanceActivity.class);
                    intent.putExtra("notifTx", false);
                    intent.putExtra("fetch", false);
                    startActivity(intent);
                }

                return false;
            }
        };

        getActionBar().setListNavigationCallbacks(adapter, navigationListener);
        getActionBar().setSelectedNavigationItem(1);

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
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            AppUtil.getInstance(MainActivity2.this).restartApp();
                        }
                    }).show();

        }
        else  {

            exchangeRateThread();

            boolean isDial = false;
            String strUri = null;
            String strPCode = null;
            Bundle extras = getIntent().getExtras();
            if(extras != null && extras.containsKey("dialed"))	{
                isDial = extras.getBoolean("dialed");
            }
            if(extras != null && extras.containsKey("uri"))	{
                strUri = extras.getString("uri");
            }
            if(extras != null && extras.containsKey("pcode"))	{
                strPCode = extras.getString("pcode");
            }

            doAppInit(isDial, strUri, strPCode);

        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        AppUtil.getInstance(MainActivity2.this).setIsInForeground(true);

        AppUtil.getInstance(MainActivity2.this).deleteQR();
        AppUtil.getInstance(MainActivity2.this).deleteBackup();

        if(TimeOutUtil.getInstance().isTimedOut()) {
            if(AccessFactory.getInstance(MainActivity2.this).getGUID().length() < 1 || !PayloadUtil.getInstance(MainActivity2.this).walletFileExists()) {
                AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);
                initDialog();
            }
            else {
                AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);
                validatePIN(null);
            }
        }
        else {
            TimeOutUtil.getInstance().updatePin();
        }

        IntentFilter filter_restart = new IntentFilter(ACTION_RESTART);
        LocalBroadcastManager.getInstance(MainActivity2.this).registerReceiver(receiver_restart, filter_restart);

        doAccountSelection();

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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(resultCode == Activity.RESULT_OK && requestCode == SCAN_SEED)	{

            if(data != null && data.getStringExtra(ZBarConstants.SCAN_RESULT) != null)	{

                final String strResult = data.getStringExtra(ZBarConstants.SCAN_RESULT);

                if(isBIP39Mnemonic(strResult))    {
                    initThread(false, createPin(), "", strResult);
                }
                else    {
                    Toast.makeText(MainActivity2.this, R.string.invalid_mnemonic, Toast.LENGTH_SHORT).show();
                    AppUtil.getInstance(MainActivity2.this).restartApp();
                }

            }
        }
        else if(resultCode == Activity.RESULT_CANCELED && requestCode == SCAN_SEED)	{
            AppUtil.getInstance(MainActivity2.this).restartApp();
        }
        else {
            AppUtil.getInstance(MainActivity2.this).restartApp();
        }

    }

    private void doScan() {
        Intent intent = new Intent(MainActivity2.this, ZBarScannerActivity.class);
        intent.putExtra(ZBarConstants.SCAN_MODES, new int[]{ Symbol.QRCODE } );
        startActivityForResult(intent, SCAN_SEED);
    }

    private void initDialog()	{

        AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);

        AlertDialog.Builder dlg = new AlertDialog.Builder(MainActivity2.this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.restore_wallet)
                .setCancelable(false)
                .setPositiveButton(R.string.import_scan, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        doScan();
                        dialog.dismiss();

                    }
                })
                .setNegativeButton(R.string.import_mnemonic, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        final EditText mnemonic = new EditText(MainActivity2.this);
                        mnemonic.setHint(R.string.mnemonic_hex);
                        mnemonic.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                        final EditText passphrase = new EditText(MainActivity2.this);
                        passphrase.setHint(R.string.bip39_passphrase);
                        passphrase.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                        passphrase.setSingleLine(true);

                        LinearLayout restoreLayout = new LinearLayout(MainActivity2.this);
                        restoreLayout.setOrientation(LinearLayout.VERTICAL);
                        restoreLayout.addView(mnemonic);
                        restoreLayout.addView(passphrase);

                        AlertDialog.Builder dlg = new AlertDialog.Builder(MainActivity2.this)
                                .setTitle(R.string.app_name)
                                .setMessage(R.string.bip39_restore)
                                .setView(restoreLayout)
                                .setCancelable(false)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {

                                        final String seed39 = mnemonic.getText().toString();
                                        final String passphrase39 = passphrase.getText().toString();

                                        if (seed39 != null && seed39.length() > 0 && isBIP39Mnemonic(seed39)) {
                                            initThread(false, createPin(), passphrase39 == null ? "" : passphrase39, seed39);

                                        } else {

                                            Toast.makeText(MainActivity2.this, R.string.invalid_mnemonic, Toast.LENGTH_SHORT).show();
                                            AppUtil.getInstance(MainActivity2.this).restartApp();

                                        }

                                        dialog.dismiss();

                                    }
                                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {

                                        AppUtil.getInstance(MainActivity2.this).restartApp();

                                    }
                                });
                        if(!isFinishing())    {
                            dlg.show();
                        }

                    }
                });
        if(!isFinishing())    {
            dlg.show();
        }

    }

    private void validatePIN(String strUri)	{

        if(AccessFactory.getInstance(MainActivity2.this).isLoggedIn() && !TimeOutUtil.getInstance().isTimedOut())	{
            return;
        }

        AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);

        validateThread(getPin(), strUri);

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

    private void doAppInit(boolean isDial, final String strUri, final String strPCode) {

        if(AccessFactory.getInstance(MainActivity2.this).getGUID().length() < 1 || !PayloadUtil.getInstance(MainActivity2.this).walletFileExists()) {
            AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);
            initDialog();
        }
        else if(TimeOutUtil.getInstance().isTimedOut()) {
            AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);
            validatePIN(strUri == null ? null : strUri);
        }
        else if(AccessFactory.getInstance(MainActivity2.this).isLoggedIn() && !TimeOutUtil.getInstance().isTimedOut()) {

            TimeOutUtil.getInstance().updatePin();
            loadedBalanceFragment = true;

            Intent intent = new Intent(MainActivity2.this, BalanceActivity.class);
            intent.putExtra("notifTx", true);
            intent.putExtra("fetch", true);
            startActivity(intent);
        }
        else {
            AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);
            validatePIN(strUri == null ? null : strUri);
        }

    }

    public void doAccountSelection() {

        if(!PayloadUtil.getInstance(MainActivity2.this).walletFileExists())    {
            return;
        }

        account_selections = new String[] {
                getString(R.string.total),
                getString(R.string.account_Afterburner),
                getString(R.string.account_shuffling),
        };

        adapter = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_spinner_dropdown_item, account_selections);

        if(account_selections.length > 1)    {
            SamouraiWallet.getInstance().setShowTotalBalance(true);
        }
        else    {
            SamouraiWallet.getInstance().setShowTotalBalance(false);
        }

    }

    private boolean isBIP39Mnemonic(String mnemonic)   {

        boolean ret = false;

        if(mnemonic.matches(FormatsUtil.XPUB)) {
            ret = false;
        }
        else if(mnemonic.matches(FormatsUtil.HEX) && mnemonic.length() % 4 == 0) {

            try {
                InputStream wis = MainActivity2.this.getResources().getAssets().open("BIP39/en.txt");
                MnemonicCode mc = null;
                mc = new MnemonicCode(wis, HD_WalletFactory.BIP39_ENGLISH_SHA256);
                List<String> seed = mc.toMnemonic(org.spongycastle.util.encoders.Hex.decode(mnemonic));

                ret = true;
            }
            catch(IOException ioe) {
                ret = false;
            }
            catch(MnemonicException.MnemonicLengthException mle) {
                ret = false;
            }
            catch(org.spongycastle.util.encoders.DecoderException de) {
                ret = false;
            }

        }
        else    {
            mnemonic = mnemonic.replaceAll("[^a-z]+", " ");             // only use for BIP39 English
            List<String> words = Arrays.asList(mnemonic.trim().split("\\s+"));

            if(words.size() % 3 != 0)    {
                ret = false;
            }
            else if(words.size() < 12 || words.size() > 24)   {
                ret = false;
            }
            else    {
                try {
                    InputStream wis = MainActivity2.this.getResources().getAssets().open("BIP39/en.txt");
                    MnemonicCode mc = null;
                    mc = new MnemonicCode(wis, HD_WalletFactory.BIP39_ENGLISH_SHA256);
                    byte[] seed = mc.toEntropy(words);

                    ret = true;
                }
                catch(IOException ioe) {
                    ret = false;
                }
                catch(MnemonicException.MnemonicLengthException mle) {
                    ret = false;
                }
                catch(MnemonicException.MnemonicChecksumException mce) {
                    ret = false;
                }
                catch(MnemonicException.MnemonicWordException mwe) {
                    ret = false;
                }

            }

        }

        return ret;
    }

    private void validateThread(final String pin, final String uri)	{

        final ProgressDialog progress = new ProgressDialog(MainActivity2.this);

        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }

        progress.setCancelable(false);
        progress.setTitle(R.string.app_name);
        progress.setMessage(getString(R.string.please_wait));
        progress.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                if (pin.length() < AccessFactory.MIN_PIN_LENGTH || pin.length() > AccessFactory.MAX_PIN_LENGTH) {
                    if (progress != null && progress.isShowing()) {
                        progress.dismiss();
                    }
                    Toast.makeText(MainActivity2.this, R.string.pin_error, Toast.LENGTH_SHORT).show();
                    AppUtil.getInstance(MainActivity2.this).restartApp();
                }

                String randomKey = AccessFactory.getInstance(MainActivity2.this).getGUID();
                if (randomKey.length() < 1) {
                    if (progress != null && progress.isShowing()) {
                        progress.dismiss();
                    }
                    Toast.makeText(MainActivity2.this, R.string.random_key_error, Toast.LENGTH_SHORT).show();
                    AppUtil.getInstance(MainActivity2.this).restartApp();
                }

                String hash = PrefsUtil.getInstance(MainActivity2.this).getValue(PrefsUtil.ACCESS_HASH, "");
                if (AccessFactory.getInstance(MainActivity2.this).validateHash(hash, randomKey, new CharSequenceX(pin), AESUtil.DefaultPBKDF2Iterations)) {

                    AccessFactory.getInstance(MainActivity2.this).setPIN(pin);

                    try {
                        HD_Wallet hdw = PayloadUtil.getInstance(MainActivity2.this).restoreWalletfromJSON(new CharSequenceX(AccessFactory.getInstance(MainActivity2.this).getGUID() + pin));

                        if (progress != null && progress.isShowing()) {
                            progress.dismiss();
                        }

                        if(hdw == null) {
                            Toast.makeText(MainActivity2.this, R.string.login_error, Toast.LENGTH_SHORT).show();
                            AppUtil.getInstance(MainActivity2.this).restartApp();
                        }

                        AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(true);
                        TimeOutUtil.getInstance().updatePin();
                        if(uri != null)    {
                            Log.i("PinEntryActivity", "uri to restartApp()");
                            AppUtil.getInstance(MainActivity2.this).restartApp("uri", uri);
                        }
                        else    {
                            AppUtil.getInstance(MainActivity2.this).restartApp();
                        }

                    }
                    catch (MnemonicException.MnemonicLengthException mle) {
                        mle.printStackTrace();
                    } catch (DecoderException de) {
                        de.printStackTrace();
                    } finally {
                        if (progress != null && progress.isShowing()) {
                            progress.dismiss();
                        }
                    }

                } else {
                    if (progress != null && progress.isShowing()) {
                        progress.dismiss();
                    }
                    Toast.makeText(MainActivity2.this, R.string.login_error, Toast.LENGTH_SHORT).show();
                    AppUtil.getInstance(MainActivity2.this).restartApp();
                }

                if (progress != null && progress.isShowing()) {
                    progress.dismiss();
                }

                Looper.loop();

            }
        }).start();

    }

    private void initThread(final boolean create, final String pin, final String passphrase, final String seed) {

        final ProgressDialog progress = new ProgressDialog(MainActivity2.this);

        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }

        progress.setCancelable(false);
        progress.setTitle(R.string.app_name);
        progress.setMessage(getString(R.string.please_wait));
        progress.show();

        new Thread(new Runnable() {
            @Override
            public void run() {

                Looper.prepare();

                String guid = AccessFactory.getInstance(MainActivity2.this).createGUID();
                String hash = AccessFactory.getInstance(MainActivity2.this).getHash(guid, new CharSequenceX(pin), AESUtil.DefaultPBKDF2Iterations);
                PrefsUtil.getInstance(MainActivity2.this).setValue(PrefsUtil.ACCESS_HASH, hash);
                PrefsUtil.getInstance(MainActivity2.this).setValue(PrefsUtil.ACCESS_HASH2, hash);

                if(create)	{

                    try {
                        HD_WalletFactory.getInstance(MainActivity2.this).newWallet(12, passphrase, SamouraiWallet.NB_ACCOUNTS);
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

                }
                else if(seed == null)	{
                    ;
                }
                else	{

                    try {
                        HD_WalletFactory.getInstance(MainActivity2.this).restoreWallet(seed, passphrase, SamouraiWallet.NB_ACCOUNTS);
                    }
                    catch(IOException ioe) {
                        ioe.printStackTrace();
                    }
                    catch(DecoderException de) {
                        de.printStackTrace();
                    }
                    catch(AddressFormatException afe) {
                        afe.printStackTrace();
                    }
                    catch(MnemonicException.MnemonicLengthException mle) {
                        mle.printStackTrace();
                    }
                    catch(MnemonicException.MnemonicChecksumException mce) {
                        mce.printStackTrace();
                    }
                    catch(MnemonicException.MnemonicWordException mwe) {
                        mwe.printStackTrace();
                    }
                    finally {
                        ;
                    }

                }

                PrefsUtil.getInstance(MainActivity2.this).setValue(PrefsUtil.SCRAMBLE_PIN, true);

                try {

                    String msg = null;

                    if(HD_WalletFactory.getInstance(MainActivity2.this).get() != null) {

                        if(create) {
                            msg = getString(R.string.wallet_created_ok);
                        }
                        else {
                            msg = getString(R.string.wallet_restored_ok);
                        }

                        try {
                            PayloadUtil.getInstance(MainActivity2.this).saveWalletToJSON(new CharSequenceX(AccessFactory.getInstance(MainActivity2.this).getGUID() + pin));
                            AccessFactory.getInstance(MainActivity2.this).setPIN(pin);

                            if(create) {
                                PrefsUtil.getInstance(MainActivity2.this).setValue(PrefsUtil.WALLET_ORIGIN, "new");
                                PrefsUtil.getInstance(MainActivity2.this).setValue(PrefsUtil.FIRST_RUN, true);
                            }
                            else {
                                PrefsUtil.getInstance(MainActivity2.this).setValue(PrefsUtil.WALLET_ORIGIN, "restored");
                                PrefsUtil.getInstance(MainActivity2.this).setValue(PrefsUtil.FIRST_RUN, true);
                            }

                        }
                        catch(JSONException je) {
                            je.printStackTrace();
                        }
                        catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                        catch (DecryptionException de) {
                            de.printStackTrace();
                        }
                        finally {
                            ;
                        }

                        for(int i = 0; i < 2; i++) {
                            AddressFactory.getInstance().account2xpub().put(i, HD_WalletFactory.getInstance(MainActivity2.this).get().getAccount(i).xpubstr());
                            AddressFactory.getInstance().xpub2account().put(HD_WalletFactory.getInstance(MainActivity2.this).get().getAccount(i).xpubstr(), i);
                        }

                        //
                        // backup wallet for alpha
                        //
                        if(create) {

                            String seed = null;
                            try {
                                seed = HD_WalletFactory.getInstance(MainActivity2.this).get().getMnemonic();
                            }
                            catch(IOException ioe) {
                                ioe.printStackTrace();
                            }
                            catch(MnemonicException.MnemonicLengthException mle) {
                                mle.printStackTrace();
                            }

                            new AlertDialog.Builder(MainActivity2.this)
                                    .setTitle(R.string.app_name)
                                    .setMessage(getString(R.string.alpha_create_wallet) + "\n\n" + seed)
                                    .setCancelable(false)
                                    .setPositiveButton(R.string.alpha_create_confirm_backup, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {

                                            AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(true);
                                            TimeOutUtil.getInstance().updatePin();
                                            AppUtil.getInstance(MainActivity2.this).restartApp();

                                        }
                                    }).show();

                        }
                        else {
                            AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(true);
                            TimeOutUtil.getInstance().updatePin();
                            AppUtil.getInstance(MainActivity2.this).restartApp();
                        }

                    }
                    else {
                        if(create) {
                            msg = getString(R.string.wallet_created_ko);
                        }
                        else {
                            msg = getString(R.string.wallet_restored_ko);
                        }
                    }

                    Toast.makeText(MainActivity2.this, msg, Toast.LENGTH_SHORT).show();

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

                if (progress != null && progress.isShowing()) {
                    progress.dismiss();
                }

                Intent intent = new Intent(MainActivity2.this, BalanceActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                Looper.loop();

            }
        }).start();

    }

    private String createPin()  {

        SecureRandom random = new SecureRandom();
        int val = random.nextInt(100000000);
        String strPin = String.format("%08d", val);

        PrefsUtil.getInstance(MainActivity2.this).setValue(PrefsUtil.PIN, strPin);

        return  strPin;
    }

    private String getPin()  {

        return PrefsUtil.getInstance(MainActivity2.this).getValue(PrefsUtil.PIN, "");

    }

}
