package rehanced.com.simpleetherwallet.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.kobakei.ratethisapp.RateThisApp;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.Security;

import okhttp3.Response;
import rehanced.com.simpleetherwallet.R;
import rehanced.com.simpleetherwallet.data.WatchWallet;
import rehanced.com.simpleetherwallet.fragments.FragmentPrice;
import rehanced.com.simpleetherwallet.fragments.FragmentTransactionsAll;
import rehanced.com.simpleetherwallet.fragments.FragmentWallets;
import rehanced.com.simpleetherwallet.interfaces.NetworkUpdateListener;
import rehanced.com.simpleetherwallet.services.NotificationLauncher;
import rehanced.com.simpleetherwallet.services.WalletGenService;
import rehanced.com.simpleetherwallet.utils.AddressNameConverter;
import rehanced.com.simpleetherwallet.utils.Dialogs;
import rehanced.com.simpleetherwallet.utils.ExchangeCalculator;
import rehanced.com.simpleetherwallet.utils.ExternalStorageHandler;
import rehanced.com.simpleetherwallet.utils.OwnWalletUtils;
import rehanced.com.simpleetherwallet.utils.Settings;
import rehanced.com.simpleetherwallet.utils.WalletStorage;

public class MainActivity extends SecureAppCompatActivity implements NetworkUpdateListener {

    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    public Fragment[] fragments;
    private TabLayout tabLayout;
    private CoordinatorLayout coord;
    private SharedPreferences preferences;
    private AppBarLayout appbar;
    private int generateRefreshCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        File folder = new File(Environment.getExternalStorageDirectory(), "GithubCoin");
        if (!folder.exists()) folder.mkdirs();

        // App Intro
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (preferences.getLong("APP_INSTALLED", 0) == 0) {
            Intent intro = new Intent(this, rehanced.com.simpleetherwallet.activities.AppIntroActivity.class);
            startActivityForResult(intro, rehanced.com.simpleetherwallet.activities.AppIntroActivity.REQUEST_CODE);
        }

