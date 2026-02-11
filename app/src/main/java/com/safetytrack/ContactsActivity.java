//ContactsActivity.java
package com.safetytrack;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.safetytrack.models.Contact;
import com.safetytrack.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContactsActivity extends AppCompatActivity {
    private static final String TAG = "ContactsActivity";
    private static final int CONTACTS_PERMISSION_REQUEST = 100;

    private ListView listViewContacts;
    private Button btnSaveContacts;
    private ProgressBar progressBar;
    private TextView tvEmptyState;

    private List<Contact> contactList;
    private Set<String> selectedContactsSet;
    private Map<String, Boolean> selectedStateMap; // ✅ Store selected state by phone number
    private ContactsAdapter contactsAdapter;
    private FirebaseHelper firebaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        firebaseHelper = new FirebaseHelper(this);
        initializeViews();
        initializeData();
        requestContactsPermission();
        setupListeners();
    }

    private void initializeViews() {
        listViewContacts = findViewById(R.id.listViewContacts);
        btnSaveContacts = findViewById(R.id.btnSaveContacts);
        progressBar = findViewById(R.id.progressBar);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        progressBar.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.GONE);
    }

    private void initializeData() {
        contactList = new ArrayList<>();
        selectedContactsSet = new HashSet<>();
        selectedStateMap = new HashMap<>(); // ✅ Initialize

        // Load previously selected contacts from SharedPreferences (temporary until Firestore loads)
        SharedPreferences prefs = getSharedPreferences("SafetyTrack", MODE_PRIVATE);
        String savedContacts = prefs.getString("selectedContacts", "");

        if (!savedContacts.isEmpty()) {
            String[] contactsArray = savedContacts.split(",");
            for (String contact : contactsArray) {
                if (!contact.trim().isEmpty()) {
                    selectedContactsSet.add(contact.trim());
                }
            }
        }
    }

    private void requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContactsFromFirestore();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    CONTACTS_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACTS_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContactsFromFirestore();
            } else {
                tvEmptyState.setVisibility(View.VISIBLE);
                tvEmptyState.setText("Contacts permission is required to select emergency contacts");
                Toast.makeText(this, "Contacts permission required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadContactsFromFirestore() {
        progressBar.setVisibility(View.VISIBLE);

        firebaseHelper.loadContactsFromFirestore(new FirebaseHelper.FirebaseContactsListener() {
            @Override
            public void onSuccess(List<Contact> contacts) {
                // ✅ Store selected state from Firestore contacts
                selectedStateMap.clear();
                for (Contact contact : contacts) {
                    if (contact.isSelected()) {
                        selectedStateMap.put(contact.getPhoneNumber(), true);
                        selectedContactsSet.add(contact.toString());
                    }
                }

                // Load device contacts
                loadDeviceContacts();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading contacts from Firestore: " + error);
                progressBar.setVisibility(View.GONE);
                // Still load device contacts even if Firestore fails
                loadDeviceContacts();
            }
        });
    }

    private void loadDeviceContacts() {
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        );

        if (cursor != null && cursor.getCount() > 0) {
            contactList.clear();

            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));

                if (Integer.parseInt(cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {

                    Cursor phoneCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id},
                            null
                    );

                    if (phoneCursor != null) {
                        while (phoneCursor.moveToNext()) {
                            String phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.Phone.NUMBER));

                            phoneNumber = formatPhoneNumber(phoneNumber);
                            String contactInfo = name + "\n" + phoneNumber;

                            Contact contact = new Contact(name, phoneNumber);
                            contact.setRawContactInfo(contactInfo);

                            // ✅ Check if this contact was previously selected using phone number
                            if (selectedStateMap.containsKey(phoneNumber) || selectedContactsSet.contains(contactInfo)) {
                                contact.setSelected(true);
                            }

                            contactList.add(contact);
                        }
                        phoneCursor.close();
                    }
                }
            }
            cursor.close();

            if (contactList.isEmpty()) {
                tvEmptyState.setVisibility(View.VISIBLE);
                tvEmptyState.setText("No contacts with phone numbers found");
            } else {
                tvEmptyState.setVisibility(View.GONE);
                contactsAdapter = new ContactsAdapter(this, contactList);
                listViewContacts.setAdapter(contactsAdapter);
                listViewContacts.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            }
        } else {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText("No contacts found on device");
        }

        progressBar.setVisibility(View.GONE);
    }

    private String formatPhoneNumber(String phone) {
        return phone.replaceAll("[^0-9+]", "");
    }

    private void setupListeners() {
        btnSaveContacts.setOnClickListener(v -> saveSelectedContacts());

        listViewContacts.setOnItemClickListener((parent, view, position, id) -> {
            Contact contact = contactList.get(position);
            contact.setSelected(!contact.isSelected());
            contactsAdapter.notifyDataSetChanged();
        });
    }

    private void saveSelectedContacts() {
        progressBar.setVisibility(View.VISIBLE);
        btnSaveContacts.setEnabled(false);

        List<Contact> selectedContactsList = new ArrayList<>();
        StringBuilder contactsBuilder = new StringBuilder();

        for (Contact contact : contactList) {
            if (contact.isSelected()) {
                selectedContactsList.add(contact);
                contactsBuilder.append(contact.toString()).append(",");
            }
        }

        // Save to SharedPreferences (for backward compatibility)
        SharedPreferences prefs = getSharedPreferences("SafetyTrack", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("selectedContacts", contactsBuilder.toString());
        editor.putString("emergencyContacts", contactsBuilder.toString());
        editor.apply();

        // ✅ Save to Firestore - NOW INCLUDES SELECTED STATE
        firebaseHelper.saveContactsToFirestore(selectedContactsList,
                new FirebaseHelper.FirebaseCompleteListener() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnSaveContacts.setEnabled(true);

                            Toast.makeText(ContactsActivity.this,
                                    selectedContactsList.size() + " contacts saved successfully",
                                    Toast.LENGTH_SHORT).show();

                            // Set result and finish
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("contacts_count", selectedContactsList.size());
                            setResult(RESULT_OK, resultIntent);
                            finish();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnSaveContacts.setEnabled(true);

                            Toast.makeText(ContactsActivity.this,
                                    "Failed to save contacts: " + error,
                                    Toast.LENGTH_LONG).show();

                            // Still finish but with error
                            finish();
                        });
                    }
                });
    }
}