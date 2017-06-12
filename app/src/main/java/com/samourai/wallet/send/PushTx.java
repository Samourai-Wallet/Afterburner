package com.samourai.wallet.send;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.samourai.afterburner.R;
import com.samourai.wallet.util.WebUtil;

import org.json.JSONException;
import org.json.JSONObject;

public class PushTx {

    private static boolean DO_SPEND = true;

    private static PushTx instance = null;
    private static Context context = null;

    private PushTx() { ; }

    public static PushTx getInstance(Context ctx) {

        context = ctx;

        if(instance == null) {
            instance = new PushTx();
        }

        return instance;
    }

    public String chainSo(String hexString) {

        try {
            String response = WebUtil.getInstance(null).postURL(WebUtil.CHAINSO_PUSHTX_URL, "tx_hex=" + hexString);
            return response;
        }
        catch(Exception e) {
            return null;
        }

    }

    public String blockchain(String hexString) {

        try {
            String response = WebUtil.getInstance(null).postURL(WebUtil.BLOCKCHAIN_DOMAIN + "pushtx", "tx=" + hexString);
            return response;
        }
        catch(Exception e) {
            return null;
        }

    }

    public String samourai(String hexString) {

        try {
            String response = WebUtil.getInstance(context).postURL(WebUtil.SAMOURAI_API + "v1/pushtx", "tx=" + hexString);
            return response;
        }
        catch(Exception e) {
            return null;
        }

    }

    public boolean pushTx(String hexTx) {

        String response = null;
        boolean isOK = false;

        try {
            if(DO_SPEND)    {
                response = PushTx.getInstance(context).samourai(hexTx);
                if(response != null)    {
                    JSONObject jsonObject = new org.json.JSONObject(response);
                    if(jsonObject.has("status"))    {
                        if(jsonObject.getString("status").equals("ok"))    {
                            isOK = true;
                        }
                    }
                }
                else    {
                    Toast.makeText(context, R.string.pushtx_returns_null, Toast.LENGTH_SHORT).show();
                }
            }
            else    {
                Log.d("PushTx", hexTx);
                isOK = true;
            }

            if(isOK)    {
                Toast.makeText(context, R.string.tx_sent, Toast.LENGTH_SHORT).show();
            }
            else    {
                Toast.makeText(context, R.string.tx_failed, Toast.LENGTH_SHORT).show();
            }

        }
        catch(JSONException je) {
            Toast.makeText(context, "pushTx:" + je.getMessage(), Toast.LENGTH_SHORT).show();
        }

        return isOK;

    }

}
