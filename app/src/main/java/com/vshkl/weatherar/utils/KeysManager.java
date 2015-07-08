package com.vshkl.weatherar.utils;

import android.content.Context;
import android.content.res.Resources;

import com.vshkl.weatherar.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class KeysManager {

    public static String getKey(Context context, String name) {
        Resources resources = context.getResources();
        InputStream stream = resources.openRawResource(R.raw.license);
        String licenseKey = "";
        Properties properties = new Properties();

        try {
            properties.load(stream);
            licenseKey = properties.getProperty(name);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return licenseKey;
    }
}
