package com.vshkl.weatherar.views;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.vshkl.weatherar.R;

import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;

@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity {

    @Click(R.id.buttonShow)
    void show() {
        startActivity(new Intent(this, CameraActivity_.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
