package com.specknet.pdiotapp.Login;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Intent;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.specknet.pdiotapp.R;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText email;
    private EditText password;
    private Button register;

    private FirebaseAuth auth;
    private FirebaseFirestore store;
    String userID;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        register = findViewById(R.id.register);

        auth = FirebaseAuth.getInstance();
        store = FirebaseFirestore.getInstance();

        register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String txt_email = email.getText().toString();
                String txt_password = password.getText().toString();

                if (TextUtils.isEmpty(txt_email) || TextUtils.isEmpty(txt_password)){
                    Toast.makeText(RegisterActivity.this, "Empty Credentials!", Toast.LENGTH_SHORT).show();
                } else if (txt_password.length() < 6){
                    Toast.makeText(RegisterActivity.this, "Password too short!", Toast.LENGTH_SHORT).show();
                } else {
                    registerUser(txt_email, txt_password);
                }
            }
        });
    }

    private void registerUser(String email, String password) {
        DataStorage x = new DataStorage();
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(RegisterActivity.this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()){
                    Toast.makeText(RegisterActivity.this, "Registering User successful!", Toast.LENGTH_SHORT).show();
                    userID = auth.getCurrentUser().getUid();
                    DocumentReference doc_ref = store.collection("users").document(userID);
                    Map<String, Double> user = x.getUserMap();

//                    user.put("Lying Down", 0.0);
//                    user.put("Running", 0.0);
//                    user.put("Sitting,Standing", 0.0);
//                    user.put("Stairs", 0.0);
//                    user.put("Walking", 0.0);

//                    "Climbing stairs",
//                            "Descending stairs",
//                            "Desk work",
//                            "Lying down left",
//                            "Lying down on back",
//                            "Lying down on stomach",
//                            "Lying down right",
//                            "Movement",
//                            "Running",
//                            "Sitting",
//                            "Sitting bent backward",
//                            "Sitting bent forward",
//                            "Standing",
//                            "Walking at normal speed"

                    user.put("Climbing stairs", 0.0);
                    user.put("Descending stairs", 0.0);
                    user.put("Desk work", 0.0);
                    user.put("Lying down left", 0.0);
                    user.put("Lying down on back", 0.0);
                    user.put("Lying down on stomach", 0.0);
                    user.put("Lying down right", 0.0);
                    user.put("Movement", 0.0);
                    user.put("Running", 0.0);
                    user.put("Sitting", 0.0);
                    user.put("Sitting bent backward", 0.0);
                    user.put("Sitting bent forward", 0.0);
                    user.put("Standing", 0.0);
                    user.put("Walking at normal speed", 0.0);


                    doc_ref.set(user).addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            Log.d("TAG", "onSuccess: User profile created for user "+ userID);
                        }
                    });
                    Intent intent = new Intent(RegisterActivity.this, StartActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(RegisterActivity.this, "Registration failed!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public class DataStorage {
        private HashMap<String, Double> user = new HashMap<String, Double>();

        public Map<String, Double> getUserMap() {
            return user;
        }
    }
}
