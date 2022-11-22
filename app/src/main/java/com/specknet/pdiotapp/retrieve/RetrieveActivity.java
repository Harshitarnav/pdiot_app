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


//    TextView climbing_stairs, descending_stairs, desk_work, lying_left, lying_back, lying_stomach, lying_right, sitting, standing, walking,
//            running, movement, sitting_back, sitting_for;
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_retrieve_14);
//
//        climbing_stairs = findViewById(R.id.textView1);
//        descending_stairs = findViewById(R.id.textView2);
//        desk_work = findViewById(R.id.textView3);
//        lying_left = findViewById(R.id.textView4);
//        lying_back = findViewById(R.id.textView5);
//        lying_stomach = findViewById(R.id.textView6);
//        lying_right = findViewById(R.id.textView7);
//        movement = findViewById(R.id.textView8);
//        running = findViewById(R.id.textView9);
//        sitting = findViewById(R.id.textView10);
//        sitting_back = findViewById(R.id.textView11);
//        sitting_for = findViewById(R.id.textView12);
//        standing = findViewById(R.id.textView13);
//        walking = findViewById(R.id.textView14);
//
//        auth = FirebaseAuth.getInstance();
//        store = FirebaseFirestore.getInstance();
//
//        userID = auth.getCurrentUser().getUid();
//
//        DocumentReference doc_ref = store.collection("users").document(userID);
//        doc_ref.addSnapshotListener(this, new EventListener<DocumentSnapshot>() {
//            @Override
//            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
//                assert value != null;
//                climbing_stairs.setText(value.get("Climbing stairs").toString());
//                descending_stairs.setText(value.get("Descending stairs").toString());
//                desk_work.setText(value.get("Desk work").toString());
//                lying_left.setText(value.get("Lying down left").toString());
//                lying_back.setText(value.get("Lying down on back").toString());
//                lying_stomach.setText(value.get("Lying down on stomach").toString());
//                lying_right.setText(value.get("Lying down right").toString());
//                movement.setText(value.get("Movement").toString());
//                running.setText(value.get("Running").toString());
//                sitting.setText(value.get("Sitting").toString());
//                sitting_back.setText(value.get("Sitting bent backward").toString());
//                sitting_for.setText(value.get("Sitting bent forward").toString());
//                standing.setText(value.get("Standing").toString());
//                walking.setText(value.get("Walking at normal speed").toString());
//
//                //                    "Climbing stairs",
////                            "Descending stairs",
////                            "Desk work",
////                            "Lying down left",
////                            "Lying down on back",
////                            "Lying down on stomach",
////                            "Lying down right",
////                            "Movement",
////                            "Running",
////                            "Sitting",
////                            "Sitting bent backward",
////                            "Sitting bent forward",
////                            "Standing",
////                            "Walking at normal speed"
////                lying.setText(value.get("Lying down").toString());
////                run.setText(value.get("Running").toString());
////                sit.setText(value.get("Sitting,Standing").toString());
////                stair.setText(value.get("Stairs").toString());
////                walk.setText(value.get("Walking").toString());
////                lying.setText(value.get("Lying Down").toString());
////                run.setText(value.get("Running").toString());
////                sit.setText(value.get("Sitting,Standing").toString());
////                stair.setText(value.get("Stairs").toString());
////                walk.setText(value.get("Walking").toString());
////                lying.setText(value.get("Lying Down").toString());
////                run.setText(value.get("Running").toString());
////                sit.setText(value.get("Sitting,Standing").toString());
////                stair.setText(value.get("Stairs").toString());
//            }
//        });
//    }



//    TextView lying4, run4, sit4, walk4;
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_retrieve_4);
//        lying4 = findViewById(R.id.textView1);
//        run4 = findViewById(R.id.textView2);
//        sit4 = findViewById(R.id.textView3);
//        walk4 = findViewById(R.id.textView5);
//
//        auth = FirebaseAuth.getInstance();
//        store = FirebaseFirestore.getInstance();
//
//        userID = auth.getCurrentUser().getUid();
//
//        DocumentReference doc_ref = store.collection("users").document(userID);
//        doc_ref.addSnapshotListener(this, new EventListener<DocumentSnapshot>() {
//            @Override
//            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
//                assert value != null;
//                lying4.setText(value.get("Lying down").toString());
//                run4.setText(value.get("Running").toString());
//                sit4.setText(value.get("Sitting,Standing").toString());
//                walk4.setText(value.get("Walking").toString());
//            }
//        });
//    }
//}