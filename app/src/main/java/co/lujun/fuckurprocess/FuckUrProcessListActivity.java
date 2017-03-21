package co.lujun.fuckurprocess;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class FuckUrProcessListActivity extends AppCompatActivity {

    private List<ProcessManager.Process> mRunningProcessList;
    private ActivityManager aManager;
    private ProcessAdapter mAdapter;

    private RecyclerView mProcessList;
    private EditText textFilter;
    private ProgressBar progressBar;

    private String mFilterWords;
    private static final String TAG = "FuckUrProcess";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fuck_ur_process_list);

        aManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        mRunningProcessList = new ArrayList<ProcessManager.Process>();
        mAdapter = new ProcessAdapter(mRunningProcessList, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                killProcess(mRunningProcessList.get(position));
            }
        });

        progressBar = (ProgressBar) findViewById(R.id.progressbar);
        textFilter = (EditText) findViewById(R.id.text_filter);
        mProcessList = (RecyclerView) findViewById(R.id.process_list);
        mProcessList.setLayoutManager(new LinearLayoutManager(this));
        mProcessList.setAdapter(mAdapter);

        textFilter.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE){
                    mFilterWords = textFilter.getText().toString();
                    updateRunningProcessData();
                }
                return false;
            }
        });
        
        mFilterWords = textFilter.getText().toString();

        updateRunningProcessData();
    }

    private void updateRunningProcessData(){
        progressBar.setVisibility(View.VISIBLE);
        final Runnable updateUI = new Runnable() {
            @Override
            public void run() {
                String[] excludeWords = mFilterWords.split(",");
                if (excludeWords.length > 0) {
                    List<ProcessManager.Process> tmpList = new ArrayList<ProcessManager.Process>();

                    for (int i = 0; i < mRunningProcessList.size(); i++) {
                        ProcessManager.Process process = mRunningProcessList.get(i);
                        int total = 0;
                        for (int j = 0; j < excludeWords.length; j++) {
                            if (process.getPackageName().startsWith(excludeWords[j])) {
                                break;
                            }else {
                                total++;
                            }
                        }
                        if (total == excludeWords.length){
                            tmpList.add(process);
                        }
                    }

                    mRunningProcessList.clear();
                    mRunningProcessList.addAll(tmpList);
                }

                Log.d(TAG, "------------------------------------------------------------");
                for (int i = 0; i < mRunningProcessList.size(); i++) {
                    ProcessManager.Process pInfo = mRunningProcessList.get(i);
                    Log.d(TAG, i +
                            " USER: " + pInfo.user +
                            ", state: " + pInfo.state +
                            ", process name: " + pInfo.getPackageName() +
                            ", pid: " + pInfo.pid +
                            ", uid: " + pInfo.uid +
                            ", memory rss/vSize: " + pInfo.rss + "/" + pInfo.vsize + " KB");
                }
                Log.d(TAG, "------------------------------------------------------------");

                mAdapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
            }
        };
        Runnable findRunningPRunnable = new Runnable() {
            @Override
            public void run() {
                mRunningProcessList.clear();
                mRunningProcessList.addAll(ProcessManager.getRunningProcesses());
                runOnUiThread(updateUI);
            }
        };
        new Thread(findRunningPRunnable).start();
    }

    private void killProcess(final ProcessManager.Process pInfo){
        if (aManager == null){
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Kill process")
                .setMessage("Process '" + pInfo.getPackageName() + "' will be killed!")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("Kill", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        aManager.killBackgroundProcesses(pInfo.getPackageName());
                        updateRunningProcessData();
                    }
                })
                .show();
    }


    static class ProcessManager {

        private static final String TAG = "ProcessManager";

        private static final String APP_ID_PATTERN;

        static {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                // Android 4.2 (JB-MR1) changed the UID name of apps for multiple user account support.
                APP_ID_PATTERN = "u\\d+_a\\d+";
            } else {
                APP_ID_PATTERN = "app_\\d+";
            }
        }

        public static List<Process> getRunningProcesses() {
            List<Process> processes = new ArrayList<>();
            List<String> stdout = Shell.SH.run("toolbox ps -p -P -x -c");
            for (String line : stdout) {
                try {
                    Process process = new Process(line);
                    if (process.getPackageName() != null) {
                        processes.add(process);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Failed parsing line " + line);
                }
            }
            return processes;
        }

        public static List<Process> getRunningApps() {
            List<Process> processes = new ArrayList<>();
            List<String> stdout = Shell.SH.run("toolbox ps -p -P -x -c");
            int myPid = android.os.Process.myPid();
            for (String line : stdout) {
                try {
                    Process process = new Process(line);
                    if (process.user.matches(APP_ID_PATTERN)) {
                        if (process.ppid == myPid || process.name.equals("toolbox")) {
                            // skip the processes we created to get the running apps.
                            continue;
                        }
                        processes.add(process);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Failed parsing line " + line);
                }
            }
            return processes;
        }

        public static class Process implements Parcelable {

            /** User name */
            public final String user;

            /** User ID */
            public final int uid;

            /** Processes ID */
            public final int pid;

            /** Parent processes ID */
            public final int ppid;

            /** virtual memory size of the process in KiB (1024-byte units). */
            public final long vsize;

            /** resident set size, the non-swapped physical memory that a task has used (in kiloBytes). */
            public final long rss;

            public final int cpu;

            /** The priority */
            public final int priority;

            /** The priority, <a href="https://en.wikipedia.org/wiki/Nice_(Unix)">niceness</a> level */
            public final int niceness;

            /** Real time priority */
            public final int realTimePriority;

            /** 0 (sched_other), 1 (sched_fifo), and 2 (sched_rr). */
            public final int schedulingPolicy;

            /** The scheduling policy. Either "bg", "fg", "un", "er", or "" */
            public final String policy;

            /** address of the kernel function where the process is sleeping */
            public final String wchan;

            public final String pc;

            /**
             * Possible states:
             * <p/>
             * "D" uninterruptible sleep (usually IO)
             * <p/>
             * "R" running or runnable (on run queue)
             * <p/>
             * "S" interruptible sleep (waiting for an event to complete)
             * <p/>
             * "T" stopped, either by a job control signal or because it is being traced
             * <p/>
             * "W" paging (not valid since the 2.6.xx kernel)
             * <p/>
             * "X" dead (should never be seen)
             * </p>
             * "Z" defunct ("zombie") process, terminated but not reaped by its parent
             */
            public final String state;

            /** The process name */
            public final String name;

            /** user time in milliseconds */
            public final long userTime;

            /** system time in milliseconds */
            public final long systemTime;

            // Much dirty. Much ugly.
            private Process(String line) throws Exception {
                String[] fields = line.split("\\s+");
                user = fields[0];
                uid = android.os.Process.getUidForName(user);
                pid = Integer.parseInt(fields[1]);
                ppid = Integer.parseInt(fields[2]);
                vsize = Integer.parseInt(fields[3]);
                rss = Integer.parseInt(fields[4]);
                cpu = Integer.parseInt(fields[5]);
                priority = Integer.parseInt(fields[6]);
                niceness = Integer.parseInt(fields[7]);
                realTimePriority = Integer.parseInt(fields[8]);
                schedulingPolicy = Integer.parseInt(fields[9]);
                if (fields.length == 16) {
                    policy = "";
                    wchan = fields[10];
                    pc = fields[11];
                    state = fields[12];
                    name = fields[13];
                    userTime = Integer.parseInt(fields[14].split(":")[1].replace(",", "")) * 1000;
                    systemTime = Integer.parseInt(fields[15].split(":")[1].replace(")", "")) * 1000;
                } else {
                    policy = fields[10];
                    wchan = fields[11];
                    pc = fields[12];
                    state = fields[13];
                    name = fields[14];
                    userTime = Integer.parseInt(fields[15].split(":")[1].replace(",", "")) * 1000;
                    systemTime = Integer.parseInt(fields[16].split(":")[1].replace(")", "")) * 1000;
                }
            }

            private Process(Parcel in) {
                user = in.readString();
                uid = in.readInt();
                pid = in.readInt();
                ppid = in.readInt();
                vsize = in.readLong();
                rss = in.readLong();
                cpu = in.readInt();
                priority = in.readInt();
                niceness = in.readInt();
                realTimePriority = in.readInt();
                schedulingPolicy = in.readInt();
                policy = in.readString();
                wchan = in.readString();
                pc = in.readString();
                state = in.readString();
                name = in.readString();
                userTime = in.readLong();
                systemTime = in.readLong();
            }

            public String getPackageName() {
                if (!user.matches(APP_ID_PATTERN)) {
                    // this process is not an application
                    return null;
                } /*else if (name.contains(":")) {
                    // background service running in another process than the main app process
                    return name.split(":")[0];
                }*/
                return name;
            }

            public PackageInfo getPackageInfo(Context context, int flags)
                    throws PackageManager.NameNotFoundException
            {
                String packageName = getPackageName();
                if (packageName == null) {
                    throw new PackageManager.NameNotFoundException(name + " is not an application process");
                }
                return context.getPackageManager().getPackageInfo(packageName, flags);
            }

            public ApplicationInfo getApplicationInfo(Context context, int flags)
                    throws PackageManager.NameNotFoundException
            {
                String packageName = getPackageName();
                if (packageName == null) {
                    throw new PackageManager.NameNotFoundException(name + " is not an application process");
                }
                return context.getPackageManager().getApplicationInfo(packageName, flags);
            }

            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                dest.writeString(user);
                dest.writeInt(uid);
                dest.writeInt(pid);
                dest.writeInt(ppid);
                dest.writeLong(vsize);
                dest.writeLong(rss);
                dest.writeInt(cpu);
                dest.writeInt(priority);
                dest.writeInt(niceness);
                dest.writeInt(realTimePriority);
                dest.writeInt(schedulingPolicy);
                dest.writeString(policy);
                dest.writeString(wchan);
                dest.writeString(pc);
                dest.writeString(state);
                dest.writeString(name);
                dest.writeLong(userTime);
                dest.writeLong(systemTime);
            }

            public static final Creator<Process> CREATOR = new Creator<Process>() {

                public Process createFromParcel(Parcel source) {
                    return new Process(source);
                }

                public Process[] newArray(int size) {
                    return new Process[size];
                }
            };
        }
    }

    class ProcessAdapter extends RecyclerView.Adapter<ProcessAdapter.ProcessInfoViewHolder>{

        private List<ProcessManager.Process> mData;
        private AdapterView.OnItemClickListener mItemClickListener;

        public ProcessAdapter(List<ProcessManager.Process> data, AdapterView.OnItemClickListener listener){
            mData = data;
            mItemClickListener = listener;
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        @Override
        public ProcessInfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_process_info,
                    parent, false);
            return new ProcessInfoViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ProcessInfoViewHolder holder, final int position) {
            ProcessManager.Process pInfo = mData.get(position);
            holder.llProcessContainer.setBackgroundColor(position % 2 == 0 ? Color.TRANSPARENT :
                    Color.GRAY);
            holder.tvProcessInfo.setText("process name: " + pInfo.getPackageName() +
                    "\nUSER: " + pInfo.user +
                    "\nstate: " + pInfo.state +
                    "\npid: " + pInfo.pid +
                    "\nuid: " + pInfo.uid +
                    "\nmemory rss/vSize: " + pInfo.rss + "/" + pInfo.vsize + " KB" +
                    "\ntime u/s: " + pInfo.userTime + "/" + pInfo.systemTime + " ms" +
                    "\npriority/niceness: " + pInfo.priority + "/" + pInfo.niceness);

            holder.llProcessContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mItemClickListener != null){
                        mItemClickListener.onItemClick(null, null, position, 0);
                    }
                }
            });
        }

        class ProcessInfoViewHolder extends RecyclerView.ViewHolder{

            private TextView tvProcessInfo;
            private LinearLayout llProcessContainer;

            public ProcessInfoViewHolder(View v){
                super(v);
                tvProcessInfo = (TextView) v.findViewById(R.id.tv_process_info);
                llProcessContainer = (LinearLayout) v.findViewById(R.id.ll_process_container);
            }
        }
    }
}
