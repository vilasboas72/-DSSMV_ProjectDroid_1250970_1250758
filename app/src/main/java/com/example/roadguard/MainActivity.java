package com.example.roadguard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {

    private EditText editEmail;
    private EditText editPassword;
    private Button btnLogin;
    private Button btnIrRegisto; // <-- Botão de ir para o Registo

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Verificação de Sessão (Shared Preferences) com Pop-up
        SharedPreferences sharedPref = getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE);
        if (sharedPref.contains("UID_LOGADO")) {
            String emailGuardado = sharedPref.getString("EMAIL_LOGADO", "a sua conta");

            new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Sessão Encontrada")
                    .setMessage("Deseja continuar com o email:\n" + emailGuardado + "?")
                    .setCancelable(false)
                    .setPositiveButton("Continuar", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            Intent intent = new Intent(MainActivity.this, MapActivity.class);
                            startActivity(intent);
                            finish();
                        }
                    })
                    .setNegativeButton("Usar outra conta", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.remove("UID_LOGADO");
                            editor.remove("EMAIL_LOGADO");
                            editor.apply();
                        }
                    })
                    .show();
        }

        // Ligar as variáveis aos elementos do ecrã
        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        btnLogin = findViewById(R.id.btn_login);
        btnIrRegisto = findViewById(R.id.btn_ir_registo);

        // Ação do botão Criar Nova Conta (abre a RegistoActivity)
        btnIrRegisto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, RegistoActivity.class);
                startActivity(intent);
            }
        });

        // Ação do botão Entrar
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = editEmail.getText().toString();
                final String password = editPassword.getText().toString();

                if (!email.isEmpty() && !password.isEmpty()) {

                    // 2. Worker Thread para o pedido HTTP
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            HttpURLConnection httpConn = null;
                            try {
                                URL url = new URL("https://roadguard-25b31-default-rtdb.europe-west1.firebasedatabase.app/Utilizador.json");
                                httpConn = (HttpURLConnection) url.openConnection();
                                httpConn.setRequestMethod("GET");
                                httpConn.connect();

                                if (httpConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                    InputStream in = httpConn.getInputStream();
                                    BufferedReader br = new BufferedReader(new InputStreamReader(in));
                                    StringBuilder sb = new StringBuilder();
                                    String linha;
                                    while ((linha = br.readLine()) != null) {
                                        sb.append(linha);
                                    }

                                    String jsonResposta = sb.toString();
                                    Log.d("FIREBASE_DEBUG", "A Firebase devolveu: " + jsonResposta);

                                    if (jsonResposta.equals("null") || jsonResposta.isEmpty()) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this, "A base de dados está vazia ou o URL está errado.", Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    } else {
                                        boolean loginSucesso = false;
                                        String idUtilizadorEncontrado = "";

                                        // 3. Processar JSON e preencher DTO
                                        JSONObject jsonObject = new JSONObject(jsonResposta);
                                        Iterator<String> keys = jsonObject.keys();

                                        while(keys.hasNext()) {
                                            String uid = keys.next();
                                            JSONObject userObj = jsonObject.getJSONObject(uid);

                                            UtilizadorDTO utilizador = new UtilizadorDTO();
                                            utilizador.setEmail(userObj.optString("email", ""));
                                            utilizador.setPassword(userObj.optString("password", ""));

                                            if(utilizador.getEmail().equals(email) && utilizador.getPassword().equals(password)) {
                                                loginSucesso = true;
                                                idUtilizadorEncontrado = uid;
                                                break;
                                            }
                                        }

                                        if (loginSucesso) {
                                            // 4. Gravar a sessão e o email
                                            SharedPreferences sharedPreferences = MainActivity.this.getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE);
                                            SharedPreferences.Editor editor = sharedPreferences.edit();
                                            editor.putString("UID_LOGADO", idUtilizadorEncontrado);
                                            editor.putString("EMAIL_LOGADO", email);
                                            editor.apply();

                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Intent intent = new Intent(MainActivity.this, MapActivity.class);
                                                    startActivity(intent);
                                                    finish();
                                                }
                                            });
                                        } else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this, "Email ou password incorretos!", Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                final String mensagemErroReal = e.toString();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "Erro: " + mensagemErroReal, Toast.LENGTH_LONG).show();
                                    }
                                });
                            } finally {
                                if (httpConn != null) httpConn.disconnect();
                            }
                        }
                    }).start();

                } else {
                    Toast.makeText(MainActivity.this, "Por favor, preencha o email e a password.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}