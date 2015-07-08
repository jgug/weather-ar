package com.vshkl.weatherar.views;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.survivingwithandroid.weather.lib.WeatherClient;
import com.survivingwithandroid.weather.lib.WeatherConfig;
import com.survivingwithandroid.weather.lib.exception.WeatherLibException;
import com.survivingwithandroid.weather.lib.exception.WeatherProviderInstantiationException;
import com.survivingwithandroid.weather.lib.model.City;
import com.survivingwithandroid.weather.lib.model.CurrentWeather;
import com.survivingwithandroid.weather.lib.model.Weather;
import com.survivingwithandroid.weather.lib.provider.openweathermap.OpenweathermapProviderType;
import com.survivingwithandroid.weather.lib.request.WeatherRequest;
import com.vshkl.weatherar.R;
import com.vshkl.weatherar.utils.KeysManager;

import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ItemClick;
import org.androidannotations.annotations.TextChange;
import org.androidannotations.annotations.ViewById;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity {

    private WeatherClient.ClientBuilder builder;
    private WeatherConfig config;
    private WeatherClient client;
    private WeatherRequest request;

    private ListAdapter listAdapter;
    private Map<String, String> citiesMap = new HashMap<>();

    @ViewById(R.id.cities)
    ListView citiesList;

    @TextChange(R.id.searchCity)
    void onTextChangesOnSearchCity(TextView s) {
        client.searchCity(s.getText().toString(), new WeatherClient.CityEventListener() {
            @Override
            public void onCityListRetrieved(List<City> list) {
                List<String> cities = new ArrayList<>();
                for (City c : list) {
                    citiesMap.put(c.toString(), c.getId());
                    cities.add(c.toString());
                }
                listAdapter= new ArrayAdapter<>(getApplicationContext(),
                        android.R.layout.simple_list_item_1, cities);
                citiesList.setAdapter(listAdapter);
            }

            @Override
            public void onWeatherError(WeatherLibException e) {
                e.printStackTrace();
            }

            @Override
            public void onConnectionError(Throwable throwable) {
            }
        });
    }

    @ItemClick
    void citiesItemClicked(String city) {
        Toast.makeText(
                getApplicationContext(),
                citiesMap.get(city),
                Toast.LENGTH_SHORT
        ).show();
        startActivity(new Intent(this, CameraActivity_.class));

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        createWeather();
        super.onCreate(savedInstanceState);
    }

    private void createWeather() {
        builder = new WeatherClient.ClientBuilder();
        config = new WeatherConfig();
        config.ApiKey = KeysManager.getKey(getApplicationContext(), "openweathermap_api_key");
        config.unitSystem = WeatherConfig.UNIT_SYSTEM.M;
        config.lang = "en";

        try {
            client = builder.attach(this)
                    .provider(new OpenweathermapProviderType())
                    .httpClient(com.survivingwithandroid.weather.lib.client.volley
                            .WeatherClientDefault.class)
                    .config(config)
                    .build();
        } catch (WeatherProviderInstantiationException e) {
            e.printStackTrace();
        }
    }

    private void getCurrentConditions() {
        client.getCurrentCondition(request, new WeatherClient.WeatherEventListener() {
            @Override
            public void onWeatherRetrieved(CurrentWeather currentWeather) {
                Weather weather = currentWeather.weather;
                Log.v("TEMP", String.valueOf(weather.temperature.getTemp()));
            }

            @Override
            public void onWeatherError(WeatherLibException e) {
                e.printStackTrace();
            }

            @Override
            public void onConnectionError(Throwable throwable) {
                throwable.printStackTrace();
            }
        });
    }
}
