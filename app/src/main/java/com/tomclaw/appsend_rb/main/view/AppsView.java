package com.tomclaw.appsend_rb.main.view;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.ViewFlipper;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;
import com.github.rubensousa.bottomsheetbuilder.BottomSheetBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.greysonparrelli.permiso.Permiso;
import com.tomclaw.appsend_rb.DonateActivity;
import com.tomclaw.appsend_rb.PermissionsActivity;
import com.tomclaw.appsend_rb.R;
import com.tomclaw.appsend_rb.core.TaskExecutor;
import com.tomclaw.appsend_rb.main.adapter.BaseItemAdapter;
import com.tomclaw.appsend_rb.main.adapter.FilterableItemAdapter;
import com.tomclaw.appsend_rb.main.controller.AppsController;
import com.tomclaw.appsend_rb.main.item.AppItem;
import com.tomclaw.appsend_rb.main.item.BaseItem;
import com.tomclaw.appsend_rb.main.task.ExportApkTask;
import com.tomclaw.appsend_rb.util.ColorHelper;
import com.tomclaw.appsend_rb.util.EdgeChanger;
import com.tomclaw.appsend_rb.util.PreferenceHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.tomclaw.appsend_rb.util.ColorHelper.getAttributedColor;
import static com.tomclaw.appsend_rb.util.IntentHelper.openGooglePlay;
import static com.tomclaw.appsend_rb.util.Logger.logEvent;

/**
 * Created by ivsolkin on 08.01.17.
 */
public class AppsView extends MainView implements BillingProcessor.IBillingHandler, AppsController.AppsCallback {

    private ViewFlipper viewFlipper;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private FilterableItemAdapter adapter;
    private BillingProcessor bp;

    public AppsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        String licenseKey = context.getString(R.string.license_key);
        bp = new BillingProcessor(context, licenseKey, this);

        viewFlipper = findViewById(R.id.apps_view_switcher);

