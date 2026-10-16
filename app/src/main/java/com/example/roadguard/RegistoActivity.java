package com.example.roadguard;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RegistoActivity extends AppCompatActivity {

    private EditText editNome;
    private EditText editEmail;
    private EditText editPassword;
    private Button btnConfirmarRegisto;
    private Button btnVoltarLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registo);

        editNome = findViewById(R.id.edit_registo_nome);
        editEmail = findViewById(R.id.edit_registo_email);
        editPassword = findViewById(R.id.edit_registo_password);
        btnConfirmarRegisto = findViewById(R.id.btn_confirmar_registo);
        btnVoltarLogin = findViewById(R.id.btn_voltar_login);

        // Botão para voltar atrás caso o utilizador se engane
        btnVoltarLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Botão para criar a conta na Firebase via HTTP POST
        btnConfirmarRegisto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String nome = editNome.getText().toString().trim();
                final String email = editEmail.getText().toString().trim();
                final String password = editPassword.getText().toString().trim();

                if (!nome.isEmpty() && !email.isEmpty() && !password.isEmpty()) {

                    // Worker Thread para enviar o pedido POST
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            HttpURLConnection httpConn = null;
                            try {
                                URL url = new URL("https://roadguard-25b31-default-rtdb.europe-west1.firebasedatabase.app/Utilizador.json");
                                httpConn = (HttpURLConnection) url.openConnection();

                                // Verbo HTTP POST (cria um novo registo na Firebase)
                                httpConn.setRequestMethod("POST");
                                httpConn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                                httpConn.setRequestProperty("Accept", "application/json");
                                httpConn.setDoOutput(true);

                                // Construir o objeto JSON com os dados do novo utilizador
                                JSONObject novoUtilizador = new JSONObject();
                                novoUtilizador.put("nome", nome);
                                novoUtilizador.put("email", email);
                                novoUtilizador.put("password", password);

                                // Enviar o JSON no corpo do pedido
                                OutputStream os = httpConn.getOutputStream();
                                os.write(novoUtilizador.toString().getBytes("UTF-8"));
                                os.close();

                                final int responseCode = httpConn.getResponseCode();
                                if (responseCode == HttpURLConnection.HTTP_OK) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(RegistoActivity.this, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show();
                                            finish(); // Fecha o ecrã de registo e volta ao Login
                                        }
                                    });
                                } else {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(RegistoActivity.this, "Erro ao criar conta: " + responseCode, Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                final String erro = e.toString();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(RegistoActivity.this, "Erro: " + erro, Toast.LENGTH_LONG).show();
                                    }
                                });
                            } finally {
                                if (httpConn != null) httpConn.disconnect();
                            }
                        }
                    }).start();

                } else {
                    Toast.makeText(RegistoActivity.this, "Por favor, preencha todos os campos.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}