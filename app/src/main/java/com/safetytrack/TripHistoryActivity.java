package com.safetytrack;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripHistoryActivity extends AppCompatActivity {
    private ListView listViewTrips;
    private TextView tvNoTrips;
    private ArrayAdapter<String> adapter;
    private List<String> tripHistoryList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_history);

        initializeViews();
        loadTripHistory();
    }

    private void initializeViews() {
        listViewTrips = findViewById(R.id.listViewTrips);
        tvNoTrips = findViewById(R.id.tvNoTrips);

        tripHistoryList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, tripHistoryList);
        listViewTrips.setAdapter(adapter);
    }

    private void loadTripHistory() {
        // For now, show sample data
        tripHistoryList.clear();
        tripHistoryList.add("Start: Jan 25, 10:30\nEnd: Jan 25, 11:15\nDuration: 45 mins\nStatus: ✅ completed");
        tripHistoryList.add("Start: Jan 24, 14:20\nEnd: Jan 24, 15:10\nDuration: 50 mins\nStatus: ✅ completed");
        tripHistoryList.add("Start: Jan 23, 09:15\nEnd: Jan 23, 09:45\nDuration: 30 mins\nStatus: ✅ completed");

        adapter.notifyDataSetChanged();

        if (tripHistoryList.isEmpty()) {
            tvNoTrips.setText("No trip history found");
            tvNoTrips.setVisibility(android.view.View.VISIBLE);
        } else {
            tvNoTrips.setVisibility(android.view.View.GONE);
        }
    }
}