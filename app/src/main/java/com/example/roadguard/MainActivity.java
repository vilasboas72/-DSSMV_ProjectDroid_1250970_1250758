package com.example.roadguard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    private Button btnIrRegisto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Deixa espaço para as barras do sistema e permite deslocar o formulário com o teclado aberto.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_scroll), (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });


        // Abre o ficheiro interno da app (RoadGuardPrefs) em modo privado.
        SharedPreferences sharedPref = getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE);

        // Verifica se existe a chave "UID_LOGADO", o que significa que há uma sessão ativa.
        if (sharedPref.contains("UID_LOGADO")) {
            // Vai buscar o email que ficou guardado.
            String emailGuardado = sharedPref.getString("EMAIL_LOGADO", "a sua conta");

            // Constrói um Pop-up a perguntar se o utilizador quer usar a sessão guardada.
            android.app.AlertDialog dialogSessao = new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Sessão Encontrada")
                    .setMessage("Deseja continuar com o email:\n" + emailGuardado + "?")
                    .setCancelable(false) // Obriga o utilizador a escolher uma opção.
                    .setPositiveButton("Continuar", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            // Cria um Intent (intenção de viagem) para ir direto para o Mapa.
                            Intent intent = new Intent(MainActivity.this, MapActivity.class);
                            startActivity(intent); // Arranca para o mapa.
                            finish(); // Destrói o ecrã de login para o utilizador não conseguir voltar para trás.
                        }
                    })
                    .setNegativeButton("Usar outra conta", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            // Se quiser usar outra conta, abre o editor de memória e limpa a sessão antiga.
                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.remove("UID_LOGADO");
                            editor.remove("EMAIL_LOGADO");
                            editor.apply(); // Guarda as alterações feitas na memória.
                        }
                    })
                    .create();

            dialogSessao.show();
            // Centra a janela da mensagem, independentemente da posição definida pelo tema.
            Window janela = dialogSessao.getWindow();
            if (janela != null) {
                janela.setGravity(Gravity.CENTER);
                WindowManager.LayoutParams parametros = janela.getAttributes();
                parametros.x = 0;
                parametros.y = 0;
                janela.setAttributes(parametros);
            }
        }


        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        btnLogin = findViewById(R.id.btn_login);
        btnIrRegisto = findViewById(R.id.btn_ir_registo);

        // O botão de concluir do teclado executa a mesma ação que o botão Entrar.
        editPassword.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnLogin.performClick();
                return true;
            }
            return false;
        });

        // Ação para o botão de Registo: ao clicar, viaja para a RegistoActivity.
        btnIrRegisto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, RegistoActivity.class);
                startActivity(intent);
            }
        });


        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Vai buscar o texto exato que o utilizador digitou.
                final String email = editEmail.getText().toString();
                final String password = editPassword.getText().toString();

                // Verifica se as caixas não estão vazias.
                if (!email.isEmpty() && !password.isEmpty()) {

                    // Abre uma Thread paralela sem congelar a interface .
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            HttpURLConnection httpConn = null;
                            try {
                                // PEDIDO HTTP (Ligar à Base de Dados)
                                URL url = new URL("https://roadguard-25b31-default-rtdb.europe-west1.firebasedatabase.app/Utilizador.json");
                                httpConn = (HttpURLConnection) url.openConnection();
                                httpConn.setRequestMethod("GET"); // Pedido GET porque apenas queremos LER os dados.
                                httpConn.connect();

                                // Verifica se o servidor respondeu com sucesso (Código 200 / HTTP_OK).
                                if (httpConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                    // Prepara as ferramentas para ler os bytes e transformá-los em texto legível.
                                    InputStream in = httpConn.getInputStream();
                                    BufferedReader br = new BufferedReader(new InputStreamReader(in));
                                    StringBuilder sb = new StringBuilder(); // Usado para colar as linhas de texto umas nas outras.
                                    String linha;

                                    // Lê a resposta linha a linha até acabar.
                                    while ((linha = br.readLine()) != null) {
                                        sb.append(linha);
                                    }

                                    // Guarda o texto.
                                    String jsonResposta = sb.toString();
                                    Log.d("FIREBASE_DEBUG", "A Firebase devolveu: " + jsonResposta);

                                    // Se a Firebase estiver vazia.
                                    if (jsonResposta.equals("null") || jsonResposta.isEmpty()) {
                                        // runOnUiThread devolve o controlo ao ecrã principal para podermos mostrar mensagens visuais.
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this, "A base de dados está vazia ou o URL está errado.", Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    } else {
                                        boolean loginSucesso = false;
                                        String idUtilizadorEncontrado = "";

                                        JSONObject jsonObject = new JSONObject(jsonResposta);
                                        // Puxa a lista de todas as chaves (IDs únicos) geradas pela Firebase.
                                        Iterator<String> keys = jsonObject.keys();

                                        // analisar um utilizador de cada vez.
                                        while(keys.hasNext()) {
                                            String uid = keys.next();
                                            JSONObject userObj = jsonObject.getJSONObject(uid); // Isola os dados de 1 utilizador.

                                            UtilizadorDTO utilizador = new UtilizadorDTO();
                                            utilizador.setEmail(userObj.optString("email", ""));
                                            utilizador.setPassword(userObj.optString("password", ""));

                                            // Compara os dados que vieram da Firebase com os que a pessoa escreveu no ecrã.
                                            if(utilizador.getEmail().equals(email) && utilizador.getPassword().equals(password)) {
                                                loginSucesso = true;
                                                idUtilizadorEncontrado = uid;
                                                break; // Para a pesquisa porque já encontrou a conta correta.
                                            }
                                        }

                                        if (loginSucesso) {
                                            // Abre o ficheiro interno da memória e guarda o email e ID do utilizador (shared preference).
                                            SharedPreferences sharedPreferences = MainActivity.this.getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE);
                                            SharedPreferences.Editor editor = sharedPreferences.edit();
                                            editor.putString("UID_LOGADO", idUtilizadorEncontrado);
                                            editor.putString("EMAIL_LOGADO", email);
                                            editor.apply();

                                            // Usa o runOnUiThread para voltar ao ecrã principal e viajar para o Mapa.
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Intent intent = new Intent(MainActivity.this, MapActivity.class);
                                                    startActivity(intent);
                                                    finish(); // Destrói o ecrã de login.
                                                }
                                            });
                                        } else {
                                            // Se os dados não combinarem, mostra mensagem de erro no ecrã.
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
                                // O bloco catch impede que a app feche caso a internet falhe a meio do processo.
                                e.printStackTrace();
                                final String mensagemErroReal = e.toString();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "Erro: " + mensagemErroReal, Toast.LENGTH_LONG).show();
                                    }
                                });
                            } finally {
                                // O bloco finally corre sempre no final de tudo. Serve para fechar a ligação e poupar bateria/memória.
                                if (httpConn != null) httpConn.disconnect();
                            }
                        }
                    }).start(); // Dá a ordem para a Thread em segundo plano arrancar.

                } else {
                    // Aviso caso a pessoa tenha clicado em "Entrar" com as caixas vazias.
                    Toast.makeText(MainActivity.this, "Por favor, preencha o email e a password.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
