package com.samourai.wallet;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.dm.zbar.android.scanner.ZBarConstants;
import com.dm.zbar.android.scanner.ZBarScannerActivity;
import com.samourai.afterburner.R;
import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.crypto.AESUtil;
import com.samourai.wallet.crypto.DecryptionException;
import com.samourai.wallet.hd.HD_WalletFactory;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.util.AddressFactory;
import com.samourai.wallet.util.AppUtil;
import com.samourai.wallet.util.CharSequenceX;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.wallet.util.PrefsUtil;

import net.sourceforge.zbar.Symbol;

import org.apache.commons.codec.DecoderException;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

public class InitActivity extends Activity {

    private static final int SCAN_SEED = 2075;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_init);

        Button btImportWallet = (Button)findViewById(R.id.button2);
        btImportWallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initDialog();
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(resultCode == Activity.RESULT_OK && requestCode == SCAN_SEED)	{

            if(data != null && data.getStringExtra(ZBarConstants.SCAN_RESULT) != null)	{

                final String strResult = data.getStringExtra(ZBarConstants.SCAN_RESULT);

                if(isBIP39Mnemonic(strResult))    {
                    Intent intent = new Intent(InitActivity.this, PinEntryActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("create", true);
                    intent.putExtra("seed", strResult);
                    intent.putExtra("passphrase", "");
                    startActivity(intent);
                }
                else    {
                    Toast.makeText(InitActivity.this, R.string.invalid_mnemonic, Toast.LENGTH_SHORT).show();
                    AppUtil.getInstance(InitActivity.this).restartApp();
                }

            }
        }
        else if(resultCode == Activity.RESULT_CANCELED && requestCode == SCAN_SEED)	{
            AppUtil.getInstance(InitActivity.this).restartApp();
        }
        else {
            AppUtil.getInstance(InitActivity.this).restartApp();
        }

    }

    private void doScan() {
        Intent intent = new Intent(InitActivity.this, ZBarScannerActivity.class);
        intent.putExtra(ZBarConstants.SCAN_MODES, new int[]{ Symbol.QRCODE } );
        startActivityForResult(intent, SCAN_SEED);
    }

    private void initDialog()	{

        AccessFactory.getInstance(InitActivity.this).setIsLoggedIn(false);

        AlertDialog.Builder dlg = new AlertDialog.Builder(InitActivity.this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.restore_wallet)
                .setCancelable(false)
                .setIcon(R.drawable.ic_launcher)
                .setPositiveButton(R.string.import_scan, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        doScan();
                        dialog.dismiss();

                    }
                })
                .setNegativeButton(R.string.import_mnemonic, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        final EditText mnemonic = new EditText(InitActivity.this);
                        mnemonic.setHint(R.string.mnemonic_hex);
                        mnemonic.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                        final EditText passphrase = new EditText(InitActivity.this);
                        passphrase.setHint(R.string.bip39_passphrase);
                        passphrase.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                        passphrase.setSingleLine(true);

                        LinearLayout restoreLayout = new LinearLayout(InitActivity.this);
                        restoreLayout.setOrientation(LinearLayout.VERTICAL);
                        restoreLayout.addView(mnemonic);
                        restoreLayout.addView(passphrase);

                        AlertDialog.Builder dlg = new AlertDialog.Builder(InitActivity.this)
                                .setTitle(R.string.app_name)
                                .setMessage(R.string.bip39_restore)
                                .setView(restoreLayout)
                                .setCancelable(false)
                                .setIcon(R.drawable.ic_launcher)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {

                                        final String seed39 = mnemonic.getText().toString();
                                        final String passphrase39 = passphrase.getText().toString();

                                        if (seed39 != null && seed39.length() > 0 && isBIP39Mnemonic(seed39)) {

                                            Intent intent = new Intent(InitActivity.this, PinEntryActivity.class);
                                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                            intent.putExtra("create", true);
                                            intent.putExtra("seed", seed39);
                                            intent.putExtra("passphrase", passphrase39 == null ? "" : passphrase39);
                                            startActivity(intent);

                                        } else {

                                            Toast.makeText(InitActivity.this, R.string.invalid_mnemonic, Toast.LENGTH_SHORT).show();
                                            AppUtil.getInstance(InitActivity.this).restartApp();

                                        }

                                        dialog.dismiss();

                                    }
                                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {

                                        AppUtil.getInstance(InitActivity.this).restartApp();

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

    private boolean isBIP39Mnemonic(String mnemonic)   {

        boolean ret = false;

        if(mnemonic.matches(FormatsUtil.XPUB)) {
            ret = false;
        }
        else if(mnemonic.matches(FormatsUtil.HEX) && mnemonic.length() % 4 == 0) {

            try {
                InputStream wis = InitActivity.this.getResources().getAssets().open("BIP39/en.txt");
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
                    InputStream wis = InitActivity.this.getResources().getAssets().open("BIP39/en.txt");
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

}
