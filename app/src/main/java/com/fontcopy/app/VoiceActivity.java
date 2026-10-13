package com.fontcopy.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;
import java.util.ArrayList;

public class VoiceActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String lang = getIntent().getStringExtra("lang");
        if (lang == null) lang = "ar-EG";
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "اتكلم دلوقتي");
        try {
            startActivityForResult(i, 77);
        } catch (Exception e) {
            Toast.makeText(this, "الكتابة بالصوت مش متاحة على الجهاز", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 77 && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) {
                getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).edit()
                        .putString("voice", r.get(0))
                        .putLong("voice_ts", System.currentTimeMillis())
                        .apply();
            }
        }
        finish();
    }
}
