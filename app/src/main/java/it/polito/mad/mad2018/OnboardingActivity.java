package it.polito.mad.mad2018;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

public class OnboardingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);

        findViewById(R.id.splash).setOnClickListener(v -> finish());
        ImageView imageView = findViewById(R.id.splash_image);
        TextView textView = findViewById(R.id.splash_text);
        Animation animation_image = AnimationUtils.loadAnimation(this, R.anim.from_top);
        Animation animation_text = AnimationUtils.loadAnimation(this, R.anim.from_bottom);

        imageView.setAnimation(animation_image);
        textView.setAnimation(animation_text);
    }
}
