package com.example.serviceproviderBuilder;

import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.example.common.services.IService;
import com.tablebird.serviceproviderbuilder.ServiceProviderBuilder;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        IService iService = ServiceProviderBuilder.buildSingleService(IService.class);
        TextView textView = findViewById(R.id.text);
        if (iService != null) {
            textView.setText(iService.getName());
        }
    }
}
