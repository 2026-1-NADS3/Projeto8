package com.example.maya_project;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Banco de dados LOCAL do app, usando SQLite.
 */
public class BancoLocal extends SQLiteOpenHelper {

    // ====== Configuração do banco ======
    private static final String NOME_BANCO = "maya_local.db";
    private static final int VERSAO = 1;

    // ====== Tabela: check-ins ======
    private static final String TABELA_CHECKINS = "checkins";
    private static final String COL_ID = "id";
    private static final String COL_CPF_PACIENTE = "cpf_paciente";
    private static final String COL_NOME_EXERCICIO = "nome_exercicio";
    private static final String COL_NIVEL_DOR = "nivel_dor"; // 0 a 10
    private static final String COL_DATA = "data"; // "dd/MM/yyyy HH:mm"

    public BancoLocal(Context contexto) {
        super(contexto, NOME_BANCO, null, VERSAO);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // SQL pra criar a tabela de check-ins
        String sql = "CREATE TABLE " + TABELA_CHECKINS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_CPF_PACIENTE + " TEXT NOT NULL, "
                + COL_NOME_EXERCICIO + " TEXT NOT NULL, "
                + COL_NIVEL_DOR + " INTEGER NOT NULL, "
                + COL_DATA + " TEXT NOT NULL"
                + ")";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int versaoAntiga, int versaoNova) {
        // estratégia simples: apaga e recria
        db.execSQL("DROP TABLE IF EXISTS " + TABELA_CHECKINS);
        onCreate(db);
    }

    // OPERAÇÕES (CRUD)

    /**
     Salva um novo check-in no banco local.
     */
    public void salvarCheckin(String cpfPaciente, String nomeExercicio, int nivelDor) {
        SQLiteDatabase db = getWritableDatabase();

        // pega a data/hora atual formatada
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));
        String dataAgora = formato.format(new Date());

        ContentValues valores = new ContentValues();
        valores.put(COL_CPF_PACIENTE, cpfPaciente);
        valores.put(COL_NOME_EXERCICIO, nomeExercicio);
        valores.put(COL_NIVEL_DOR, nivelDor);
        valores.put(COL_DATA, dataAgora);

        db.insert(TABELA_CHECKINS, null, valores);
        db.close();
    }

    /**
     Lista todos os check-ins de um paciente, do mais novo pro mais antigo.
     */
    public ArrayList<Checkin> listarCheckins(String cpfPaciente) {
        ArrayList<Checkin> resultado = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        // SELECT * FROM checkins WHERE cpf = ? ORDER BY id DESC
        Cursor cursor = db.query(
                TABELA_CHECKINS,
                null,
                COL_CPF_PACIENTE + " = ?",
                new String[]{cpfPaciente},
                null, null,
                COL_ID + " DESC"
        );

        if (cursor.moveToFirst()) {
            do {
                Checkin c = new Checkin(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CPF_PACIENTE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_NOME_EXERCICIO)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_NIVEL_DOR)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_DATA))
                );
                resultado.add(c);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return resultado;
    }

    /**
     Conta quantos check-ins o paciente fez no total.
     */
    public int contarCheckins(String cpfPaciente) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABELA_CHECKINS + " WHERE " + COL_CPF_PACIENTE + " = ?",
                new String[]{cpfPaciente}
        );
        int total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return total;
    }

    /**
     Calcula a média de nível de dor dos check-ins do paciente.
     */
    public double mediaDor(String cpfPaciente) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT AVG(" + COL_NIVEL_DOR + ") FROM " + TABELA_CHECKINS
                        + " WHERE " + COL_CPF_PACIENTE + " = ?",
                new String[]{cpfPaciente}
        );
        double media = 0;
        if (cursor.moveToFirst()) {
            media = cursor.getDouble(0);
        }
        cursor.close();
        db.close();
        return media;
    }

    /**
     Conta quantos check-ins o paciente fez nos últimos 7 dias.
     * (faz uma comparação simples pelo texto da data)
     */
    public int contarUltimaSemana(String cpfPaciente) {
        ArrayList<Checkin> todos = listarCheckins(cpfPaciente);
        int contador = 0;

        // pega a data de 7 dias atrás
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));
        long agora = System.currentTimeMillis();
        long seteDiasMs = 7L * 24 * 60 * 60 * 1000;

        for (Checkin c : todos) {
            try {
                Date data = formato.parse(c.getData());
                if (data != null && (agora - data.getTime()) <= seteDiasMs) {
                    contador++;
                }
            } catch (Exception e) {
                // se falhar, ignora esse registro
            }
        }
        return contador;
    }
}