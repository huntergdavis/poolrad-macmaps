package name.osher.gil.minivmac;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import name.osher.gil.minivmac.mapper.CodeWheel;
import name.osher.gil.minivmac.mapper.RuneArtwork;

/** Small native helper: no WebView, no automatic screen-reading, no RAM writes. */
public final class CodeWheelDialog extends DialogFragment {
    public interface Host { boolean typeWheelCode(String code); }
    private static final String[] PATHS = {"Dots   · · · · ·", "Dash / two dots   — · · — · ·", "Dashes   — — — —"};
    private int espruar, dethek, path = -1;
    private RuneArtwork artwork;
    private Button outer, inner, pathButton;
    private TextView answer;
    private AlertDialog picker;
    private CompanionDialogBounds.Binding bounds, pickerBounds;

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private Button button(Context context, LinearLayout column, String text) {
        Button b = new Button(context); b.setText(text); b.setAllCaps(false);
        column.addView(b, new LinearLayout.LayoutParams(-1, -2)); return b;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        if (state != null) { espruar = state.getInt("outer"); dethek = state.getInt("inner"); path = state.getInt("path", -1); }
        Context context = requireContext();
        artwork = new RuneArtwork(context.getAssets());
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL); column.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView instructions = new TextView(context);
        instructions.setTextColor(Color.BLACK);
        instructions.setText("Match the runes and path in the game below. All illustrations are included offline.");
        column.addView(instructions);
        outer = button(context, column, ""); inner = button(context, column, ""); pathButton = button(context, column, "");
        outer.setOnClickListener(v -> chooseRune(true)); inner.setOnClickListener(v -> chooseRune(false));
        pathButton.setOnClickListener(v -> {
            picker = new AlertDialog.Builder(context).setTitle("Choose path")
                    .setSingleChoiceItems(PATHS, path, (dialog, which) -> { path = which; refresh(); dialog.dismiss(); })
                    .setNegativeButton("Cancel", null).create();
            showPicker();
        });
        answer = new TextView(context); answer.setTextSize(23); answer.setTextColor(Color.BLACK); answer.setGravity(Gravity.CENTER);
        answer.setPadding(0, dp(12), 0, dp(12)); answer.setTextIsSelectable(true);
        answer.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); column.addView(answer);
        TextView credit = new TextView(context); credit.setTextSize(12); credit.setTextColor(Color.DKGRAY);
        credit.setText("Enter code types the answer and presses Return.\nReference: Dave Kennedy / Andrew Schultz · dkennedy.io/por-code-wheel");
        column.addView(credit);
        ScrollView scroll = new ScrollView(context); scroll.addView(column);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(R.string.code_wheel_title).setView(scroll)
                .setPositiveButton("Enter code", null).setNegativeButton("Close", null).create();
        // Configure the first frame too; onStart safely dismisses unavailable panes.
        if (!CompanionDialogBounds.prepare(requireActivity(), dialog) && dialog.getWindow() != null)
            dialog.getWindow().getDecorView().setVisibility(View.INVISIBLE);
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) requireDialog();
        if (!CompanionDialogBounds.prepare(requireActivity(), dialog)) { dismiss(); return; }
        dialog.getWindow().getDecorView().setVisibility(View.VISIBLE);
        if (bounds != null) bounds.close();
        bounds = CompanionDialogBounds.track(requireActivity(), dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (espruar == 0 || dethek == 0 || path < 0) return;
            if (getParentFragment() instanceof Host && ((Host) getParentFragment()).typeWheelCode(CodeWheel.entry(espruar, dethek, path))) dismiss();
        });
        refresh();
    }

    private void resizePanels() {
        if (!isAdded()) return;
        if (bounds != null) bounds.resize();
        if (pickerBounds != null) pickerBounds.resize();
    }

    private void showPicker() {
        AlertDialog current = picker;
        if (pickerBounds != null) pickerBounds.close();
        pickerBounds = null;
        if (!CompanionDialogBounds.prepare(requireActivity(), current)) { picker = null; return; }
        current.setOnDismissListener(ignored -> {
            if (picker == current) {
                if (pickerBounds != null) pickerBounds.close();
                pickerBounds = null; picker = null;
            }
        });
        current.show();
        pickerBounds = CompanionDialogBounds.track(requireActivity(), current);
    }

    @Override public void onConfigurationChanged(@NonNull Configuration configuration) {
        super.onConfigurationChanged(configuration);
        requireActivity().getWindow().getDecorView().post(this::resizePanels);
    }

    @Override public void onStop() {
        if (bounds != null) { bounds.close(); bounds = null; }
        if (picker != null) picker.dismiss();
        if (pickerBounds != null) { pickerBounds.close(); pickerBounds = null; }
        super.onStop();
    }

    private void refresh() {
        outer.setText(espruar == 0 ? "Choose Espruar rune (outer ring)" : "Espruar rune #" + espruar);
        inner.setText(dethek == 0 ? "Choose Dethek rune (inner ring)" : "Dethek rune #" + dethek);
        pathButton.setText(path < 0 ? "Choose path" : PATHS[path]);
        boolean ready = espruar > 0 && dethek > 0 && path >= 0;
        answer.setText(ready ? CodeWheel.entry(espruar, dethek, path) : "Choose both runes and a path");
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null && dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(ready);
    }

    private void chooseRune(boolean isOuter) {
        Context context = requireContext();
        GridView grid = new GridView(context); grid.setNumColumns(6); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setBackgroundColor(Color.WHITE); grid.setPadding(dp(8), dp(8), dp(8), dp(8));
        grid.setAdapter(new BaseAdapter() {
            public int getCount() { return 36; }
            public Object getItem(int at) { return at + 1; }
            public long getItemId(int at) { return at + 1; }
            public View getView(int at, View recycled, ViewGroup parent) {
                int id = at + 1;
                LinearLayout cell = new LinearLayout(context); cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER); cell.setPadding(0, dp(4), 0, dp(4));
                cell.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(62)));
                cell.setContentDescription((isOuter ? "Espruar" : "Dethek") + " rune " + id);
                cell.setBackgroundColor(id == (isOuter ? espruar : dethek) ? 0xffdddddd : Color.WHITE);
                Bitmap bitmap = artwork.read(isOuter, id);
                if (bitmap != null) {
                    ImageView symbol = new ImageView(context); symbol.setImageBitmap(bitmap);
                    symbol.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    cell.addView(symbol, new LinearLayout.LayoutParams(dp(38), dp(36)));
                }
                TextView number = new TextView(context); number.setText(Integer.toString(id)); number.setTextColor(Color.BLACK);
                number.setGravity(Gravity.CENTER); cell.addView(number); return cell;
            }
        });
        picker = new AlertDialog.Builder(context).setTitle(isOuter ? "Espruar — outer ring" : "Dethek — inner ring")
                .setView(grid).setNegativeButton("Cancel", null).create();
        AlertDialog current = picker;
        grid.setOnItemClickListener((parent, view, at, id) -> { if (isOuter) espruar = at + 1; else dethek = at + 1; refresh(); current.dismiss(); });
        showPicker();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out); out.putInt("outer", espruar); out.putInt("inner", dethek); out.putInt("path", path);
    }
    @Override public void onDestroyView() {
        if (bounds != null) { bounds.close(); bounds = null; }
        if (picker != null) picker.dismiss();
        if (pickerBounds != null) { pickerBounds.close(); pickerBounds = null; }
        super.onDestroyView();
    }
}
