package com.incomeexpense.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int GREEN = Color.rgb(18, 128, 92);
    private static final int ORANGE = Color.rgb(194, 65, 12);
    private static final int BG = Color.rgb(246, 248, 251);
    private static final int PANEL = Color.WHITE;
    private static final int TEXT = Color.rgb(32, 36, 42);
    private static final int MUTED = Color.rgb(104, 113, 125);

    private TrackerDb db;
    private LinearLayout root;
    private LinearLayout listContainer;
    private TextView balanceValue;
    private TextView incomeValue;
    private TextView expenseValue;
    private TextView monthValue;
    private Spinner typeFilter;
    private String currentFilter = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new TrackerDb(this);
        buildUi();
        refreshDashboard();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(22));
        scrollView.addView(root);

        TextView kicker = text("LOCAL MOBILE TRACKER", 12, MUTED, Typeface.BOLD);
        root.addView(kicker);

        TextView title = text("Income Expense Tracker", 34, TEXT, Typeface.BOLD);
        title.setPadding(0, dp(6), 0, dp(4));
        root.addView(title);

        TextView subtitle = text("Track income, expenses, and your running balance on this phone.", 15, MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, 0, 0, dp(16));
        root.addView(subtitle);

        balanceValue = addMetric("Available balance", BLUE);

        LinearLayout metrics = row();
        incomeValue = metricBox(metrics, "Total income", GREEN);
        expenseValue = metricBox(metrics, "Total expenses", ORANGE);
        root.addView(metrics);

        monthValue = addMetric("This month balance", BLUE);

        addTransactionPanel();
        addFilterPanel();

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        setContentView(scrollView);
    }

    private void addTransactionPanel() {
        LinearLayout panel = panel();
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.addView(text("QUICK ADD", 12, MUTED, Typeface.BOLD));
        panel.addView(text("New transaction", 21, TEXT, Typeface.BOLD));

        RadioGroup typeGroup = new RadioGroup(this);
        typeGroup.setOrientation(RadioGroup.HORIZONTAL);
        typeGroup.setPadding(0, dp(10), 0, dp(6));
        RadioButton income = radio("Income");
        RadioButton expense = radio("Expense");
        income.setId(View.generateViewId());
        expense.setId(View.generateViewId());
        typeGroup.addView(income);
        typeGroup.addView(expense);
        typeGroup.check(expense.getId());
        panel.addView(typeGroup);

        EditText nameInput = input("Name");
        panel.addView(nameInput);

        EditText amountInput = input("Amount");
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        panel.addView(amountInput);

        EditText categoryInput = input("Category");
        panel.addView(categoryInput);

        EditText notesInput = input("Notes optional");
        panel.addView(notesInput);

        Button save = button("Save transaction", BLUE);
        save.setOnClickListener(v -> {
            String name = value(nameInput);
            String amountText = value(amountInput);
            String category = value(categoryInput);
            String notes = value(notesInput);
            String type = typeGroup.getCheckedRadioButtonId() == income.getId() ? "income" : "expense";

            if (name.isEmpty() || amountText.isEmpty() || category.isEmpty()) {
                toast("Name, amount, and category are required.");
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountText);
            } catch (NumberFormatException ex) {
                toast("Amount must be a valid number.");
                return;
            }

            if (amount <= 0) {
                toast("Amount must be greater than zero.");
                return;
            }

            db.insert(type, name, category, amount, notes);
            nameInput.setText("");
            amountInput.setText("");
            categoryInput.setText("");
            notesInput.setText("");
            refreshDashboard();
            toast("Transaction saved.");
        });
        panel.addView(save);

        root.addView(panel);
    }

    private void addFilterPanel() {
        LinearLayout panel = panel();
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.addView(text("RECORDS", 12, MUTED, Typeface.BOLD));
        panel.addView(text("Transaction log", 21, TEXT, Typeface.BOLD));

        typeFilter = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"All", "Income", "Expense"}
        );
        typeFilter.setAdapter(adapter);
        typeFilter.setPadding(0, dp(8), 0, dp(8));
        typeFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFilter = parent.getItemAtPosition(position).toString();
                refreshDashboard();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        panel.addView(typeFilter);
        root.addView(panel);
    }

    private void refreshDashboard() {
        Summary summary = db.summary();
        balanceValue.setText(format(summary.income - summary.expense));
        incomeValue.setText(format(summary.income));
        expenseValue.setText(format(summary.expense));
        monthValue.setText(format(summary.monthIncome - summary.monthExpense));
        renderTransactions();
    }

    private void renderTransactions() {
        if (listContainer == null) {
            return;
        }

        listContainer.removeAllViews();
        List<Transaction> transactions = db.transactions(currentFilter);

        if (transactions.isEmpty()) {
            TextView empty = text("No transactions yet.", 16, MUTED, Typeface.BOLD);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(22), 0, dp(22));
            listContainer.addView(empty);
            return;
        }

        for (Transaction item : transactions) {
            LinearLayout card = panel();
            card.setPadding(dp(16), dp(14), dp(16), dp(14));

            TextView title = text(item.title, 18, TEXT, Typeface.BOLD);
            card.addView(title);

            String sign = item.type.equals("income") ? "+" : "-";
            int amountColor = item.type.equals("income") ? GREEN : ORANGE;
            TextView amount = text(sign + format(item.amount), 22, amountColor, Typeface.BOLD);
            card.addView(amount);

            TextView meta = text(cap(item.type) + " | " + item.category + " | " + item.date, 13, MUTED, Typeface.BOLD);
            card.addView(meta);

            if (!item.notes.isEmpty()) {
                TextView notes = text(item.notes, 14, MUTED, Typeface.NORMAL);
                notes.setPadding(0, dp(8), 0, 0);
                card.addView(notes);
            }

            Button delete = button("Delete", Color.rgb(179, 38, 30));
            delete.setOnClickListener(v -> confirmDelete(item.id));
            card.addView(delete);

            listContainer.addView(card);
        }
    }

    private void confirmDelete(long id) {
        new AlertDialog.Builder(this)
                .setTitle("Delete transaction?")
                .setMessage("This will remove the record from this phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.delete(id);
                    refreshDashboard();
                })
                .show();
    }

    private TextView addMetric(String label, int color) {
        LinearLayout box = panel();
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView labelView = text(label, 13, MUTED, Typeface.NORMAL);
        TextView valueView = text("PHP 0.00", 28, color, Typeface.BOLD);
        box.addView(labelView);
        box.addView(valueView);
        root.addView(box);
        return valueView;
    }

    private TextView metricBox(LinearLayout parent, String label, int color) {
        LinearLayout box = panel();
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(0, 0, dp(8), 0);
        box.setLayoutParams(params);
        box.addView(text(label, 12, MUTED, Typeface.NORMAL));
        TextView value = text("PHP 0.00", 20, color, Typeface.BOLD);
        box.addView(value);
        parent.addView(box);
        return value;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        panel.setLayoutParams(params);
        return panel;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        return row;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(false);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setPadding(0, dp(8), 0, dp(8));
        return input;
    }

    private RadioButton radio(String label) {
        RadioButton radio = new RadioButton(this);
        radio.setText(label);
        radio.setTextColor(TEXT);
        radio.setTextSize(15);
        radio.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        radio.setPadding(0, 0, dp(18), 0);
        return radio;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(color);
        button.setAllCaps(false);
        return button;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private String value(EditText input) {
        return input.getText().toString().trim();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String format(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        return format.format(amount);
    }

    private String cap(String value) {
        return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class TrackerDb extends SQLiteOpenHelper {
        TrackerDb(Context context) {
            super(context, "income_expense_tracker.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE transactions (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "type TEXT NOT NULL," +
                            "title TEXT NOT NULL," +
                            "category TEXT NOT NULL," +
                            "amount REAL NOT NULL," +
                            "notes TEXT," +
                            "created_date TEXT NOT NULL)"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }

        void insert(String type, String title, String category, double amount, String notes) {
            ContentValues values = new ContentValues();
            values.put("type", type);
            values.put("title", title);
            values.put("category", category);
            values.put("amount", amount);
            values.put("notes", notes);
            values.put("created_date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
            getWritableDatabase().insert("transactions", null, values);
        }

        void delete(long id) {
            getWritableDatabase().delete("transactions", "id = ?", new String[]{String.valueOf(id)});
        }

        Summary summary() {
            Summary summary = new Summary();
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT type, amount, created_date FROM transactions",
                    null
            );
            String month = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
            while (cursor.moveToNext()) {
                String type = cursor.getString(0);
                double amount = cursor.getDouble(1);
                String createdDate = cursor.getString(2);
                if ("income".equals(type)) {
                    summary.income += amount;
                    if (createdDate.startsWith(month)) {
                        summary.monthIncome += amount;
                    }
                } else {
                    summary.expense += amount;
                    if (createdDate.startsWith(month)) {
                        summary.monthExpense += amount;
                    }
                }
            }
            cursor.close();
            return summary;
        }

        List<Transaction> transactions(String filter) {
            ArrayList<Transaction> items = new ArrayList<>();
            String where = null;
            String[] args = null;
            if ("Income".equals(filter) || "Expense".equals(filter)) {
                where = "type = ?";
                args = new String[]{filter.toLowerCase(Locale.US)};
            }

            Cursor cursor = getReadableDatabase().query(
                    "transactions",
                    new String[]{"id", "type", "title", "category", "amount", "notes", "created_date"},
                    where,
                    args,
                    null,
                    null,
                    "id DESC"
            );

            while (cursor.moveToNext()) {
                Transaction item = new Transaction();
                item.id = cursor.getLong(0);
                item.type = cursor.getString(1);
                item.title = cursor.getString(2);
                item.category = cursor.getString(3);
                item.amount = cursor.getDouble(4);
                item.notes = cursor.getString(5) == null ? "" : cursor.getString(5);
                item.date = cursor.getString(6);
                items.add(item);
            }
            cursor.close();
            return items;
        }
    }

    private static class Summary {
        double income;
        double expense;
        double monthIncome;
        double monthExpense;
    }

    private static class Transaction {
        long id;
        String type;
        String title;
        String category;
        double amount;
        String notes;
        String date;
    }
}
