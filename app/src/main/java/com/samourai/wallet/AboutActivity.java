package com.samourai.wallet;

import android.app.Activity;
import android.os.Bundle;

import com.samourai.wallet.util.AppUtil;

import com.samourai.afterburner.R;

public class AboutActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setTitle("Afterburner, v" + getResources().getString(R.string.version_name));
    }

    @Override
    public void onResume() {
        super.onResume();

        AppUtil.getInstance(AboutActivity.this).checkTimeOut();

    }
}
