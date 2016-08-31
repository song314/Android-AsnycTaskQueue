package cn.mianyang.song314.async.taskqueue;

import android.os.SystemClock;
import android.util.Log;

import java.security.InvalidParameterException;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;


/**
 * time: 12/4/15
 * description:
 * <p>
 * A Single background thread run all the task. FIFO
 * <p>
 * todo 1.添加超时
 * todo 2.异常机制测试UT
 * todo 3.重试机制
 * todo 4.Listener使用Runnable代替，减少method数量
 * fixbug 偶尔会重复做一个任务2次
 *
 * @author tangsong
 */
public class AsyncTaskQueue<T extends AsyncTaskQueue.BaseTask> {

    private static final String TAG = "AsyncTaskQueue";

    private LinkedBlockingDeque<T> mWaitingQueue;
    private LinkedBlockingDeque<T> mFailQueue;
    private LoopThread mLooperThread;

    volatile private BaseTask mCurrTask; //TODO 验证 volatile的可靠性，因为有些虚拟机并没有实现这个关键字

    private OnEachFinishListener mEachListener;
    private OnErrorListener mErrorListener;
    private OnAllFinishListener mAllListener;
    private OnTimeOutListener mTimeOutListener; //TODO implement this
    private Runnable mThreadDeadAction;

    private long mTimeOutMillis = 0;

    public AsyncTaskQueue() {
        mWaitingQueue = new LinkedBlockingDeque();
        mFailQueue = new LinkedBlockingDeque<>();
        mLooperThread = new LoopThread();
        mLooperThread.start();
    }

    public void add(T task) {
        if (task == null) {
            throw new InvalidParameterException("AsyncTaskQueue : task cannot be null !!!!");
        }

        if (mWaitingQueue.contains(task)) {
            Log.w(TAG ,"AsyncTaskQueue : ignore the same task, it's in the queue: " + task);
            return;
        }

        if (task.equals(mCurrTask)) {
            Log.w(TAG, "AsyncTaskQueue : ignore the same task, it's the current task : " + mCurrTask);
        }


        Log.i(TAG, " add task : " + task);
        mWaitingQueue.add(task);

        synchronized (mLooperThread) {
            Log.i(TAG, "check the thread state = " + mLooperThread.getState());
            if (isWaiting()) {
                mLooperThread.notify();
                Log.i(TAG, "task thread is waitting , notify it");
            }
        }

    }

    public void addAll(Collection<T> all) {
        for (T task : all) {
            this.add(task);
        }
    }

    public boolean remove(Task task) {
        if (task == null) {
            return true;
        }

        if (task.equals(mCurrTask)) {
            Log.e(TAG, "AsyncTaskQueue :  remove fail  , task is running" + task);
            return false;
        } else {
            if (mWaitingQueue.remove(task)) {
                Log.i(TAG, " remove success !!");
                return true;
            } else {
                Log.e(TAG, " remove fail from list, task : " + task + ", mCurrTask = " + mCurrTask);
                return false;
            }
        }
    }

    public void destroy() {
        mLooperThread.interrupt();
    }

    public void setEachListener(OnEachFinishListener mEachListener) {
        this.mEachListener = mEachListener;
    }

    public void setErrorListener(OnErrorListener mErrorListener) {
        this.mErrorListener = mErrorListener;
    }

    public void setAllListener(OnAllFinishListener mAllListener) {
        this.mAllListener = mAllListener;
    }

    public void setTimeOutListener(OnTimeOutListener mTimeOutListener) {
        this.mTimeOutListener = mTimeOutListener;
    }

    public void onThreadDead(Runnable listener) {
        mThreadDeadAction = listener;
    }

    public void stop() {
        mWaitingQueue.clear();
    }

    public boolean isAllTaskFinish() {
        return mLooperThread.getState() == Thread.State.TIMED_WAITING;
    }


    public interface Task {
        void run() throws Exception;
    }

    public interface OnTimeOutListener<T extends BaseTask> {
        void onTimeOut(T task);
    }

    public interface OnEachFinishListener<T extends BaseTask> {
        void onEachFinish(T task);
    }

    public interface OnErrorListener<T extends BaseTask> {
        void onError(T errorTask, Exception e);
    }

    public interface OnAllFinishListener {
        void onAllFinish();
    }

    private class LoopThread extends Thread {
        @Override
        public void run() {

            T task;
            long cost;

            while (!this.isInterrupted()) {

                task = mWaitingQueue.poll();
                mCurrTask = task;

                if (task != null) {

                    cost = SystemClock.elapsedRealtime();
                    working(task);
                    Log.i(TAG, task.getClass().getSimpleName() + " cost : " + (SystemClock.elapsedRealtime() - cost));

                } else {
                    // no tasks , let it waiting
                    waiting();
                }
            }

            mWaitingQueue.clear();
            mFailQueue.clear();

            if (mThreadDeadAction != null) {
                mThreadDeadAction.run();
            }
            Log.i(TAG, " ----- > The Loop Thread is finished.");
        }

        private void waiting() {
            synchronized (mLooperThread) {

                if (mWaitingQueue.size() <= 0) {
                    try {
                        Log.i(TAG, " all task is finish, waiting ....");
                        mLooperThread.wait();
                        Log.i(TAG, " waiting is finished , go on ~~");
                    } catch (InterruptedException e) {
                        if (e instanceof InterruptedException) {
                            Thread.interrupted();
                        }
                        e.printStackTrace();
                    }
                }

            }
        }

        private void working(T task) {

            try {
                Log.i(TAG, " run new task : " + task);
                // run the task
                task.run();

                if (mEachListener != null) {
                    mEachListener.onEachFinish(task);
                }
            } catch (Exception e) {
                Log.e(TAG, "AsyncTaskQueue :  meet an exception in task : " + task, e);

                if (e instanceof InterruptedException) {
                    Thread.interrupted();
                }
                mFailQueue.push(task);
                task.errorCount++;
                if (mErrorListener != null) {
                    mErrorListener.onError(task, e);
                }
            } finally {
                if (mWaitingQueue.size() <= 0) {
                    if (mAllListener != null) {
                        mAllListener.onAllFinish();
                    }
                }
            }
        }
    }

    abstract public static class BaseTask<T> implements AsyncTaskQueue.Task {
        private static AtomicLong ID = new AtomicLong();

        protected T taskData;
        public String taskName;
        final long taskId;
        protected int errorCount;


        public BaseTask() {
            taskId = ID.addAndGet(1);
        }

        public BaseTask(String taskName) {
            this.taskName = taskName;
            taskId = ID.addAndGet(1);
        }

        public BaseTask(T taskData) {
            this.taskData = taskData;
            taskId = ID.addAndGet(1);
        }

        @Override
        public String toString() {
            return "BaseTask{" +
                    "taskData=" + taskData +
                    ", taskName='" + taskName + '\'' +
                    ", taskId=" + taskId +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof BaseTask) {
                return this.taskId == ((BaseTask) o).taskId;
            } else {
                return false;
            }
        }

        public T getTaskInfo() {
            return taskData;
        }

        public int getErrorCount() {
            return errorCount;
        }
    }

    public boolean isWaiting() {
        return mLooperThread.getState() == Thread.State.WAITING;

    }

    abstract public static class SyncBaskTask<T> extends BaseTask<T> {

        protected void autoWait() {
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                } finally {
                    Log.i("SyncBaskTask", " waiting finish");
                }
            }
        }

        protected void autoNotify() {
            synchronized (this) {
                notify();
            }
        }
    }

}
