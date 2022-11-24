package com.specknet.pdiotapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.content.Intent;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.polidea.rxandroidble2.RxBleConnection;
import com.specknet.pdiotapp.login.RegisterActivity;
import com.specknet.pdiotapp.login.StartActivity;

public class MainActivity extends AppCompatActivity {

    private Button register;
    private Button login;
    private Button guest;
    private EditText email;
    private EditText password;

    private FirebaseAuth auth;
    private FirebaseFirestore store;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        register = findViewById(R.id.register);
        login = findViewById(R.id.login);
        guest = findViewById(R.id.guest);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);


        auth = FirebaseAuth.getInstance();
        store = FirebaseFirestore.getInstance();

        register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String txt_email = email.getText().toString();
                String txt_password = password.getText().toString();
                if (txt_password.isEmpty() && txt_email.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Enter your email and password!", Toast.LENGTH_SHORT).show();
                }
                else if (txt_password.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Password field empty!", Toast.LENGTH_SHORT).show();
                }
                else if (txt_email.isEmpty()){
                    Toast.makeText(MainActivity.this, "Please enter your email!", Toast.LENGTH_SHORT).show();
                }
                else {
                    loginUser(txt_email, txt_password);
                }
            }
        });

        guest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String txt_email = "guest5@gmail.com"; //for 5 classes
                String txt_password = "12345678";
                loginGuestUser(txt_email, txt_password);
            }
        });
    }

    private void loginUser(String email, String password) {

        auth.signInWithEmailAndPassword(email, password).addOnSuccessListener(new OnSuccessListener<AuthResult>() {
            @Override
            public void onSuccess(AuthResult authResult) {
                Toast.makeText(MainActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, StartActivity.class);
                startActivity(intent);
                finish();
            }
        });

        auth.signInWithEmailAndPassword(email, password).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "Wrong Credentials!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loginGuestUser(String email, String password) {

        auth.signInWithEmailAndPassword(email, password).addOnSuccessListener(new OnSuccessListener<AuthResult>() {
            @Override
            public void onSuccess(AuthResult authResult) {
                Toast.makeText(MainActivity.this, "Welcome Guest!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, StartActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}