        findViewById(R.id.button_retry).setOnClickListener(v -> refresh());

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context, RecyclerView.VERTICAL, false);
        recyclerView = findViewById(R.id.apps_list_view);
        recyclerView.setLayoutManager(layoutManager);
        RecyclerView.ItemAnimator itemAnimator = new DefaultItemAnimator();
        recyclerView.setItemAnimator(itemAnimator);
        final int toolbarColor = ColorHelper.getAttributedColor(context, R.attr.toolbar_background);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                EdgeChanger.setEdgeGlowColor(recyclerView, toolbarColor);
            }
        });

        BaseItemAdapter.BaseItemClickListener listener = new BaseItemAdapter.BaseItemClickListener() {
            @Override
            public void onItemClicked(final BaseItem item) {
                boolean donateItem = item.getType() == BaseItem.DONATE_ITEM;
                if (donateItem) {
                    logEvent("Chocolate clicked");
                    showDonateDialog();
                } else {
                    final AppItem info = (AppItem) item;
                    checkPermissionsForExtract(info);
                }
            }

            @Override
            public void onActionClicked(BaseItem item, String action) {
            }
        };

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this::refresh);

        adapter = new FilterableItemAdapter(context);
        adapter.setListener(listener);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected int getLayout() {
        return R.layout.apps_view;
    }

    @Override
    public void activate() {
        if (!AppsController.getInstance().isStarted()) {
            refresh();
        }
    }

    @Override
    public void start() {
        AppsController.getInstance().onAttach(this);
    }

    @Override
    public void stop() {
        AppsController.getInstance().onDetach(this);
    }

    @Override
    public void destroy() {
        if (bp != null) {
            bp.release();
        }
    }

    @Override
    public void refresh() {
        AppsController.getInstance().reload(getContext());
    }

    @Override
    public boolean isFilterable() {
        return true;
    }

    @Override
    public void filter(String query) {
        adapter.getFilter().filter(query);
    }

    private void showDonateDialog() {
        startActivity(new Intent(getContext(), DonateActivity.class));
    }

    private void checkPermissionsForExtract(final AppItem appItem) {
        Permiso.getInstance().requestPermissions(new Permiso.IOnPermissionResult() {
            @Override
            public void onPermissionResult(Permiso.ResultSet resultSet) {
                if (resultSet.areAllPermissionsGranted()) {
                    // Permission granted!
                    showActionDialog(appItem);
                } else {
                    // Permission denied.
                    Snackbar.make(recyclerView, R.string.permission_denied_message, Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onRationaleRequested(Permiso.IOnRationaleProvided callback, String... permissions) {
                String title = getResources().getString(R.string.app_name);
                String message = getResources().getString(R.string.write_permission_extract);
                Permiso.getInstance().showRationaleInDialog(title, message, null, callback);
            }
        }, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void showActionDialog(final AppItem appItem) {
        boolean isDarkTheme = PreferenceHelper.isDarkTheme(getContext());
        @ColorInt int textColor = getAttributedColor(getContext(), R.attr.text_primary_color);
        @ColorInt int tintColor = getAttributedColor(getContext(), R.attr.menu_icons_tint);
        int theme = isDarkTheme ? R.style.AppTheme_BottomSheetDialog_Dark : R.style.AppTheme_BottomSheetDialog_Light;
        new BottomSheetBuilder(getContext(), theme)
                .setMode(BottomSheetBuilder.MODE_LIST)
                .setIconTintColor(tintColor)
                .setItemTextColor(textColor)
                .addItem(0, R.string.run_app, R.drawable.run)
                .addItem(1, R.string.find_on_gp, R.drawable.google_play)
                .addItem(2, R.string.share_apk, R.drawable.share)
                .addItem(3, R.string.extract_apk, R.drawable.floppy)
                .addItem(4, R.string.required_permissions, R.drawable.lock_open)
                .addItem(5, R.string.app_details, R.drawable.settings_box)
                .addItem(6, R.string.remove, R.drawable.delete)
                .setItemClickListener(item -> AppsView.this.onItemClick(appItem, item.getItemId()))
                .createDialog()
                .show();
    }

    private void onItemClick(AppItem appItem, long id) {
        switch ((int) id) {
            case 0: {
                logEvent("App menu: run");
                PackageManager packageManager = getContext().getPackageManager();
                Intent launchIntent = packageManager.getLaunchIntentForPackage(appItem.getPackageName());
                if (launchIntent == null) {
                    Snackbar.make(recyclerView, R.string.non_launchable_package, Snackbar.LENGTH_LONG).show();
                } else {
                    startActivity(launchIntent);
                }
                break;
            }
            case 1: {
                logEvent("App menu: Google Play");
                String packageName = appItem.getPackageName();
                openGooglePlay(getContext(), packageName);
                break;
            }
            case 2: {
                logEvent("App menu: share");
                TaskExecutor.getInstance().execute(new ExportApkTask(getContext(), appItem, ExportApkTask.ACTION_SHARE));
                break;
            }
            case 3: {
                logEvent("App menu: extract");
                TaskExecutor.getInstance().execute(new ExportApkTask(getContext(), appItem, ExportApkTask.ACTION_EXTRACT));
                break;
            }
            case 4: {
                logEvent("App menu: permissions");
                try {
                    PackageInfo packageInfo = appItem.getPackageInfo();
                    if (packageInfo.requestedPermissions != null) {
                        List<String> permissions = Arrays.asList(packageInfo.requestedPermissions);
                        Intent intent = new Intent(getContext(), PermissionsActivity.class)
                                .putStringArrayListExtra(PermissionsActivity.EXTRA_PERMISSIONS,
                                        new ArrayList<>(permissions));
                        startActivity(intent);
                    } else {
                        String message = getContext().getString(
                                R.string.no_required_permissions,
                                appItem.getLabel()
                        );
                        Snackbar.make(recyclerView, message, Snackbar.LENGTH_LONG).show();
                    }
                } catch (Throwable ex) {
                    Snackbar.make(recyclerView, R.string.unable_to_get_permissions, Snackbar.LENGTH_LONG).show();
                }
                break;
            }
            case 5: {
                logEvent("App menu: details");
                setRefreshOnResume();
                final Intent intent = new Intent()
                        .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .addCategory(Intent.CATEGORY_DEFAULT)
                        .setData(Uri.parse("package:" + appItem.getPackageName()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                break;
            }
            case 6: {
                logEvent("App menu: remove");
                setRefreshOnResume();
                Uri packageUri = Uri.parse("package:" + appItem.getPackageName());
                Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageUri);
                startActivity(uninstallIntent);
                break;
            }
        }
    }

    private void setAppInfoList(List<BaseItem> appItemList) {
        if (bp.loadOwnedPurchasesFromGoogle() &&
                bp.isPurchased(getContext().getString(R.string.chocolate_id))) {
            for (BaseItem item : appItemList) {
                boolean donateItem = (item.getType() == BaseItem.DONATE_ITEM);
                if (donateItem) {
                    appItemList.remove(item);
                    break;
                }
            }
        }
        adapter.setItemsList(appItemList);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onProductPurchased(@NonNull String productId, TransactionDetails details) {
    }

    @Override
    public void onPurchaseHistoryRestored() {
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {
    }

    @Override
    public void onBillingInitialized() {
    }

    @Override
    public void onProgress() {
        if (!swipeRefresh.isRefreshing()) {
            viewFlipper.setDisplayedChild(0);
        }
    }

    @Override
    public void onLoaded(List<BaseItem> list) {
        setAppInfoList(list);
        viewFlipper.setDisplayedChild(1);
        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void onError() {
        viewFlipper.setDisplayedChild(2);
        swipeRefresh.setRefreshing(false);
    }
}
