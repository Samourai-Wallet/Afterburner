package com.samourai.wallet.hd;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
//import android.util.Log;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.params.MainNetParams;

import com.samourai.wallet.util.AppUtil;
import com.samourai.wallet.util.FormatsUtil;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//import org.apache.commons.lang.ArrayUtils;

public class HD_WalletFactory	{

    public static final String BIP39_ENGLISH_SHA256 = "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db";

    private static HD_WalletFactory instance = null;
    private static List<HD_Wallet> wallets = null;

    private static String dataDir = "wallet";
    private static String strFilename = "samourai.dat";
    private static String strTmpFilename = "samourai.tmp";
    private static String strBackupFilename = "samourai.sav";

    private static Context context = null;

    private HD_WalletFactory()	{ ; }

    public static HD_WalletFactory getInstance(Context ctx) {

        context = ctx;

        if (instance == null) {
            wallets = new ArrayList<HD_Wallet>();
            instance = new HD_WalletFactory();
        }

        return instance;
    }

    public HD_Wallet newWallet(int nbWords, String passphrase, int nbAccounts) throws IOException, MnemonicException.MnemonicLengthException   {

        HD_Wallet hdw = null;

        if((nbWords % 3 != 0) || (nbWords < 12 || nbWords > 24)) {
            nbWords = 12;
        }

        // len == 16 (12 words), len == 24 (18 words), len == 32 (24 words)
        int len = (nbWords / 3) * 4;

        if(passphrase == null) {
            passphrase = "";
        }

        NetworkParameters params = MainNetParams.get();

        AppUtil.getInstance(context).applyPRNGFixes();
        SecureRandom random = new SecureRandom();
        byte seed[] = new byte[len];
        random.nextBytes(seed);

        InputStream wis = context.getResources().getAssets().open("BIP39/en.txt");
        if(wis != null) {
            MnemonicCode mc = new MnemonicCode(wis, BIP39_ENGLISH_SHA256);
            hdw = new HD_Wallet(44, mc, params, seed, passphrase, nbAccounts);
            wis.close();
        }

        wallets.clear();
        wallets.add(hdw);

        return hdw;
    }

    public HD_Wallet restoreWallet(String data, String passphrase, int nbAccounts) throws AddressFormatException, IOException, DecoderException, MnemonicException.MnemonicLengthException, MnemonicException.MnemonicWordException, MnemonicException.MnemonicChecksumException  {

        HD_Wallet hdw = null;

        if(passphrase == null) {
            passphrase = "";
        }

        NetworkParameters params = MainNetParams.get();

        InputStream wis = context.getResources().getAssets().open("BIP39/en.txt");
        if(wis != null) {
            List<String> words = null;

            MnemonicCode mc = null;
            mc = new MnemonicCode(wis, BIP39_ENGLISH_SHA256);

            byte[] seed = null;
            if(data.matches(FormatsUtil.XPUB)) {
                String[] xpub = data.split(":");
                hdw = new HD_Wallet(params, xpub);
            }
            else if(data.matches(FormatsUtil.HEX) && data.length() % 4 == 0) {
                seed = Hex.decodeHex(data.toCharArray());
                hdw = new HD_Wallet(44, mc, params, seed, passphrase, nbAccounts);
            }
            else {
                data = data.replaceAll("[^a-z]+", " ");             // only use for BIP39 English
                words = Arrays.asList(data.trim().split("\\s+"));
                seed = mc.toEntropy(words);
                hdw = new HD_Wallet(44, mc, params, seed, passphrase, nbAccounts);
            }

            wis.close();

        }

        wallets.clear();
        wallets.add(hdw);

        return hdw;
    }

    public HD_Wallet get() throws IOException, MnemonicException.MnemonicLengthException {

        if(wallets == null || wallets.size() < 1) {
            return null;
        }

        return wallets.get(0);
    }

    public void set(HD_Wallet wallet)	{

        if(wallet != null)	{
            wallets.clear();
            wallets.add(wallet);
        }

    }

    public boolean holding()	{
        return (wallets.size() > 0);
    }

    public List<HD_Wallet> getWallets()    {
        return wallets;
    }

}
