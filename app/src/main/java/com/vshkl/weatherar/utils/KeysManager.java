package com.vshkl.weatherar.utils;

import android.content.Context;
import android.content.res.Resources;

import com.vshkl.weatherar.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Class for managing license keys
 * Keys stores in /res/raw/license.properties file and dont track with git
 */
public class KeysManager {

    /**
     * Method retrieves license key by name from license.properties file
     *
     * @param context is an application context
     * @param name    is a license key name
     * @return license key as {@link String}
     */
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
