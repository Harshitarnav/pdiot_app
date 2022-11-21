package com.specknet.pdiotapp;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.content.Intent;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.auth.User;
import com.specknet.pdiotapp.Login.RegisterActivity;
import com.specknet.pdiotapp.Login.StartActivity;

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
                loginUser(txt_email, txt_password);
            }
        });

        guest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String txt_email = "guest@gmail.com";
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
