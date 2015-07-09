package com.vshkl.weatherar.views;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.survivingwithandroid.weather.lib.WeatherClient;
import com.survivingwithandroid.weather.lib.WeatherConfig;
import com.survivingwithandroid.weather.lib.exception.WeatherLibException;
import com.survivingwithandroid.weather.lib.exception.WeatherProviderInstantiationException;
import com.survivingwithandroid.weather.lib.model.City;
import com.survivingwithandroid.weather.lib.model.DayForecast;
import com.survivingwithandroid.weather.lib.model.WeatherForecast;
import com.survivingwithandroid.weather.lib.provider.openweathermap.OpenweathermapProviderType;
import com.survivingwithandroid.weather.lib.request.WeatherRequest;
import com.vshkl.weatherar.R;
import com.vshkl.weatherar.utils.KeysManager;

import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ItemClick;
import org.androidannotations.annotations.TextChange;
import org.androidannotations.annotations.ViewById;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity {

    private WeatherClient client;
    private WeatherRequest request;

    private ListAdapter listAdapter;
    private Map<String, String> citiesMap = new HashMap<>();

    private Objects lock;

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
                listAdapter = new ArrayAdapter<>(getApplicationContext(),
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
        request = new WeatherRequest(citiesMap.get(city));
        client.getForecastWeather(request, new WeatherClient.ForecastWeatherEventListener() {
            @Override
            public void onWeatherRetrieved(WeatherForecast weatherForecast) {
                List<DayForecast> weather = weatherForecast.getForecast();
                Calendar calendar = Calendar.getInstance();
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Forecast:\n");
                for (DayForecast day : weather) {
                    calendar.setTimeInMillis(day.timestamp * 1000l);
                    stringBuilder.append(calendar.get(Calendar.DAY_OF_MONTH))
                            .append(".")
                            .append(calendar.get(Calendar.MONTH))
                            .append(" ")
                            .append((int) day.forecastTemp.min)
                            .append(" / ")
                            .append((int) day.forecastTemp.max)
                            .append(" °C")
                            .append('\n');
                }
                Log.v("FORECAST", stringBuilder.toString());
                Intent intent = new Intent(getApplicationContext(), CameraActivity_.class);
                intent.putExtra("WeatherStr", stringBuilder.toString());
                startActivity(intent);
            }

            @Override
            public void onWeatherError(WeatherLibException e) {}

            @Override
            public void onConnectionError(Throwable throwable) {}
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        createWeather(this);
        super.onCreate(savedInstanceState);
    }

    public void createWeather(Context context) {
        WeatherClient.ClientBuilder builder = new WeatherClient.ClientBuilder();
        WeatherConfig config = new WeatherConfig();
        config.ApiKey = KeysManager.getKey(context, "openweathermap_api_key");
        config.unitSystem = WeatherConfig.UNIT_SYSTEM.M;
        config.numDays = 7;
        config.lang = "en";

        try {
            client = builder.attach(context)
                    .provider(new OpenweathermapProviderType())
                    .httpClient(com.survivingwithandroid.weather.lib.client.volley
                            .WeatherClientDefault.class)
                    .config(config)
                    .build();
        } catch (WeatherProviderInstantiationException e) {
            e.printStackTrace();
        }
    }
}
