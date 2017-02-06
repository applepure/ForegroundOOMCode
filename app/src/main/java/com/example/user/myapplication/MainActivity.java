package com.example.user.myapplication;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.jaredrummler.android.processes.AndroidProcesses;
import com.jaredrummler.android.processes.models.AndroidAppProcess;
import com.jaredrummler.android.processes.models.Stat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    /** first app user */
    public static final int AID_APP = 10000;
    private boolean mIsBound = false;
    private ForegroundService mServ;
    /** offset for uid ranges for each user */
    public static final int AID_USER = 100000;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mServ = new ForegroundService();
        getForegroundRunningApp();
        Intent foreGround = new Intent();
       foreGround.setClass(this, ForegroundService.class);
        startService(foreGround);
        doBindService();


        /*try {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);

            InetAddress.getByName("stackoverflow.com");
            InetAddress.getByName("www.google.com");
            InetAddress.getByName("www.yahoo.com");
            InetAddress.getByName("www.example.com");
            printDNSCache("addressCache");
        }catch(Exception e) {
            System.out.println("ERROOOOOOOOOOOOOOOOOOOR");
            e.printStackTrace();

        }*/

    }

    private ServiceConnection Scon =new ServiceConnection(){

        public void onServiceConnected(ComponentName name, IBinder
                binder) {
            mServ = ((ForegroundService.ServiceBinder)binder).getService();
        }

        public void onServiceDisconnected(ComponentName name) {
            mServ = null;
        }
    };
    void doBindService(){
        bindService(new Intent(this,ForegroundService.class),
                Scon, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            unbindService(Scon);
            mIsBound = false;
        }
    }
    private void printDNSCache(String cacheName) throws Exception {
        Class<InetAddress> klass = InetAddress.class;
        Field acf = klass.getDeclaredField(cacheName);
        acf.setAccessible(true);
        Object addressCache = acf.get(null);
        Class cacheKlass = addressCache.getClass();
        Field cf = cacheKlass.getDeclaredField("cache");
        cf.setAccessible(true);
        Map<String, Object> cache = (Map<String, Object>) cf.get(addressCache);
        for (Map.Entry<String, Object> hi : cache.entrySet()) {
            Object cacheEntry = hi.getValue();
            Class cacheEntryKlass = cacheEntry.getClass();
            Field expf = cacheEntryKlass.getDeclaredField("expiration");
            expf.setAccessible(true);
            long expires = (Long) expf.get(cacheEntry);

            Field af = cacheEntryKlass.getDeclaredField("address");
            af.setAccessible(true);
            InetAddress[] addresses = (InetAddress[]) af.get(cacheEntry);
            List<String> ads = new ArrayList<String>(addresses.length);
            for (InetAddress address : addresses) {
                ads.add(address.getHostAddress());
            }

            System.out.println(hi.getKey() + " "+new Date(expires) +" " +ads);
        }


    }


    public void installAPK(View view) {


        // assume there is a apk file in /sdcard/ path
        String filePath = "/download/B_180012";
        String[] args = {"pm", "install", "-r", filePath};
        ProcessBuilder processBuilder = new ProcessBuilder(args);

        Process process = null;
        BufferedReader successResult = null;
        BufferedReader errorResult = null;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder errorMsg = new StringBuilder();

        try {
            process = processBuilder.start();
            successResult = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errorResult = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String s;
            while ((s = successResult.readLine()) != null) {
                successMsg.append(s);
            }
            while ((s = errorResult.readLine()) != null) {
                errorMsg.append(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            Log.d("SilentInstall", sw.toString());
            //Log.d("SilentInstall", e.getMessage());
        } finally {
            try {
                if (successResult != null)
                    successResult.close();
                if (errorResult != null)
                    errorResult.close();
                if (process != null)
                    process.destroy();
            } catch (IOException e) {

                e.printStackTrace();
                e.printStackTrace();
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                Log.d("SilentInstall", sw.toString());
            }
        }
        if (successMsg.toString().contains("success") || successMsg.toString().contains("Success")){
            Toast.makeText(getApplication(), "Install APK success:)", Toast.LENGTH_LONG).show();
        }
        Log.d("SilentInstall", "success msg:" + successMsg);
        Log.d("SilentInstall", "error msg:" + errorMsg);
    }
    public static String getForegroundApp() {



        File[] files = new File("/proc").listFiles();
        int lowestOomScore = Integer.MAX_VALUE;
        String foregroundProcess = null;

        for (File file : files) {
            if (!file.isDirectory()) {
                continue;
            }

            int pid;
            try {
                pid = Integer.parseInt(file.getName());
            } catch (NumberFormatException e) {
                continue;
            }

            try {
                String cgroup = read(String.format("/proc/%d/cgroup", pid));

                String[] lines = cgroup.split("\n");

                if (lines.length != 2) {
                    continue;
                }

                String cpuSubsystem = lines[0];
                String cpuaccctSubsystem = lines[1];

                if (!cpuaccctSubsystem.endsWith(Integer.toString(pid))) {
                    // not an application process
                    continue;
                }

                if (cpuSubsystem.endsWith("bg_non_interactive")) {
                    // background policy
                    continue;
                }

                String cmdline = read(String.format("/proc/%d/cmdline", pid));

                if (cmdline.contains("com.android.systemui")) {
                    continue;
                }

                int uid = Integer.parseInt(
                        cpuaccctSubsystem.split(":")[2].split("/")[1].replace("uid_", ""));
                if (uid >= 1000 && uid <= 1038) {
                    // system process
                    continue;
                }

                int appId = uid - AID_APP;
                int userId = 0;
                // loop until we get the correct user id.
                // 100000 is the offset for each user.
                while (appId > AID_USER) {
                    appId -= AID_USER;
                    userId++;
                }

                if (appId < 0) {
                    continue;
                }

                // u{user_id}_a{app_id} is used on API 17+ for multiple user account support.
                // String uidName = String.format("u%d_a%d", userId, appId);

                File oomScoreAdj = new File(String.format("/proc/%d/oom_score_adj", pid));
                if (oomScoreAdj.canRead()) {
                    int oomAdj = Integer.parseInt(read(oomScoreAdj.getAbsolutePath()));
                    if (oomAdj != 0) {
                        continue;
                    }
                }

                int oomscore = Integer.parseInt(read(String.format("/proc/%d/oom_score", pid)));
                if (oomscore < lowestOomScore) {
                    lowestOomScore = oomscore;
                    foregroundProcess = cmdline;

                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return foregroundProcess;
    }






    private static String read(String path) throws IOException {
        StringBuilder output = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        output.append(reader.readLine());
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            output.append('\n').append(line);
        }
        reader.close();
        return output.toString();
    }
    public static class SortFileModified implements Comparator<File> {
        @Override
        public int compare(File f1, File f2) {
            return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
        }
    }
    public static List<AndroidAppProcess> getRunningForegroundApps(Context context) {
        List<File> directoryListing=null;
        List<AndroidAppProcess> processes = new ArrayList<>();
        File[] files = new File("/proc").listFiles();
        if (files != null) {
             directoryListing= new ArrayList<File>();
            directoryListing.addAll(Arrays.asList(files));
            Collections.sort(directoryListing, new SortFileModified());
        }

        PackageManager pm = context.getPackageManager();
        for (File file : directoryListing) {
            if (file.isDirectory()) {
                int pid;
                try {
                    pid = Integer.parseInt(file.getName());
                } catch (NumberFormatException e) {
                    continue;
                }
                try {
                    AndroidAppProcess process = new AndroidAppProcess(pid);
                    if (process.foreground
                            // ignore system processes. First app user starts at 10000.
                            && (process.uid < 1000 || process.uid > 9999)
                            // ignore processes that are not running in the default app process.
                            && !process.name.contains(":")
                            // Ignore processes that the user cannot launch.
                            && pm.getLaunchIntentForPackage(process.getPackageName()) != null) {
                        processes.add(process);
                    }
                } catch (AndroidAppProcess.NotAndroidAppProcessException ignored) {
                } catch (IOException e) {
                    log(e, "Error reading from /proc/%d.", pid);
                    // System apps will not be readable on Android 5.0+ if SELinux is enforcing.
                    // You will need root access or an elevated SELinux context to read all files under /proc.
                }
            }
        }
        return processes;
    }

    private static void log(IOException e, String s, int pid) {
    }

    private void getForegroundRunningApp() {
        final Context con = this;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                final StringBuilder log = new StringBuilder();
                final String separator = System.getProperty("line.separator");
                List<AndroidAppProcess> processes = getRunningForegroundApps(con);
                List<String> current = new ArrayList<String>();

                for (AndroidAppProcess p : processes) {
                    try {
                        Stat stat = p.stat();
                        long startTime = stat.stime();
                        int policy = stat.policy();
                        char state = stat.state();
                        //if (state!='S') {




                        log.append(p.getPackageName() + " ");
                        log.append(startTime + " ");
                        log.append(state + " ");
                        log.append(policy + " ");
                        log.append(p.uid+ " ");

                        log.append(separator);
                        current.add(p.getPackageName());

                        //}
                    } catch (Exception e) {
                        Log.w("Jenia", e.getMessage());
                    }

                }
                Log.w("Jenia", log.toString());
             //   Log.wtf("Dor", getForegroundApp());

            }

        }, 0, 3000);//Update text every second
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

}
