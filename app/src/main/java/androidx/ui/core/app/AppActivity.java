package androidx.ui.core.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;


/**
 * 应用程序 - 活动页面
 */
public class AppActivity extends AppCompatActivity implements AppTransaction, AppLayout, AppPlaceholder, AppActionBar.OnAppActionBarClickListener, AppPermission.OnRequestPermissionsListener, AppLoading {

    private AppActionBar appActionBar;
    private AppTransaction transaction;
    private AppPlaceholder placeholder;
    private AppPermission permission;
    private AppLoading loading;

    /**
     * 获取上下文
     *
     * @return
     */
    public Context getContext() {
        return this;
    }

    /**
     * 设置加载器
     *
     * @param loading
     */
    public void setLoading(AppLoading loading) {
        this.loading = loading;
    }

    /**
     * 获取加载器对象
     *
     * @return
     */
    public AppLoading getLoading() {
        return loading;
    }

    /**
     * 设置权限对象
     *
     * @param permission
     */
    public void setPermission(AppPermission permission) {
        this.permission = permission;
    }

    /**
     * 获取权限对象
     *
     * @return
     */
    public AppPermission getPermission() {
        return permission;
    }

    /**
     * 设置应用实现类
     *
     * @param transaction
     */
    protected void setAppTransactionImpl(AppTransaction transaction) {
        this.transaction = transaction;
    }

    /**
     * 设置占位实现类
     *
     * @param placeholder 占位实现类
     */
    protected void setPlaceholderImpl(AppPlaceholder placeholder) {
        this.placeholder = placeholder;
    }

    /**
     * 获取占位类
     *
     * @return
     */
    public <T extends AppPlaceholderImpl> T getPlaceholder() {
        return (T) placeholder;
    }

    /**
     * 设置应用ActionBar
     *
     * @param appActionBar 应用ActionBar
     */
    protected void setAppActionBar(AppActionBar appActionBar) {
        appActionBar.setOnAppActionBarClickListener(this);
        this.appActionBar = appActionBar;
    }

    /**
     * 获取应用ActionBar
     *
     * @return
     */
    public AppActionBar getAppActionBar() {
        return appActionBar;
    }

    @Override
    public void onBackClick(View v) {
        finish();
    }

    @Override
    public void onTitleClick(View v) {

    }

    @Override
    public void onMenuClick(View v) {

    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPermission(new AppPermission(this, this));
        AppActivityManager.getInstance().add(this);
        setAppActionBar(new AppActionBar(this));
        setPlaceholderImpl(new AppPlaceholderImpl(this));
        setContentView(getPlaceholder().getParent());
        setAppTransactionImpl(new AppTransactionImpl(this));
        setLoading(new AppLoadingImpl(this));
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        transaction.overrideAppPendingTransition(PENDING_START);
    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        super.startActivity(intent, options);
        transaction.overrideAppPendingTransition(PENDING_START);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        super.startActivityForResult(intent, requestCode);
        transaction.overrideAppPendingTransition(PENDING_START);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        super.startActivityForResult(intent, requestCode, options);
        transaction.overrideAppPendingTransition(PENDING_START);
    }

    @Override
    public void finish() {
        super.finish();
        transaction.overrideAppPendingTransition(PENDING_FINISH);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppActivityManager.getInstance().remove(this);
    }

    @Override
    public Context getAppContext() {
        return this;
    }

    @Override
    public int getContentViewLayoutResId() {
        return 0;
    }

    @Override
    public int getContainerViewResId() {
        return 0;
    }

    @Override
    public FragmentManager getAppFragmentManager() {
        return getSupportFragmentManager();
    }

    @Override
    public void setPlaceholder(int type, int resId, String text) {
        placeholder.setPlaceholder(type, resId, text);
    }

    @Override
    public void setPlaceholder(int type, int resId) {
        placeholder.setPlaceholder(type, resId);
    }

    @Override
    public void setPlaceholder(int type, String text) {
        placeholder.setPlaceholder(type, text);
    }

    @Override
    public void showPlaceholder(int type) {
        placeholder.showPlaceholder(type);
    }

    @Override
    public void hidePlaceholder() {
        placeholder.hidePlaceholder();
    }

    @Override
    public void addFragment(Fragment fragment) {
        transaction.addFragment(fragment);
    }

    @Override
    public void addFragment(Fragment fragment, Bundle options) {
        transaction.addFragment(fragment, options);
    }

    @Override
    public void addFragment(Class<? extends Fragment> cls) {
        transaction.addFragment(cls);
    }

    @Override
    public void addFragment(Class<? extends Fragment> cls, Bundle options) {
        transaction.addFragment(cls, options);
    }

    @Override
    public boolean isLogin() {
        return transaction.isLogin();
    }

    @Override
    public void setLogin(boolean login) {
        transaction.setLogin(login);
    }

    @Override
    public void setToken(String token) {
        transaction.setToken(token);
    }

    @Override
    public String getToken() {
        return transaction.getToken();
    }

    @Override
    public void setUserInfo(String json) {
        transaction.setUserInfo(json);
    }

    @Override
    public String getUserInfo() {
        return transaction.getUserInfo();
    }

    @Override
    public void overrideAppPendingTransition(int pending) {
        transaction.overrideAppPendingTransition(pending);
    }

    @Override
    public void startActivity(Class<?> cls) {
        transaction.startActivity(cls);
    }

    @Override
    public void startActivity(Class<?> cls, Bundle options) {
        transaction.startActivity(cls, options);
    }

    @Override
    public void startActivityForResult(Class<?> cls, int requestCode) {
        transaction.startActivityForResult(cls, requestCode);
    }

    @Override
    public void startActivityForResult(Class<?> cls, int requestCode, Bundle options) {
        transaction.startActivityForResult(cls, requestCode, options);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        getPermission().onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onRequestPermissionsGranted(String[] permissions) {

    }

    @Override
    public void onRequestPermissionsDenied(String[] permissions) {

    }

    @Override
    public void onRequestPermissionRationale(String[] permissions) {

    }

    @Override
    public void showLoading() {
        getLoading().showLoading();
    }

    @Override
    public void showLoading(String msg) {
        getLoading().showLoading(msg);
    }

    @Override
    public void dismissLoading() {
        getLoading().dismissLoading();
    }

    /**
     * 显示提示
     *
     * @param msg 内容
     */
    public void showToast(String msg) {
        AppToast.show(this, msg);
    }

}
