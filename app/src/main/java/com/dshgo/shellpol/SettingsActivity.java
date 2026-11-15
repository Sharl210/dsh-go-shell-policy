package com.dshgo.shellpol;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 模块独立策略设置页，不修改 DSH Go 的设置或界面。 */
public class SettingsActivity extends Activity {

    private static final int BG_LIGHT = 0xFFF5F7FC;
    private static final int CARD = 0xFFFFFFFF;
    private static final int INK = 0xFF1E232E;
    private static final int MUTED = 0xFF4F5562;
    private static final int FAINT = 0xFF646974;
    private static final int ACCENT = 0xFF4B67B4;
    private static final int HAIRLINE = 0x1A0B152D;
    private static final int DANGER = 0xFFA54743;

    private SharedPreferences prefs;
    private LinearLayout rows;
    private LinearLayout dangerRows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("dshgo_shell_policy", Context.MODE_PRIVATE);
        setContentView(build());
        refresh();
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG_LIGHT);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(title("Android Shell 确认策略"));
        root.addView(note("在 LSPosed 中启用本模块，作用域选择 DSH Go。首次启用后重新打开目标应用；此后的策略调整从下一条命令生效。"));
        root.addView(gap(14));

        LinearLayout card = card();
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        card.addView(rows);
        root.addView(card);

        root.addView(gap(18));
        root.addView(sectionTitle("危险命令判定"));
        root.addView(note("「仅危险命令询问」时，下面这些会拦下来问你，其余直接放行。"
                + "判定按整条命令的写法来，命中任一条就算危险。"));

        LinearLayout dcard = card();
        dangerRows = new LinearLayout(this);
        dangerRows.setOrientation(LinearLayout.VERTICAL);
        dcard.addView(dangerRows);
        root.addView(dcard);

        return scroll;
    }

    private void refresh() {
        int mode = prefs.getInt(HookEntry.KEY_MODE, HookEntry.MODE_ASK);
        rows.removeAllViews();
        rows.addView(optionRow(HookEntry.MODE_ASK, "每次都询问",
                "每条命令都停下来等你点确认。最稳妥，也最费手。", mode));
        rows.addView(divider());
        rows.addView(optionRow(HookEntry.MODE_ALLOW, "始终允许",
                "不再询问，直接执行。适合你在连着调试、批量跑命令的时候。", mode));
        rows.addView(divider());
        rows.addView(optionRow(HookEntry.MODE_DANGER, "仅危险命令询问",
                "普通命令直接跑；碰到删除、刷机、改权限这类才问你。", mode));

        if (dangerRows != null) {
            dangerRows.removeAllViews();
            String[] samples = {
                    "rm -rf /data/local/tmp/x",
                    "pm uninstall com.example.app",
                    "settings put global xxx 1",
                    "reboot",
                    "dd if=/dev/zero of=/dev/block/...",
                    "chmod -R 777 /system",
                    "fastboot flash boot boot.img",
                    "su -c id",
            };
            for (int i = 0; i < samples.length; i++) {
                if (i > 0) dangerRows.addView(divider());
                dangerRows.addView(sampleRow(samples[i]));
            }
        }
    }

    private View optionRow(int value, String label, String desc, int current) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setClickable(true);
        row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x1F1E232E), null, null));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(text, tp);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15f);
        t.setTextColor(INK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        text.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(12.5f);
        d.setTextColor(FAINT);
        d.setLineSpacing(0f, 1.25f);
        d.setPadding(0, dp(3), 0, 0);
        text.addView(d);

        android.widget.RadioButton radio = new android.widget.RadioButton(this);
        radio.setChecked(current == value);
        radio.setClickable(false);
        radio.setFocusable(false);
        radio.setButtonTintList(ColorStateList.valueOf(ACCENT));
        radio.setPadding(dp(12), 0, 0, 0);
        row.setContentDescription(label + (current == value ? "，已选择" : "，未选择"));
        row.addView(radio);

        row.setOnClickListener(v -> {
            boolean saved = prefs.edit().putInt(HookEntry.KEY_MODE, value).commit();
            boolean verified = false;
            if (saved) {
                try {
                    android.database.Cursor cursor = getContentResolver().query(
                            android.net.Uri.parse(HookEntry.POLICY_URI + "/mode"), new String[]{"mode"}, null, null, null);
                    if (cursor != null) try {
                        verified = cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow("mode")) == value;
                    } finally { cursor.close(); }
                } catch (Exception ignored) { }
            }
            android.widget.Toast.makeText(this,
                    verified ? "已保存，从下一条命令生效" : "保存或读回失败，请重试",
                    android.widget.Toast.LENGTH_SHORT).show();
            refresh();
        });
        return row;
    }

    private View sampleRow(String cmd) {
        TextView t = new TextView(this);
        t.setText(cmd);
        t.setTextSize(13f);
        t.setTextColor(MUTED);
        t.setTypeface(Typeface.MONOSPACE);
        t.setPadding(dp(16), dp(11), dp(16), dp(11));
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(HAIRLINE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        p.leftMargin = dp(16);
        p.rightMargin = dp(16);
        v.setLayoutParams(p);
        return v;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(21f);
        t.setTextColor(INK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13f);
        t.setTextColor(ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), 0, 0, dp(8));
        return t;
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13f);
        t.setTextColor(MUTED);
        t.setLineSpacing(0f, 1.35f);
        t.setPadding(dp(4), dp(6), dp(4), 0);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(18));
        bg.setStroke(Math.max(1, dp(1)), HAIRLINE);
        c.setBackground(bg);
        c.setClipToOutline(true);
        c.setElevation(dp(1));
        return c;
    }

    private View gap(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(dp)));
        return v;
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v);
    }
}