        Settings.displayAds = getPreferences().getBoolean("showAd", true);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        // ------------------------- Material Drawer ---------------------------------
        AccountHeader headerResult = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.drawable.ethereum_bg)
                .build();

        DrawerBuilder wip = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                .withAccountHeader(headerResult)
                .withSelectedItem(-1)

                .addDrawerItems(
                        new PrimaryDrawerItem().withName(getResources().getString(R.string.drawer_import)).withIcon(R.drawable.ic_action_wallet3),
                        new PrimaryDrawerItem().withName(getResources().getString(R.string.action_settings)).withIcon(R.drawable.ic_setting),
                        new PrimaryDrawerItem().withName(getResources().getString(R.string.drawer_about)).withIcon(R.drawable.ic_about),
                        new PrimaryDrawerItem().withName(getResources().getString(R.string.reddit)).withIcon(R.drawable.ic_reddit)
                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        selectItem(position);
                        return false;
                    }
                })
                .withOnDrawerListener(new Drawer.OnDrawerListener() {

                    @Override
                    public void onDrawerOpened(View drawerView) {

                    }

                    @Override
                    public void onDrawerClosed(View drawerView) {
                        //changeStatusBarColor();
                    }

                    @Override
                    public void onDrawerSlide(View drawerView, float slideOffset) {
                        //changeStatusBarTranslucent();
                    }
                });

        if (!((AnalyticsApplication) this.getApplication()).isGooglePlayBuild()) {
            wip.addDrawerItems(new PrimaryDrawerItem().withName(getResources().getString(R.string.drawer_donate)).withIcon(R.drawable.ic_action_donate));
        }

        Drawer result = wip.build();

        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        result.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);

        // ------------------------------------------------------------------------

        coord = (CoordinatorLayout) findViewById(R.id.main_content);
        appbar = (AppBarLayout) findViewById(R.id.appbar);

        fragments = new Fragment[3];
        fragments[0] = new FragmentPrice();
        fragments[1] = new FragmentWallets();
        fragments[2] = new FragmentTransactionsAll();


        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
        tabLayout.setupWithViewPager(mViewPager);

        tabLayout.getTabAt(0).setIcon(R.drawable.ic_price);
        tabLayout.getTabAt(1).setIcon(R.drawable.ic_wallet);
        tabLayout.getTabAt(2).setIcon(R.drawable.ic_transactions);

        try {
            ExchangeCalculator.getInstance().updateExchangeRates(preferences.getString("maincurrency", "USD"), this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Settings.initiate(this);
        NotificationLauncher.getInstance().start(this);

        if (getIntent().hasExtra("STARTAT")) { //  Click on Notification, show Transactions
            if (tabLayout != null)
                tabLayout.getTabAt(getIntent().getIntExtra("STARTAT", 2)).select();
            broadCastDataSetChanged();

        } else if (Settings.startWithWalletTab) { // if enabled in setting select wallet tab instead of price tab
            if (tabLayout != null)
                tabLayout.getTabAt(1).select();
        }

        mViewPager.setOffscreenPageLimit(3);

        // Rate Dialog (only show on google play builds)
        if (((AnalyticsApplication) this.getApplication()).isGooglePlayBuild()) {
            try {
                RateThisApp.onCreate(this);
                RateThisApp.Config config = new RateThisApp.Config(3, 5);
                RateThisApp.init(config);
                RateThisApp.showRateDialogIfNeeded(this, R.style.AlertDialogTheme);
            } catch (Exception e) {
            }
        }
        //Security.removeProvider("BC");
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);

    }

    public void setSelectedPage(int i){
        if(mViewPager != null)
            mViewPager.setCurrentItem(i, true);
    }

    // Spongy Castle Provider
    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case ExternalStorageHandler.REQUEST_WRITE_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (fragments != null && fragments[1] != null)
                        ((FragmentWallets) fragments[1]).export();
                } else {
                    snackError(getString(R.string.main_grant_permission_export));
                }
                return;
            }
            case ExternalStorageHandler.REQUEST_READ_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        WalletStorage.getInstance(this).importingWalletsDetector(this);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    snackError(getString(R.string.main_grant_permission_import));
                }
                return;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        broadCastDataSetChanged();

        // Update wallets if activity resumed and a new wallet was found (finished generation or added as watch only address)
        if (fragments != null && fragments[1] != null && WalletStorage.getInstance(this).get().size() != ((FragmentWallets) fragments[1]).getDisplayedWalletCount()) {
            try {
                ((FragmentWallets) fragments[1]).update();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
       /* if(preferences == null)
            preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("APP_PAUSED", true);
        editor.apply();*/
    }

    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == rehanced.com.simpleetherwallet.activities.QRScanActivity.REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                byte type = data.getByteExtra("TYPE", rehanced.com.simpleetherwallet.activities.QRScanActivity.SCAN_ONLY);
                if (type == rehanced.com.simpleetherwallet.activities.QRScanActivity.SCAN_ONLY) {
                    if (data.getStringExtra("ADDRESS").length() != 42 || !data.getStringExtra("ADDRESS").startsWith("0x")) {
                        snackError("Invalid Ethereum address!");
                        return;
                    }
                    Intent watch = new Intent(this, rehanced.com.simpleetherwallet.activities.AddressDetailActivity.class);
                    watch.putExtra("ADDRESS", data.getStringExtra("ADDRESS"));
                    startActivity(watch);
                } else if (type == rehanced.com.simpleetherwallet.activities.QRScanActivity.ADD_TO_WALLETS) {
                    if (data.getStringExtra("ADDRESS").length() != 42 || !data.getStringExtra("ADDRESS").startsWith("0x")) {
                        snackError("Invalid Ethereum address!");
                        return;
                    }
                    final boolean suc = WalletStorage.getInstance(this).add(new WatchWallet(data.getStringExtra("ADDRESS")), this);
                    new Handler().postDelayed(
                            new Runnable() {
                                @Override
                                public void run() {
                                    if (fragments != null && fragments[1] != null) {
                                        try {
                                            ((FragmentWallets) fragments[1]).update();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (tabLayout != null)
                                        tabLayout.getTabAt(1).select();
                                    Snackbar mySnackbar = Snackbar.make(coord,
                                            MainActivity.this.getResources().getString(suc ? R.string.main_ac_wallet_added_suc : R.string.main_ac_wallet_added_er), Snackbar.LENGTH_SHORT);
                                    if (suc)
                                        AddressNameConverter.getInstance(MainActivity.this).put(data.getStringExtra("ADDRESS"), "Watch " + data.getStringExtra("ADDRESS").substring(0, 6), MainActivity.this);

                                    mySnackbar.show();

                                }
                            }, 100);
                } else if (type == rehanced.com.simpleetherwallet.activities.QRScanActivity.REQUEST_PAYMENT) {
                    if (WalletStorage.getInstance(this).getFullOnly().size() == 0) {
                        Dialogs.noFullWallet(this);
                    } else {
                        Intent watch = new Intent(this, rehanced.com.simpleetherwallet.activities.SendActivity.class);
                        watch.putExtra("TO_ADDRESS", data.getStringExtra("ADDRESS"));
                        watch.putExtra("AMOUNT", data.getStringExtra("AMOUNT"));
                        startActivity(watch);
                    }
                } else if (type == rehanced.com.simpleetherwallet.activities.QRScanActivity.PRIVATE_KEY) {
                    if (OwnWalletUtils.isValidPrivateKey(data.getStringExtra("ADDRESS"))) {
                        importPrivateKey(data.getStringExtra("ADDRESS"));
                    } else {
                        this.snackError("Invalid private key!");
                    }
                }
            } else {
                Snackbar mySnackbar = Snackbar.make(coord,
                        MainActivity.this.getResources().getString(R.string.main_ac_wallet_added_fatal), Snackbar.LENGTH_SHORT);
                mySnackbar.show();
            }
        } else if (requestCode == rehanced.com.simpleetherwallet.activities.WalletGenActivity.REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Intent generatingService = new Intent(this, WalletGenService.class);
                generatingService.putExtra("PASSWORD", data.getStringExtra("PASSWORD"));
                if (data.hasExtra("PRIVATE_KEY"))
                    generatingService.putExtra("PRIVATE_KEY", data.getStringExtra("PRIVATE_KEY"));
                startService(generatingService);

                final Handler handler = new Handler();
                generateRefreshCount = 0;
                final int walletcount = WalletStorage.getInstance(this).getFullOnly().size();
                Runnable runnable = new Runnable() {
                    public void run() {
                        try {
                            if(walletcount < WalletStorage.getInstance(MainActivity.this).getFullOnly().size()) {
                                ((FragmentWallets) fragments[1]).update();
                                return;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (generateRefreshCount++ < 8)
                            handler.postDelayed(this, 3000);
                    }
                };
                handler.postDelayed(runnable, 4000);
            }
        } else if (requestCode == rehanced.com.simpleetherwallet.activities.SendActivity.REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                if( fragments == null )
                    return;
                if(fragments[2] == null) {
                    return;
                }
                ((FragmentTransactionsAll) fragments[2]).addConfirmedTransaction(data.getStringExtra("FROM_ADDRESS"), data.getStringExtra("TO_ADDRESS"), new BigDecimal("-" + data.getStringExtra("AMOUNT")).multiply(new BigDecimal("1000000000000000000")).toBigInteger());
                //((FragmentTransactionsAll) fragments[2]).addUnconfirmedTransaction(data.getStringExtra("FROM_ADDRESS"), data.getStringExtra("TO_ADDRESS"), new BigDecimal("-" + data.getStringExtra("AMOUNT")).multiply(new BigDecimal("1000000000000000000")).toBigInteger());

                if (tabLayout != null)
                    tabLayout.getTabAt(2).select();
            }
        } else if (requestCode == rehanced.com.simpleetherwallet.activities.AppIntroActivity.REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                finish();
            } else {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putLong("APP_INSTALLED", System.currentTimeMillis());
                editor.commit();
            }
        } else if (requestCode == rehanced.com.simpleetherwallet.activities.SettingsActivity.REQUEST_CODE) {
            if (!preferences.getString("maincurrency", "USD").equals(ExchangeCalculator.getInstance().getMainCurreny().getName())) {
                try {
                    ExchangeCalculator.getInstance().updateExchangeRates(preferences.getString("maincurrency", "USD"), this);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                new Handler().postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                if (fragments != null) {
                                    if (fragments[0] != null)
                                        ((FragmentPrice) fragments[0]).update(true);
                                    if (fragments[1] != null) {
                                        ((FragmentWallets) fragments[1]).updateBalanceText();
                                        ((FragmentWallets) fragments[1]).notifyDataSetChanged();
                                    }
                                    if (fragments[2] != null)
                                        ((FragmentTransactionsAll) fragments[2]).notifyDataSetChanged();
                                }
                            }
                        }, 950);
            }

        }
    }

    public void importPrivateKey(String privatekey) {
        Intent genI = new Intent(this, rehanced.com.simpleetherwallet.activities.WalletGenActivity.class);
        genI.putExtra("PRIVATE_KEY", privatekey);
        startActivityForResult(genI, rehanced.com.simpleetherwallet.activities.WalletGenActivity.REQUEST_CODE);
    }

    public void snackError(String s, int length) {
        if (coord == null) return;
        Snackbar mySnackbar = Snackbar.make(coord, s, length);
        mySnackbar.show();
    }

    public void snackError(String s) {
        snackError(s, Snackbar.LENGTH_SHORT);
    }

    private void selectItem(int position) {
        switch (position) {
            case 1: {
                try {
                    WalletStorage.getInstance(this).importingWalletsDetector(this);

                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case 2: {
                Intent settings = new Intent(this, rehanced.com.simpleetherwallet.activities.SettingsActivity.class);
                startActivityForResult(settings, rehanced.com.simpleetherwallet.activities.SettingsActivity.REQUEST_CODE);
                break;
            }
            case 3: {

                Intent intent = new Intent();
                //或者是像下面这样的写,这里是用来了意图（intent）里面的标准action这里面还有许多的标准意图可以使用
//   intent.setAction("android.intent.action.VIEW"); //这里面的意图可以自已写也可以像下面一句这样直接用Intent里面的常量
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("http://v.hayoou.com/ethwallet/gtbshare/intro.php"));//设置一个URI地址
                this.startActivity(intent);//用startActivity打开这个指定的网页。
                /*
                new AlertDialog.Builder(this)
                        .setTitle("贵州Githubcoin科技有限公司介绍")
                        .setMessage("关于我们：\n" +
                               "    Githubcoin实际上就是一个市场与原生态食品原生态旅游对接的O2O共享平台，利用有限的资源通过共享和抱团去无限的展示、放大农场和农场的产品，由单一走向全面。Githubcoin将成为资金、人才、技术、市场、经验等互助的互联网+O2O共享平台。在这里真正要实现的是吸引用户，树立品牌，建立自己的个人信誉为保证的社会群体。社员与社员之间互帮互学、优势互补、资源共享，通过抱团发展，优势互补，解决社员与商家的难点，为社员提供金融支持、技术指导、宣传推广，在Githubcoin大品牌下共同发展共同受益。社员与消费者之间，诚信相处，互利共赢。引领社会诚信，民族身心健康，国家昌盛。\n" +
                                "哈友社区是一个未来智能社区  " +
                                "同时自建了一个完善的互联网与线下服务体系，" +
                                "为分布在国内地区的社区成员独立提供 安全、互联网、" +
                                "影视娱乐、食品 甚至今后的社区小镇 " +
                                "可以体验社区互助文化氛围，" +
                                "高端、雅致的生活方式与自然的生态环境\n" +
                                " \n" +
                                "\n" +
                                "以哈友社区和Githubcoin用户为服务对象，围绕高端社区成员创建一系列定制化产品与项目，" +
                                "推动社区科技/效率发展。充分吸收高能力、高学习能力、" +
                                "高行动力的精英人才，使其在一个无限边界的领域内施展其无限的创造力。\n" +
                                "社区在 在线视频（魔映短视频），文化，网上社区，视频存储，自动摘要，微信助手，" +
                                "企业信箱，无人机，虚拟好友等方向做了一些探索。" +
                                "并将社区产品连接到社会生活中，期许能为社会发展提供新一代引擎。\n" +
                                "网站是：hayoou.com 注册邀请码：hayoouAI\n" +
                                "微信公众号：hayoou 微信客服：hayoou2")
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .show();*/
                break;
            }
            case 4: {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("http://hayoou.com/githubcoin"));
                startActivity(i);
                break;
            }
            case 5: {
                if (WalletStorage.getInstance(this).getFullOnly().size() == 0) {
                    Dialogs.noFullWallet(this);
                } else {
                    Intent donate = new Intent(this, SendActivity.class);
                    donate.putExtra("TO_ADDRESS", "0xde7bb8d864380070f4814da0b9bd7ecdb5c2337f");
                    startActivity(donate);
                }
                break;
            }
            default: {
                return;
            }
        }
    }

    public void broadCastDataSetChanged() {
        if (fragments != null && fragments[1] != null && fragments[2] != null) {
            ((FragmentWallets) fragments[1]).notifyDataSetChanged();
            ((FragmentTransactionsAll) fragments[2]).notifyDataSetChanged();
        }
    }

    @Override
    public void onUpdate(Response s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                broadCastDataSetChanged();
                if (fragments != null && fragments[0] != null) {
                    ((FragmentPrice) fragments[0]).update(true);
                }
            }
        });
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return fragments[position];
        }

        @Override
        public int getCount() {
            return fragments.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "";
        }
    }

    public AppBarLayout getAppBar() {
        return appbar;
    }
}
