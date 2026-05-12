package com.example.maya_project;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Helper para montar layouts simples programaticamente.
 * Usado no diálogo de check-in pra não precisar criar um XML separado.
 */
public class LinearLayoutBuilder {

    private final Context contexto;
    private final LinearLayout layout;

    public LinearLayoutBuilder(Context contexto) {
        this.contexto = contexto;
        this.layout = new LinearLayout(contexto);
        this.layout.setOrientation(LinearLayout.VERTICAL);

        int padding = dp(20);
        this.layout.setPadding(padding, padding, padding, padding);
    }

    public LinearLayout getLayout() {
        return layout;
    }

    public TextView criarLabel(String texto) {
        TextView tv = new TextView(contexto);
        tv.setText(texto);
        tv.setTextSize(14);
        tv.setPadding(0, dp(4), 0, dp(4));
        return tv;
    }

    public void adicionarView(View v) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(v, params);
    }

    public void adicionarSeekBar(SeekBar bar) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, dp(8));
        layout.addView(bar, params);
    }

    private int dp(int v) {
        float densidade = contexto.getResources().getDisplayMetrics().density;
        return Math.round(v * densidade);
    }
}