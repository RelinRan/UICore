package androidx.ui.core.app;

import android.view.View;

public interface AppView {

    /**
     * 初始化View
     */
    void onInitViews();

    /**
     * 添加点击事件
     *
     * @param ids
     */
    void addClick(int... ids);

    /**
     * 查找View
     *
     * @param clazz 类
     * @param id    对应id
     * @param <T>
     * @return
     */
    <T extends View> T find(Class<T> clazz, int id);

}
