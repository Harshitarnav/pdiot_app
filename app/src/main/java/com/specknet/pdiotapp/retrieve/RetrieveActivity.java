package com.specknet.pdiotapp.retrieve;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.specknet.pdiotapp.R;

import java.util.Objects;

public class RetrieveActivity extends AppCompatActivity {
    FirebaseFirestore store;
    FirebaseAuth auth;
    String userID;

    TextView lying, run, sit, stair, walk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retrieve);
        lying = findViewById(R.id.textView1);
        run = findViewById(R.id.textView2);
        sit = findViewById(R.id.textView3);
        stair = findViewById(R.id.textView4);
        walk = findViewById(R.id.textView5);

        auth = FirebaseAuth.getInstance();
        store = FirebaseFirestore.getInstance();

        userID = auth.getCurrentUser().getUid();

        DocumentReference doc_ref = store.collection("users").document(userID);
        doc_ref.addSnapshotListener(this, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                assert value != null;
                Log.i("val", Objects.requireNonNull(value.get("Lying down")).toString());
                lying.setText(value.get("Lying down").toString());
                run.setText(value.get("Running").toString());
                sit.setText(value.get("Sitting,Standing").toString());
                stair.setText(value.get("Stairs").toString());
                walk.setText(value.get("Walking").toString());
            }
        });
    }
}