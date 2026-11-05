package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;

/**
 * 主接口（主配置）无法加载/访问且本地无缓存时的全屏报错页。
 * 需求：主接口失败时直接弹出本界面报错，不再加载首页等其它界面。
 */
public class ConfigErrorActivity extends BaseActivity {

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_config_error;
    }

    @Override
    protected void init() {
        TextView btnRetry = findViewById(R.id.btnRetry);
        TextView btnExit = findViewById(R.id.btnExit);
        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retry();
            }
        });
        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                com.github.tvbox.osc.util.AppManager.getInstance().appExit(0);
                finish();
            }
        });
        btnRetry.requestFocus();
    }

    /** 重试：重置主接口状态、重新拉取主配置，并重新进入首页 */
    private void retry() {
        com.github.tvbox.osc.n2n.RemoteConfig.retryPrimary(getApplicationContext());
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // 主接口不可用：不允许返回进入其它界面，只能用重试/退出
    }
}
