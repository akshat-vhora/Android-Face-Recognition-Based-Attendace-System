package com.attendance.system;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 3000; // 3 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        setContentView(R.layout.activity_splash);

        // Get the ImageView (Logo) and set fade-in animation
        ImageView splashImage = findViewById(R.id.splashImage);
        startFadeInAnimation(splashImage);

        // Delay for the splash duration and then move to the LectureSelectionActivity
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, AuthActivity.class);
            startActivity(intent);
            finish(); // Finish SplashActivity to remove it from back stack
        }, SPLASH_DURATION);
    }

    // Method for fade-in animation
    private void startFadeInAnimation(ImageView imageView) {
        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setDuration(1500); // Duration of fade-in animation (1.5 seconds)
        imageView.startAnimation(fadeIn);
    }
}