package com.android.launcher3.util;

import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.Executor;

public class TrTaskSchedule {

    static final String TAG = "TrTaskSchedule";
    public static void runAllTasksInExecutor(ArrayList<SafelyRunnable> runnableList, Executor executor, int parallelTasks) {
        int taskSize = runnableList.size();
        parallelTasks = Math.min(taskSize, parallelTasks);
        Object LOCK = new Object();
        for (int i = 0; i < parallelTasks; ++i) {
            executor.execute(() -> {
                while (true) {
                    SafelyRunnable runnable = getTask(runnableList, LOCK);
                    if (runnable == null) {
                        break;
                    }
                    runnable.run();
                }
            });
        }
    }

    public static void runTasks(ArrayList<SafelyRunnable> runnableList, Executor executor, int parallelTasks, long timeOut) {
        int taskSize = runnableList.size();
        parallelTasks = Math.min(taskSize - 1, parallelTasks);
        Object LOCK = new Object();
        final IntCounter intCounter = new IntCounter();
        for (int i = 0; i < parallelTasks; ++i) {
            executor.execute(() -> {
                while (true) {
                    SafelyRunnable runnable = getTask(runnableList, LOCK);
                    if (runnable == null) {
                        break;
                    }
                    runnable.run();
                    synchronized (LOCK) {
                        intCounter.add();
                    }
                }
            });
        }
        long taskDoneTime = -1;
        while (true) {
            SafelyRunnable runnable = getTask(runnableList, LOCK);
            if (runnable == null) {
                if (intCounter.isEqual(taskSize)) {
                    break;
                }
                if (taskDoneTime == -1) {
                    taskDoneTime = SystemClock.uptimeMillis();
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Log.e(TAG, "InterruptedException-e:" + e.getMessage());
                }
                long now = SystemClock.uptimeMillis();
                if (now - taskDoneTime > timeOut) {
                    Log.e(TAG, "runTasks>>>>>>>>>>>>>>>>>>>>>>>time out");
                    break;
                }
                continue;
            }
            runnable.run();
            synchronized (LOCK) {
                intCounter.add();
            }
        }
    }

    static SafelyRunnable getTask(ArrayList<SafelyRunnable> runnableList, final Object LOCK) {
        synchronized (LOCK) {
            if (runnableList.isEmpty()) {
                return null;
            }
            int size = runnableList.size();
            return runnableList.remove(size - 1);
        }
    }

    public static abstract class SafelyRunnable implements Runnable {

        @Override
        public final void run() {
            try {
                onTaskRun();
            } catch (Throwable e) {
                Log.e(TAG,"SafelyRunnable-Throwable:" + e.getMessage());
            }
        }

        public abstract void onTaskRun();
    }

    public static class IntCounter {
        public int mCounter = 0;

        public void add() {
            this.mCounter += 1;
        }

        public boolean isEqual(int value) {
            return this.mCounter == value;
        }
    }
}