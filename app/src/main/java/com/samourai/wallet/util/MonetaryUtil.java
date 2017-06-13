package com.samourai.wallet.util;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public class MonetaryUtil {

//    private static CharSequence[] btcUnits = { "BTC", "mBTC", "µBTC" };
    private static CharSequence[] btcUnits = { "BTC", "mBTC", "bits" };
    public static final int UNIT_BTC = 0;
    public static final int MILLI_BTC = 1;
    public static final int MICRO_BTC = 2;

    private static MonetaryUtil instance = null;
	private static NumberFormat btcFormat = null;
	private static NumberFormat fiatFormat = null;

	private MonetaryUtil() { ; }

	public static MonetaryUtil getInstance() {
		
		if(instance == null) {
        	fiatFormat = NumberFormat.getInstance(Locale.getDefault());
        	fiatFormat.setMaximumFractionDigits(2);
        	fiatFormat.setMinimumFractionDigits(2);

        	btcFormat = NumberFormat.getInstance(Locale.getDefault());
        	btcFormat.setMaximumFractionDigits(8);
        	btcFormat.setMinimumFractionDigits(1);

			instance = new MonetaryUtil();
		}
		
		return instance;
	}

	public NumberFormat getBTCFormat() {
		return btcFormat;
	}

	public NumberFormat getFiatFormat(String fiat) {
    	fiatFormat.setCurrency(Currency.getInstance(fiat));
		return fiatFormat;
	}

    public CharSequence[] getBTCUnits() {
        return btcUnits;
    }

}
